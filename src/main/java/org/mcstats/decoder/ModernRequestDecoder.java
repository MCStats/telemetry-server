package org.mcstats.decoder;

import org.eclipse.jetty.server.Request;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.mcstats.MCStats;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class ModernRequestDecoder implements RequestDecoder {

    private MCStats mcstats;

    public ModernRequestDecoder(MCStats mcstats) {
        this.mcstats = mcstats;
    }

    /**
     * {@inheritDoc}
     */
    public DecodedRequest decode(Plugin plugin, Request request) throws IOException {
        String encoding = request.getHeader("Content-Encoding");
        String content = "";

        BufferedReader reader;
        if (encoding != null && encoding.equals("gzip")) {
            reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(request.getInputStream()), "UTF-8"));
        } else {
            reader = new BufferedReader(new InputStreamReader(request.getInputStream(), "UTF-8"));
        }

        String line;
        while ((line = reader.readLine()) != null) {
            content += line;
        }

        reader.close();
        JSONObject post = (JSONObject) JSONValue.parse(content);
        if (post == null || !post.containsKey("guid")) {
            return null;
        }
        DecodedRequest decoded = new DecodedRequest();
        decoded.guid = (String) post.get("guid");
        decoded.serverVersion = (new StringBuilder()).append("").append(post.get("server_version")).toString();
        decoded.pluginVersion = (new StringBuilder()).append("").append(post.get("plugin_version")).toString();
        decoded.isPing = post.containsKey("ping");
        decoded.revision = Integer.parseInt(request.getHeader("User-Agent").substring("MCStats/".length()));
        decoded.playersOnline = Long.valueOf(tryParseLong(post.get("players_online"))).intValue();
        if (decoded.guid == null || decoded.serverVersion == null || decoded.pluginVersion == null) {
            return null;
        }
        if (decoded.playersOnline < 0 || decoded.playersOnline > 2000) {
            decoded.playersOnline = 0;
        }
        if (decoded.revision >= 6) {
            decoded.osname = (String) post.get("osname");
            decoded.osarch = (String) post.get("osarch");
            decoded.osversion = (new StringBuilder()).append("").append(post.get("osversion")).toString();
            decoded.javaName = "";
            decoded.javaVersion = (String) post.get("java_version");
            decoded.cores = Long.valueOf(tryParseLong(post.get("cores"))).intValue();
            decoded.authMode = Long.valueOf(tryParseLong(post.get("auth_mode"))).intValue();
            if (decoded.osname == null) {
                decoded.osname = "Unknown";
                decoded.osversion = "Unknown";
            }
            if (decoded.osversion.isEmpty()) {
                decoded.osversion = "Unknown";
            }
            if (decoded.javaVersion == null) {
                decoded.javaVersion = "Unknown";
            } else if (decoded.javaVersion.startsWith("1.") && decoded.javaVersion.length() > 3) {
                decoded.javaName = decoded.javaVersion.substring(0, decoded.javaVersion.indexOf('.', decoded.javaVersion.indexOf('.') + 1));
                decoded.javaVersion = decoded.javaVersion.substring(decoded.javaName.length() + 1);
            }
        }
        decoded.customData = extractCustomData(plugin, post);
        return decoded;
    }

    /**
     * Extract custom data from a json post
     *
     * @param plugin
     * @param post
     * @return
     */
    private Map<Column, Long> extractCustomData(Plugin plugin, JSONObject post) {
        Map<Column, Long> customData = new HashMap();
        if (!post.containsKey("graphs")) {
            return customData;
        }

        JSONObject graphs = (JSONObject) post.get("graphs");

        for (Object o : graphs.entrySet()) {
            Map.Entry<String, JSONObject> entry = (Map.Entry<String, JSONObject>) o;
            String graphName = (String) entry.getKey();
            JSONObject columns = (JSONObject) entry.getValue();
            Graph graph = mcstats.loadGraph(plugin, graphName);

            if (graph != null && graph.getActive() != 0) {
                for (Object o2 : columns.entrySet()) {
                    Map.Entry<String, Long> entryColumn = (Map.Entry<String, Long>) o2;

                    String columnName = (String) entryColumn.getKey();
                    long value = tryParseLong(entryColumn.getValue());
                    org.mcstats.model.Column column = graph.loadColumn(columnName);

                    if (column != null) {
                        customData.put(column, Long.valueOf(value));
                    }
                }
            }
        }

        return customData;
    }

    /**
     * Attempt to parse a long from an object
     *
     * @param input
     * @return
     */
    private long tryParseLong(Object input) {
        String value = input.toString();
        long longValue = -1L;

        try {
            longValue = Long.parseLong(value);
            return longValue;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

}
