package org.mcstats.jetty;

import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ByteArrayISO8859Writer;
import org.mcstats.MCStats;
import org.mcstats.decoder.DecodedRequest;
import org.mcstats.decoder.LegacyRequestDecoder;
import org.mcstats.decoder.ModernRequestDecoder;
import org.mcstats.decoder.RequestDecoder;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;
import org.mcstats.util.URLUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PluginTelemetryHandler extends AbstractHandler {

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

    public PluginTelemetryHandler(MCStats mcstats) {
        this.mcstats = mcstats;
        modernDecoder = new ModernRequestDecoder(mcstats);
        legacyDecoder = new LegacyRequestDecoder(mcstats);
    }

    /**
     * Get the size of the work queue in the background
     *
     * @return
     */
    public int queueSize() {
        return 0;
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
    private void finishRequest(DecodedRequest decoded, PluginTelemetryResponseType responseType, String message, Request baseRequest, HttpServletResponse response) throws IOException {
        ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer(1500);
        if (decoded != null && decoded.revision >= 7) {
            if (responseType == PluginTelemetryResponseType.OK) {
                writer.write("0");
            } else if (responseType == PluginTelemetryResponseType.OK_FIRST_REQUEST) {
                writer.write("1");
            } else if (responseType == PluginTelemetryResponseType.OK_REGENERATE_GUID) {
                writer.write("2");
            } else if (responseType == PluginTelemetryResponseType.ERROR) {
                writer.write("7");
            }
            if (!message.isEmpty()) {
                writer.write((new StringBuilder()).append(",").append(message).toString());
            }
        } else {
            if (responseType == PluginTelemetryResponseType.OK || responseType == PluginTelemetryResponseType.OK_REGENERATE_GUID) {
                writer.write("OK");
            } else if (responseType == PluginTelemetryResponseType.OK_FIRST_REQUEST) {
                writer.write("OK This is your first update this hour.");
            } else if (responseType == PluginTelemetryResponseType.ERROR) {
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
    private void finishRequest(DecodedRequest decoded, PluginTelemetryResponseType responseType, Request baseRequest, HttpServletResponse response) throws IOException {
        finishRequest(decoded, responseType, "", baseRequest, response);
    }

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try {
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
                finishRequest(null, PluginTelemetryResponseType.OK, baseRequest, response);
                return;
            }

            String pluginName = URLUtils.decode(getPluginName(request));

            if (pluginName == null) {
                finishRequest(null, PluginTelemetryResponseType.ERROR, "Invalid arguments.", baseRequest, response);
                return;
            }

            final Plugin plugin = mcstats.loadPlugin(pluginName);

            String userAgent = request.getHeader("User-Agent");
            final DecodedRequest decoded;

            try {
                if (userAgent != null && userAgent.startsWith("MCStats/")) {
                    decoded = modernDecoder.decode(plugin, baseRequest);
                } else {
                    decoded = legacyDecoder.decode(plugin, baseRequest);
                }
            } catch (IOException e) {
                // Trap IOException from the decoder because it's common for decoding to fail
                // when a request is malformed.
                finishRequest(null, PluginTelemetryResponseType.OK, baseRequest, response);
                return;
            }

            if (decoded == null) {
                finishRequest(decoded, PluginTelemetryResponseType.ERROR, "Invalid arguments.", baseRequest, response);
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
                finishRequest(decoded, PluginTelemetryResponseType.ERROR, "Rejected.", baseRequest, response);
                return;
            }

            int normalizedTime = normalizeTime();

            long lastSent = 0L;

            String serverCacheKey = decoded.guid + "/" + plugin.getId();

            if (serverLastSendCache.containsKey(serverCacheKey)) {
                lastSent = serverLastSendCache.get(serverCacheKey);
            }

            if (((plugin.getId() != 1) || (decoded.revision != 7)) ||
                    (lastSent > normalizedTime)) {
                finishRequest(decoded, PluginTelemetryResponseType.OK, baseRequest, response);
            } else {
                finishRequest(decoded, PluginTelemetryResponseType.OK_FIRST_REQUEST, baseRequest, response);
            }

            serverLastSendCache.put(serverCacheKey, (int) System.currentTimeMillis());

            if (plugin.getId() == 4930) {
                return;
            }

            try {
                Server server = mcstats.loadServer(decoded.guid);

                if ((server.getViolationCount() >= MAX_VIOLATIONS_ALLOWED) && (!server.isBlacklisted())) {
                    server.setBlacklisted(true);
                    return;
                }

                ServerPlugin serverPlugin = server.getPlugin(plugin);

                if (serverPlugin == null) {
                    serverPlugin = new ServerPlugin(server, plugin);
                    serverPlugin.setVersion(decoded.pluginVersion);

                    server.addPlugin(serverPlugin);
                    mcstats.notifyServerPlugin(serverPlugin);
                }

                if ((!serverPlugin.getVersion().equals(decoded.pluginVersion)) && (!server.isBlacklisted())) {
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

                if (canonicalServerVersion.equals("CraftBukkit")) {
                    ServerPlugin cbplusplus = server.getPlugin(mcstats.loadPlugin(137));

                    if (cbplusplus != null) {
                        if (cbplusplus.recentlyUpdated()) {
                            canonicalServerVersion = "CraftBukkit++";
                        }
                    }
                }

                // BungeeCord doesn't send a proper version string, so detect it via the metrics data it sends with the server
                ServerPlugin bungeeCordPlugin = server.getPlugin(mcstats.loadPlugin(5921));

                if (bungeeCordPlugin != null) {
                    canonicalServerVersion = "BungeeCord";
                }

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

                plugin.setLastUpdated((int) (System.currentTimeMillis() / 1000L));
                server.setLastSentData((int) (System.currentTimeMillis() / 1000L));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();

            finishRequest(null, PluginTelemetryResponseType.OK, baseRequest, response);
        }
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

    /**
     * Process a post request and return all of its key/value pairs
     *
     * @param content
     * @return
     */
    private Map<String, String> processPostRequest(String content) {
        Map<String, String> store = new HashMap<>();

        // Split the post data by &
        for (String entry : content.split("&")) {
            // Split it by =
            String[] data = entry.split("=");

            // Check that there is sufficient data in the data array
            if (data.length != 2) {
                continue;
            }

            // decode the data
            String key = URLUtils.decode(data[0]);
            String value = URLUtils.decode(data[1]);

            // Add it to the store
            store.put(key, value);
        }

        return store;
    }

}
