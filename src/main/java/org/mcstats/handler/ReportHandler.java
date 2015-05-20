package org.mcstats.handler;

import com.google.gson.Gson;
import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ByteArrayISO8859Writer;
import org.mcstats.MCStats;
import org.mcstats.db.ModelCache;
import org.mcstats.db.RedisCache;
import org.mcstats.decoder.DecodedRequest;
import org.mcstats.decoder.LegacyRequestDecoder;
import org.mcstats.decoder.ModernRequestDecoder;
import org.mcstats.decoder.RequestDecoder;
import org.mcstats.model.Plugin;
import org.mcstats.processing.BatchPluginRequestProcessor;
import org.mcstats.util.URLUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Singleton
public class ReportHandler extends AbstractHandler {

    private Logger logger = Logger.getLogger("ReportHandler");

    /**
     * If requests should be soft ignored temporarily
     */
    public static boolean SOFT_IGNORE_REQUESTS = false;

    /**
     * The MCStats object
     */
    private final MCStats mcstats;

    /**
     * Gson instance
     */
    private final Gson gson;

    /**
     * The redis pool
     */
    private final JedisPool redisPool;

    /**
     * The background request processor
     */
    private final BatchPluginRequestProcessor requestProcessor;

    /**
     * Modern request decoder
     */
    private final RequestDecoder modernDecoder;

    /**
     * Legacy request decoder
     */
    private final RequestDecoder legacyDecoder;

    @Inject
    public ReportHandler(MCStats mcstats, Gson gson, JedisPool redisPool, BatchPluginRequestProcessor requestProcessor) {
        this.mcstats = mcstats;
        this.gson = gson;
        this.redisPool = redisPool;
        this.requestProcessor = requestProcessor;

        modernDecoder = new ModernRequestDecoder();
        legacyDecoder = new LegacyRequestDecoder();
    }

    /**
     * Finish a request and end it by closing it immediately
     *
     * @param decoded
     * @param responseType
     * @param message
     * @param request
     * @param response
     * @throws IOException
     */
    private void finishRequest(DecodedRequest decoded, ResponseType responseType, String message, Request request, HttpServletResponse response) throws IOException {
        try (ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer(64)) {
            if (decoded != null && decoded.revision >= 7) {
                if (responseType == ResponseType.OK) {
                    writer.write("0");
                } else if (responseType == ResponseType.OK_FIRST_REQUEST) {
                    writer.write("1");
                } else if (responseType == ResponseType.ERROR) {
                    writer.write("7");
                }

                if (!message.isEmpty()) {
                    writer.write("," + message);
                }
            } else {
                if (responseType == ResponseType.OK) {
                    writer.write("OK");
                } else if (responseType == ResponseType.OK_FIRST_REQUEST) {
                    writer.write("OK This is your first update this hour.");
                } else if (responseType == ResponseType.ERROR) {
                    writer.write("ERR");
                }

                if (!message.isEmpty()) {
                    writer.write(" " + message);
                }
            }

            response.setContentLength(writer.size());

            writer.writeTo(response.getOutputStream());
        }

        request.getHttpChannel().getEndPoint().close();
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

    /**
     * Get the country code passed by a request.
     *
     * @param request
     * @return
     */
    private String getCountryCode(HttpServletRequest request) {
        String country = request.getHeader("GEOIP_COUNTRY_CODE") == null ? request.getHeader("HTTP_X_GEOIP") : request.getHeader("GEOIP_COUNTRY_CODE");

        if (country == null) {
            country = "ZZ";
        }

        return country;
    }

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        long startTimeNano = System.nanoTime();

        if (!request.getMethod().equals("POST")) {
            return;
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

        try {
            int normalizedTime = normalizeTime();
            String pluginName = URLUtils.decode(getPluginName(request));

            if (pluginName == null) {
                finishRequest(null, ResponseType.ERROR, "Invalid arguments.", baseRequest, response);
                return;
            }

            final Plugin plugin = mcstats.loadPlugin(pluginName);

            if (plugin == null || plugin.getId() == -1) {
                finishRequest(null, ResponseType.ERROR, "Invalid arguments.", baseRequest, response);
                return;
            }

            String userAgent = request.getHeader("User-Agent");
            final DecodedRequest decoded;

            if (userAgent.startsWith("MCStats/")) {
                decoded = modernDecoder.decode(plugin, baseRequest);
            } else {
                decoded = legacyDecoder.decode(plugin, baseRequest);
            }

            if (decoded == null) {
                finishRequest(null, ResponseType.ERROR, "Invalid arguments.", baseRequest, response);
                return;
            }

            decoded.plugin = plugin;
            decoded.country = getCountryCode(request);
            normalizeRequest(decoded);

            int lastSent = 0;
            String lastSentKey = String.format(RedisCache.SERVER_LAST_SENT_KEY, decoded.uuid, plugin.getId());

            try (Jedis redis = redisPool.getResource()) {
                String lastSentValue = redis.get(lastSentKey);

                if (lastSentValue != null) {
                    lastSent = Integer.parseInt(lastSentValue);
                }
            }

            if (decoded.revision < 7 || lastSent > normalizedTime) {
                finishRequest(decoded, ResponseType.OK, baseRequest, response);
            } else {
                finishRequest(decoded, ResponseType.OK_FIRST_REQUEST, baseRequest, response);
            }

            plugin.setLastUpdated((int) (System.currentTimeMillis() / 1000L));
            requestProcessor.submit(decoded);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            long takenNano = System.nanoTime() - startTimeNano;
            double takenMs = takenNano / 1_000_000d;

            mcstats.getRequestProcessingTimeAverage().update(takenMs);
        }
    }

    /**
     * Normalizes values in a request, e.g. amd64 -> x86_64.
     *
     * @param request
     */
    private void normalizeRequest(DecodedRequest request) {
        if (request.revision >= 6) {
            if ((request.osarch != null) && (request.osarch.equals("i386"))) {
                request.osarch = "x86";
            }

            if ((request.osarch != null) && (request.osarch.equals("amd64"))) {
                request.osarch = "x86_64";
            }

            if ((request.osname.startsWith("Windows")) && (request.osname.length() > 8)) {
                request.osversion = request.osname.substring(8);
                request.osname = "Windows";
            }

            if (request.osversion.equals("6.1")) {
                request.osversion = "7";
                request.osname = "Windows";
            }
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

}
