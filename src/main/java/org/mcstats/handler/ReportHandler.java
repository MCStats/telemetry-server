package org.mcstats.handler;

import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ByteArrayISO8859Writer;
import org.mcstats.MCStats;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.PluginVersion;
import org.mcstats.model.RawQuery;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;
import org.mcstats.util.URLUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ReportHandler extends AbstractHandler {
    private Logger logger = Logger.getLogger("ReportHandler");

    /**
     * The maximum amount of allowable version switches in a graph interval before they are blacklisted;
     */
    private static final int MAX_VIOLATIONS_ALLOWED = 7;

    /**
     * The MCStats object
     */
    private MCStats mcstats;

    /**
     * A queue of work to run in a separate thread
     */
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(5, 10, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    public ReportHandler(MCStats mcstats) {
        this.mcstats = mcstats;
    }

    /**
     * Get the size of the work queue in the background
     * @return
     */
    public int queueSize() {
        return executor.getQueue().size();
    }

    /**
     * Finish a request and end it by closing it immediately
     *
     * @param message
     * @param baseRequest
     * @param response
     * @throws IOException
     */
    private void finishRequest(String message, Request baseRequest, HttpServletResponse response) throws IOException {
        ByteArrayISO8859Writer writer = new ByteArrayISO8859Writer(1500);
        writer.write(message);
        writer.flush();

        response.setContentLength(writer.size());

        OutputStream outputStream = response.getOutputStream();
        writer.writeTo(outputStream);

        outputStream.close();
        writer.close();
        baseRequest.getConnection().getEndPoint().close();
    }

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try {
            // If they aren't posting to us, we don't want to know about them :p
            if (!request.getMethod().equals("POST")) {
                return;
            }

            request.setCharacterEncoding("UTF-8");
            response.setHeader("Connection", "close");
            baseRequest.setHandled(true);
            response.setStatus(200);
            response.setContentType("text/plain");

            // request counter
            mcstats.incrementAndGetRequests();

            // Get the plugin name
            String pluginName = URLUtils.decode(getPluginName(request));

            if (pluginName == null) {
                finishRequest("ERR Invalid arguments.", baseRequest, response);
                return;
            }

            // the full request contents
            String content = "";

            // Read the request
            BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream(), "UTF-8"));
            String line;

            while ((line = reader.readLine()) != null) {
                content += line;
            }

            // System.out.println("content => " + content);

            // Close our reader, no longer needed
            reader.close();

            // Decode the post request
            Map<String, String> post = processPostRequest(content);
            // System.out.println(post);

            // Check for required values
            if (!post.containsKey("guid")) {
                finishRequest("ERR Invalid arguments.", baseRequest, response);
                return;
            }

            // GeoIP
            String geoipCountryCode = request.getHeader("GEOIP_COUNTRY_CODE");

            // fallback incase it's being proxied
            if (geoipCountryCode == null) {
                geoipCountryCode = request.getHeader("HTTP_X_GEOIP");
            }

            // Data that was posted to us
            String guid = post.get("guid");
            String serverVersion = post.get("server");
            String pluginVersion = post.get("version");
            boolean isPing = post.containsKey("ping");
            int revision;
            int players;

            // gracefully pull out numbers incase they send malformed numbers
            try {
                revision = post.containsKey("revision") ? Integer.parseInt(post.get("revision")) : 4;
                players = post.containsKey("players") ? Integer.parseInt(post.get("players")) : 0;
            } catch (NumberFormatException e) {
                finishRequest("ERR Invalid arguments.", baseRequest, response);
                return;
            }

            // Check for nulls
            if (guid == null || serverVersion == null || pluginVersion == null) {
                finishRequest("ERR Invalid arguments.", baseRequest, response);
                return;
            }

            if (players < 0 || players > 2000) {
                finishRequest("ERR Invalid arguments.", baseRequest, response);
                return;
            }

            // Load the plugin
            final Plugin plugin = mcstats.loadPlugin(pluginName);
            // logger.info("plugin [ id => " + plugin.getId() + " , name => " + plugin.getName() + " ]");

            if (plugin.getId() == -1) {
                finishRequest("ERR Rejected.", baseRequest, response);
                return;
            }

            // Load the server
            final Server server = mcstats.loadServer(guid);
            // logger.info("server [ id => " + server.getId() + " , guid => " + server.getGUID() + " ]");

            // Check violations
            if (server.getViolationCount() >= MAX_VIOLATIONS_ALLOWED && !server.isBlacklisted()) {
                server.setBlacklisted(true);
                mcstats.getDatabase().blacklistServer(server);
            }

            // Something bad happened
            if (plugin == null || server == null) {
                finishRequest("ERR Something bad happened", baseRequest, response);
                return;
            }

            // Load the server plugin object which stores data shared between this server and plugin
            ServerPlugin serverPlugin = mcstats.loadServerPlugin(server, plugin, pluginVersion);

            // logger.info("ServerPlugin => " + serverPlugin.getVersion() + " , " + serverPlugin.getUpdated());

            // Something bad happened????
            if (serverPlugin == null) {
                finishRequest("OK", baseRequest, response);
                return;
            }

            // Now check the basic stuff
            if (!serverPlugin.getVersion().equals(pluginVersion) && !server.isBlacklisted()) {
                // only add version history if their current version isn't blank
                // if their current version is blank, that means they just
                // installed the plugin
                if (!serverPlugin.getVersion().isEmpty()) {
                    PluginVersion version = mcstats.loadPluginVersion(plugin, pluginVersion);

                    if (version != null) {
                        String query = "INSERT INTO VersionHistory (Plugin, Server, Version, Created) VALUES (" + plugin.getId() + ", " + server.getId() + ", " + version.getId() + ", UNIX_TIMESTAMP())";
                        new RawQuery(this.mcstats, query).save();
                    }
                }

                serverPlugin.setVersion(pluginVersion);
            }

            if (server.getRevision() != revision) {
                server.setRevision(revision);
            }

            if (!server.getServerVersion().equals(serverVersion)) {
                server.setServerVersion(serverVersion);
            }

            if (server.getPlayers() != players && players >= 0) {
                server.setPlayers(players);
            }

            if (geoipCountryCode != null && !geoipCountryCode.isEmpty() && !server.getCountry().equals(geoipCountryCode)) {
                server.setCountry(geoipCountryCode);
            }

            // Identify the server & minecraft version
            String canonicalServerVersion = mcstats.getServerBuildIdentifier().getServerVersion(serverVersion);
            String minecraftVersion = mcstats.getServerBuildIdentifier().getMinecraftVersion(serverVersion);

            // Improve CB++ detection
            if (canonicalServerVersion.equals("CraftBukkit")) {
                for (Map.Entry<Plugin, ServerPlugin> entry : server.getPlugins().entrySet()) {
                    ServerPlugin serverPlugin1 = entry.getValue();

                    // CB++
                    if (entry.getKey().getId() == 137) {
                        // make sure it's within the last day
                        if ((System.currentTimeMillis() / 1000) - serverPlugin1.getUpdated() < 86400) {
                            // CB++ !
                            canonicalServerVersion = "CraftBukkit++";
                        }

                        break;
                    }
                }
            }

            if (!server.getServerSoftware().equals(canonicalServerVersion)) {
                server.setServerSoftware(canonicalServerVersion);
            }

            if (!server.getMinecraftVersion().equals(minecraftVersion)) {
                server.setMinecraftVersion(minecraftVersion);
            }

            // Increment start counters if needed
            if (!isPing) {
                plugin.setGlobalHits(plugin.getGlobalHits() + 1);
                // remove server startups ?
            }

            // Custom Data
            if (revision >= 4 && !server.getCountry().equals("SG") && (geoipCountryCode == null || !geoipCountryCode.equals("SG"))) {
                final Map<Column, Integer> customData;

                // Extract custom data
                if (revision >= 5) {
                    customData = extractCustomData(plugin, post);
                } else { // legacy
                    customData = extractCustomDataLegacy(plugin, post);
                }

                if (customData != null && customData.size() > 0) {
                    executor.execute(new Runnable() {
                        public void run() {
                            // Begin building the query
                            String query = "INSERT INTO CustomData (Server, Plugin, ColumnID, DataPoint, Updated) VALUES";
                            int currentSeconds = (int) (System.currentTimeMillis() / 1000);

                            // Iterate through each column
                            for (Map.Entry<Column, Integer> entry : customData.entrySet()) {
                                Column column = entry.getKey();
                                int value = entry.getValue();

                                // append the query
                                query += " (" + server.getId() + ", " + plugin.getId() + ", " + column.getId() + ", " + value + ", " + currentSeconds + "),";
                            }

                            // Remove the last comma
                            query = query.substring(0, query.length() - 1);

                            // add the duplicate key entry
                            query += " ON DUPLICATE KEY UPDATE DataPoint = VALUES(DataPoint) , Updated = VALUES(Updated)";

                            // queue the query
                            new RawQuery(mcstats, query).save();
                        }
                    });
                }
            }

            // R6 additions
            if (revision >= 6) {
                String osname = post.get("osname");
                String osarch = post.get("osarch");
                String osversion = post.get("osversion");
                String java_name = "";
                String java_version = post.get("java_version");
                int cores;
                int online_mode;

                if (osname == null) {
                    osname = "Unknown";
                    osversion = "Unknown";
                }

                if (osversion == null) {
                    osversion = "Unknown";
                }

                if (java_version == null) {
                    java_version = "Unknown";
                } else {
                    if (java_version.startsWith("1.") && java_version.length() > 3) {
                        java_name = java_version.substring(0, java_version.indexOf('.', java_version.indexOf('.') + 1));
                        java_version = java_version.substring(java_name.length() + 1);
                    }
                }

                if (osname != null) {
                    try {
                        cores = Integer.parseInt(post.get("cores"));
                        online_mode = Boolean.parseBoolean(post.get("online-mode")) ? 1 : 0;
                    } catch (Exception e) {
                        cores = 0;
                        online_mode = -1;
                    }

                    if (osarch.equals("i386")) {
                        osarch = "x86";
                    }

                    // Windows' version is just 6.1, 5.1, etc, so make the version just the name
                    // so name = "Windows", version = "7", "Server 2008 R2", "XP", etc
                    if (osname.startsWith("Windows")) {
                        osversion = osname.substring(8);
                        osname = "Windows";
                    }

                    if (osversion.equals("6.1")) {
                        osversion = "7";
                        osname = "Windows";
                    }

                    if (!osname.equals(server.getOSName())) {
                        server.setOSName(osname);
                    }

                    if (osarch != null && !osarch.equals(server.getOSArch())) {
                        server.setOSArch(osarch);
                    }

                    if (!osversion.equals(server.getOSVersion())) {
                        server.setOSVersion(osversion);
                    }

                    if (server.getCores() != cores) {
                        server.setCores(cores);
                    }

                    if (server.getOnlineMode() != online_mode) {
                        server.setOnlineMode(online_mode);
                    }

                    if (!java_name.equals(server.getJavaName())) {
                        server.setJavaName(java_name);
                    }

                    if (!java_version.equals(server.getJavaVersion())) {
                        server.setJavaVersion(java_version);
                    }
                }
            }

            // get the last graph time
            int lastGraphUpdate = normalizeTime();

            if (lastGraphUpdate > serverPlugin.getUpdated()) {
                server.setViolationCount(0);
                finishRequest("OK This is your first update this hour.", baseRequest, response);
            } else {
                finishRequest("OK", baseRequest, response);
            }

            // close the connection
            // r.getConnection().getEndPoint().close();

            // force the server plugin to update
            serverPlugin.setUpdated((int) (System.currentTimeMillis() / 1000L));
            plugin.setLastUpdated((int) (System.currentTimeMillis() / 1000L));

            // Save everything
            // They keep flags internally to know if something was modified so all is well
            server.save();
            serverPlugin.save();
            plugin.save();
        } catch (Exception e) {
            e.printStackTrace();

            // pretend nothing happened
            finishRequest("OK", baseRequest, response);
        }
    }

    /**
     * Normalize the time to the nearest graphing period
     *
     * @return
     */
    private int normalizeTime() {
        int currentTimeSeconds = (int) (System.currentTimeMillis() / 1000);

        // The graphing interval in minutes
        // TODO not hardcoded :3
        int interval = 30;

        // calculate the devisor denominator
        int denom = interval * 60;

        return (int) Math.round((currentTimeSeconds - (denom / 2d)) / denom) * denom;
    }

    /**
     * Extract the custom data from a post request
     *
     * @param plugin
     * @param post
     * @return
     */
    private Map<Column, Integer> extractCustomData(Plugin plugin, Map<String, String> post) {
        Map<Column, Integer> customData = new HashMap<Column, Integer>();

        for (Map.Entry<String, String> entry : post.entrySet()) {
            String postKey = entry.getKey();
            String postValue = entry.getValue();

            // we only want numeric values, skip the rest
            int value;

            try {
                value = Integer.parseInt(postValue);
            } catch (NumberFormatException e) {
                continue;
            }

            // Split by the separator
            // [0] = magic str
            // [1] = graph name
            // [2] = column name
            String[] graphData = postKey.split("~~");

            if (graphData.length != 3) {
                continue;
            }

            // get the data as mentioned above
            String graphName = graphData[1];
            String columnName = graphData[2];

            // Load the graph
            Graph graph = mcstats.loadGraph(plugin, graphName);

            if (graph == null) {
                continue;
            }

            // Load the column
            Column column = graph.loadColumn(columnName);

            if (column != null) {
                customData.put(column, value);
            }
        }

        return customData;
    }

    /**
     * Extract custom data from a Metrics R4 reporter
     *
     * @param plugin
     * @param post
     * @return
     */
    private Map<Column, Integer> extractCustomDataLegacy(Plugin plugin, Map<String, String> post) {
        Map<Column, Integer> customData = new HashMap<Column, Integer>();

        // All of the custom data is thrown onto the 'Default' graph since we have no
        // idea what a "graph" is back in R4
        Graph graph = mcstats.loadGraph(plugin, "Default");

        for (Map.Entry<String, String> entry : post.entrySet()) {
            String postKey = entry.getKey();
            String postValue = entry.getValue();

            // we only want numeric values, skip the rest
            int value;

            try {
                value = Integer.parseInt(postValue);
            } catch (NumberFormatException e) {
                continue;
            }

            // Look for the magic string
            if (!postKey.startsWith("Custom")) {
                continue;
            }

            // Extract the column name
            String columnName = postKey.substring(6).replaceAll("_", " ");

            if (graph == null) {
                continue;
            }

            // Load the column
            Column column = graph.loadColumn(columnName);

            if (column != null) {
                customData.put(column, value);
            }
        }

        return customData;
    }

    /**
     * Get the plugin name from a request
     *
     * @param request
     * @return
     */
    private String getPluginName(HttpServletRequest request) {
        String url = request.getRequestURI();

        // /report/PluginName
        if (url.startsWith("//report/")) {
            return url.substring("//report/".length());
        }

        else if (url.startsWith("/report/")) {
            return url.substring("/report/".length());
        }

        // /report.php?plugin=PluginName
        else if (url.startsWith("/report.php?plugin=")) {
            return url.substring("/report.php?plugin=".length());
        }

        return null;
    }

    /**
     * Process a post request and return all of its key/value pairs
     *
     * @param content
     * @return
     */
    private Map<String, String> processPostRequest(String content) {
        Map<String, String> store = new HashMap<String, String>();

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
