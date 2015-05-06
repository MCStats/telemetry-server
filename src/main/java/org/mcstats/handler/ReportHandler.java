package org.mcstats.handler;

import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ByteArrayISO8859Writer;
import org.mcstats.AccumulatorDelegator;
import org.mcstats.MCStats;
import org.mcstats.accumulator.CustomDataAccumulator;
import org.mcstats.accumulator.MCStatsInfoAccumulator;
import org.mcstats.accumulator.ServerInfoAccumulator;
import org.mcstats.accumulator.VersionInfoAccumulator;
import org.mcstats.decoder.DecodedRequest;
import org.mcstats.decoder.LegacyRequestDecoder;
import org.mcstats.decoder.ModernRequestDecoder;
import org.mcstats.decoder.RequestDecoder;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;
import org.mcstats.util.Tuple;
import org.mcstats.util.URLUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ReportHandler extends AbstractHandler {

    private Logger logger = Logger.getLogger("ReportHandler");

    /**
     * If requests should be soft ignored temporarily
     */
    public static boolean SOFT_IGNORE_REQUESTS = false;

    /**
     * The maximum amount of allowable version switches in a graph interval before they are blacklisted;
     */
    private static final int MAX_VIOLATIONS_ALLOWED = 7;

    /**
     * The MCStats object
     */
    private MCStats mcstats;

    /**
     * The delegator for accumulators
     */
    private AccumulatorDelegator accumulatorDelegator;

    /**
     * Modern request decoder
     */
    private final RequestDecoder modernDecoder;

    /**
     * Legacy request decoder
     */
    private final RequestDecoder legacyDecoder;

    /**
     * Cache of the last sent times
     */
    private Map<String, Integer> serverLastSendCache = new ConcurrentHashMap<>();

    /**
     * Executor for off-thread work
     */
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 16, 10, TimeUnit.MINUTES, new LinkedBlockingQueue<>());

    /**
     * SHA of the redis sum add script
     */
    private final String redisAddSumScriptSha;

    public ReportHandler(MCStats mcstats) {
        this.mcstats = mcstats;
        accumulatorDelegator = new AccumulatorDelegator(mcstats);
        modernDecoder = new ModernRequestDecoder(mcstats);
        legacyDecoder = new LegacyRequestDecoder(mcstats);

        registerAccumulators();

        redisAddSumScriptSha = mcstats.loadRedisScript("/scripts/redis/zadd-sum.lua");
    }

    /**
     * Registers accumulators
     */
    private void registerAccumulators() {
        accumulatorDelegator.add(new ServerInfoAccumulator());
        accumulatorDelegator.add(new MCStatsInfoAccumulator());
        accumulatorDelegator.add(new VersionInfoAccumulator());
        accumulatorDelegator.add(new CustomDataAccumulator());
    }

    /**
     * Finish a request and end it by closing it immediately
     *
     * @param decoded
     * @param responseType
     * @param message
     * @param baseRequest
     * @param response
     * @throws IOException
     */
    private void finishRequest(DecodedRequest decoded, ResponseType responseType, String message, Request baseRequest, HttpServletResponse response) throws IOException {
        ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer(1500);
        if (decoded != null && decoded.revision >= 7) {
            if (responseType == ResponseType.OK) {
                writer.write("0");
            } else if (responseType == ResponseType.OK_FIRST_REQUEST) {
                writer.write("1");
            } else if (responseType == ResponseType.OK_REGENERATE_GUID) {
                writer.write("2");
            } else if (responseType == ResponseType.ERROR) {
                writer.write("7");
            }
            if (!message.isEmpty()) {
                writer.write((new StringBuilder()).append(",").append(message).toString());
            }
        } else {
            if (responseType == ResponseType.OK || responseType == ResponseType.OK_REGENERATE_GUID) {
                writer.write("OK");
            } else if (responseType == ResponseType.OK_FIRST_REQUEST) {
                writer.write("OK This is your first update this hour.");
            } else if (responseType == ResponseType.ERROR) {
                writer.write("ERR");
            }
            if (!message.isEmpty()) {
                writer.write((new StringBuilder()).append(" ").append(message).toString());
            }
        }
        writer.flush();
        response.setContentLength(writer.size());
        OutputStream outputStream = response.getOutputStream();
        writer.writeTo(outputStream);
        outputStream.close();
        writer.close();
        baseRequest.getHttpChannel().getEndPoint().close();
    }

    /**
     * Finish a request and end it by closing it immediately
     *
     * @param decoded
     * @param responseType
     * @param baseRequest
     * @param response
     * @throws IOException
     */
    private void finishRequest(DecodedRequest decoded, ResponseType responseType, Request baseRequest, HttpServletResponse response) throws IOException {
        finishRequest(decoded, responseType, "", baseRequest, response);
    }

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        long startTimeNano = System.nanoTime();

        try (Jedis redis = mcstats.getRedisPool().getResource()) {
            if (!request.getMethod().equals("POST")) {
                return;
            }

            if (serverLastSendCache.size() > 200000) {
                serverLastSendCache.clear();
            }

            request.setCharacterEncoding("UTF-8");
            response.setHeader("Connection", "close");
            baseRequest.setHandled(true);
            response.setStatus(200);
            response.setContentType("text/plain");
            mcstats.incrementAndGetRequests();

            if (SOFT_IGNORE_REQUESTS) {
                finishRequest(null, ResponseType.OK, baseRequest, response);
                return;
            }

            String pluginName = URLUtils.decode(getPluginName(request));

            if (pluginName == null) {
                finishRequest(null, ResponseType.ERROR, "Invalid arguments.", baseRequest, response);
                return;
            }

            final Plugin plugin = mcstats.loadPlugin(pluginName);

            // TODO
            redis.sadd("plugins", Integer.toString(plugin.getId()));

            String userAgent = request.getHeader("User-Agent");
            final DecodedRequest decoded;

            if (userAgent.startsWith("MCStats/")) {
                decoded = modernDecoder.decode(plugin, baseRequest);
            } else {
                decoded = legacyDecoder.decode(plugin, baseRequest);
            }

            if (decoded == null) {
                finishRequest(decoded, ResponseType.ERROR, "Invalid arguments.", baseRequest, response);
                return;
            }

            if (mcstats.isDebug()) {
                logger.debug("Processing request for " + plugin.getName() + " request=" + decoded);
            }

            String geoipCountryCodeNonFinal = request.getHeader("GEOIP_COUNTRY_CODE") == null ? request.getHeader("HTTP_X_GEOIP") : request.getHeader("GEOIP_COUNTRY_CODE");

            if (geoipCountryCodeNonFinal == null) {
                geoipCountryCodeNonFinal = "ZZ";
            }
            final String geoipCountryCode = geoipCountryCodeNonFinal;

            if (plugin.getId() == -1) {
                finishRequest(decoded, ResponseType.ERROR, "Rejected.", baseRequest, response);
                return;
            }

            int normalizedTime = normalizeTime();

            long lastSent = 0L;

            String serverCacheKey = decoded.uuid + "/" + plugin.getId();

            if (serverLastSendCache.containsKey(serverCacheKey)) {
                lastSent = serverLastSendCache.get(serverCacheKey);
            }

            if (((plugin.getId() != 1) || (decoded.revision != 7)) ||
                    (lastSent > normalizedTime)) {
                finishRequest(decoded, ResponseType.OK, baseRequest, response);
            } else {
                finishRequest(decoded, ResponseType.OK_FIRST_REQUEST, baseRequest, response);
            }

            serverLastSendCache.put(serverCacheKey, (int) System.currentTimeMillis());

            if (plugin.getId() == 4930) {
                return;
            }

            try {
                Server server = new Server(decoded.uuid);

                // TODO dao?
                Map<String, String> serverData = redis.hgetAll("server:" + server.getUUID());

                if (serverData != null && !serverData.isEmpty()) {
                    server.setJavaName(serverData.get("java.name"));
                    server.setJavaVersion(serverData.get("java.version"));
                    server.setOSName(serverData.get("os.name"));
                    server.setOSVersion(serverData.get("os.version"));
                    server.setOSArch(serverData.get("os.arch"));

                    server.setOnlineMode(Integer.parseInt(serverData.get("authMode")));

                    server.setCountry(serverData.get("country"));
                    server.setServerSoftware(serverData.get("serverSoftware"));
                    server.setMinecraftVersion(serverData.get("minecraftVersion"));
                    server.setPlayers(Integer.parseInt(serverData.get("players.online")));
                    server.setCores(Integer.parseInt(serverData.get("cores")));
                }

                boolean isBlacklisted = redis.sismember("server-blacklist", decoded.uuid);

                if ((server.getViolationCount() >= MAX_VIOLATIONS_ALLOWED) && !isBlacklisted) {
                    redis.sadd("server-blacklist", decoded.uuid);
                    return;
                }

                ServerPlugin serverPlugin = new ServerPlugin(server, plugin);

                // TODO dao?
                Map<String, String> serverPluginData = redis.hgetAll("server-plugin:" + server.getUUID() + ":" + plugin.getId());

                if (serverPluginData != null && !serverPluginData.isEmpty()) {
                    serverPlugin.setVersion(serverPluginData.get("version"));
                }

                if ((!serverPlugin.getVersion().equals(decoded.pluginVersion)) && !isBlacklisted) {
                    serverPlugin.addVersionChange(serverPlugin.getVersion(), decoded.pluginVersion);
                    serverPlugin.setVersion(decoded.pluginVersion);
                    server.incrementViolations();
                }

                if (serverPlugin.getRevision() != decoded.revision) {
                    serverPlugin.setRevision(decoded.revision);
                }

                if (!server.getServerVersion().equals(decoded.serverVersion)) {
                    server.setServerVersion(decoded.serverVersion);
                }

                if ((server.getPlayers() != decoded.playersOnline) && (decoded.playersOnline >= 0)) {
                    server.setPlayers(decoded.playersOnline);
                }

                if (!geoipCountryCode.isEmpty() && !server.getCountry().equals(geoipCountryCode)) {
                    server.setCountry(geoipCountryCode);
                }

                String canonicalServerVersion = mcstats.getServerBuildIdentifier().getServerVersion(decoded.serverVersion);
                String minecraftVersion = mcstats.getServerBuildIdentifier().getMinecraftVersion(decoded.serverVersion);

                if (!server.getServerSoftware().equals(canonicalServerVersion)) {
                    server.setServerSoftware(canonicalServerVersion);
                }

                if (!server.getMinecraftVersion().equals(minecraftVersion)) {
                    server.setMinecraftVersion(minecraftVersion);
                }

                if (!decoded.isPing) {
                    plugin.setGlobalHits(plugin.getGlobalHits() + 1);
                }

                if ((decoded.revision >= 4) && (!server.getCountry().equals("SG")) && ((geoipCountryCode == null) || (!geoipCountryCode.equals("SG")))) {
                    serverPlugin.setCustomData(decoded.customData);
                }

                if (decoded.revision >= 6) {
                    if ((decoded.osarch != null) && (decoded.osarch.equals("i386"))) {
                        decoded.osarch = "x86";
                    }

                    if ((decoded.osarch != null) && (decoded.osarch.equals("amd64"))) {
                        decoded.osarch = "x86_64";
                    }

                    if ((decoded.osname.startsWith("Windows")) && (decoded.osname.length() > 8)) {
                        decoded.osversion = decoded.osname.substring(8);
                        decoded.osname = "Windows";
                    }

                    if (decoded.osversion.equals("6.1")) {
                        decoded.osversion = "7";
                        decoded.osname = "Windows";
                    }

                    if (!decoded.osname.equals(server.getOSName())) {
                        server.setOSName(decoded.osname);
                    }

                    if ((decoded.osarch != null) && (!decoded.osarch.equals(server.getOSArch()))) {
                        server.setOSArch(decoded.osarch);
                    }

                    if (!decoded.osversion.equals(server.getOSVersion())) {
                        server.setOSVersion(decoded.osversion);
                    }

                    if (server.getCores() != decoded.cores) {
                        server.setCores(decoded.cores);
                    }

                    if (server.getOnlineMode() != decoded.authMode) {
                        server.setOnlineMode(decoded.authMode);
                    }

                    if (!decoded.javaName.equals(server.getJavaName())) {
                        server.setJavaName(decoded.javaName);
                    }

                    if (!decoded.javaVersion.equals(server.getJavaVersion())) {
                        server.setJavaVersion(decoded.javaVersion);
                    }
                }

                // TODO
                serverData.put("country", geoipCountryCode);
                serverData.put("serverSoftware", canonicalServerVersion);
                serverData.put("minecraftVersion", minecraftVersion);
                serverData.put("players.online", Integer.toString(decoded.playersOnline));
                serverData.put("os.name", decoded.osname);
                serverData.put("os.version", decoded.osversion);
                serverData.put("os.arch", decoded.osarch);
                serverData.put("java.name", decoded.javaName);
                serverData.put("java.version", decoded.javaVersion);
                serverData.put("cores", Integer.toString(decoded.cores));
                serverData.put("authMode", Integer.toString(decoded.authMode));

                serverPluginData.put("revision", Integer.toString(decoded.revision));
                serverPluginData.put("version", decoded.pluginVersion);

                executor.execute(() -> {
                    try (Jedis executorRedis = mcstats.getRedisPool().getResource()) {
                        Pipeline pipeline = executorRedis.pipelined();

                        pipeline.sadd("servers", decoded.uuid);
                        pipeline.sadd("server-plugins:" + decoded.uuid, Integer.toString(plugin.getId()));
                        pipeline.hmset("server:" + decoded.uuid, serverData);
                        pipeline.hmset("server-plugin:" + decoded.uuid + ":" + plugin.getId(), serverPluginData);

                        // accumulate all graph data
                        // TODO break out to somewhere else?
                        List<Tuple<Column, Long>> accumulatedData = accumulatorDelegator.accumulate(decoded, serverPlugin);

                        for (Tuple<Column, Long> data : accumulatedData) {
                            // N.B.: This column & graph are virtual, so are later
                            // forcibly loaded from the database to rev up caches
                            // and db state.
                            Column column = data.first();
                            Graph graph = column.getGraph();
                            long value = data.second();

                            if (graph.getName() == null || column.getName() == null) {
                                continue;
                            }

                            if (!graph.isFromDatabase()) {
                                graph = plugin.getGraph(graph.getName());
                            }

                            String redisDataKey = String.format("plugin-data:%d:%s:%s", graph.getPlugin().getId(), graph.getName(), column.getName());
                            String redisDataSumKey = String.format("plugin-data-sum:%d:%s:%s", graph.getPlugin().getId(), graph.getName(), column.getName());

                            // metadata
                            pipeline.sadd("graphs:" + graph.getPlugin().getId(), graph.getName());
                            pipeline.sadd("columns:" + graph.getPlugin().getId() + ":" + graph.getName(), column.getName());

                            // data
                            pipeline.evalsha(redisAddSumScriptSha, 2, redisDataKey, redisDataSumKey, Long.toString(value), server.getUUID());
                        }

                        pipeline.sync();
                    }
                });

                // serverPlugin.setUpdated((int) (System.currentTimeMillis() / 1000L));
                plugin.setLastUpdated((int) (System.currentTimeMillis() / 1000L));
                server.setLastSentData((int) (System.currentTimeMillis() / 1000L));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();

            finishRequest(null, ResponseType.OK, baseRequest, response);
        } finally {
            long takenNano = System.nanoTime() - startTimeNano;
            double takenMs = takenNano / 1_000_000d;

            mcstats.getRequestProcessingTimeAverage().update(takenMs);
        }
    }

    /**
     * Returns the size of the executor queue
     *
     * @return
     */
    public int getExecutorQueueSize() {
        return executor.getQueue().size();
    }

    /**
     * Normalize the time to the nearest graphing period
     *
     * @return
     */
    public static int normalizeTime() {
        int currentTimeSeconds = (int) (System.currentTimeMillis() / 1000);

        // The graphing interval in minutes
        // TODO not hardcoded :3
        int interval = 30;

        // calculate the devisor denominator
        int denom = interval * 60;

        return (int) Math.round((currentTimeSeconds - (denom / 2d)) / denom) * denom;
    }

    /**
     * Get the plugin name from a request
     *
     * @param request
     * @return
     */
    private String getPluginName(HttpServletRequest request) {
        String url = request.getRequestURI();
        if (url.startsWith("//report/")) {
            return url.substring("//report/".length());
        } else if (url.startsWith("/report/")) {
            return url.substring("/report/".length());
        } else if (url.startsWith("/plugin/")) {
            return url.substring("/plugin/".length());
        } else if (url.startsWith("/report.php?plugin=")) {
            return url.substring("/report.php?plugin=".length());
        } else {
            return null;
        }
    }

}
