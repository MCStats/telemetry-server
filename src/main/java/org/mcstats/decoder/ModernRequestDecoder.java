package org.mcstats.decoder;

import com.google.common.collect.ImmutableMap;
import org.eclipse.jetty.server.Request;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class ModernRequestDecoder implements RequestDecoder {

    @Override
    public DecodedRequest decode(Request request) throws IOException {
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

        if (post.containsKey("plugin")) {
            decoded.pluginName = post.get("plugin").toString();
        }

        decoded.guid = post.get("guid").toString();
        decoded.serverVersion = (new StringBuilder()).append("").append(post.get("server_version")).toString();
        decoded.pluginVersion = (new StringBuilder()).append("").append(post.get("plugin_version")).toString();
        decoded.isPing = post.containsKey("ping");
        decoded.revision = Integer.parseInt(request.getHeader("User-Agent").substring("MCStats/".length()));
        decoded.playersOnline = Long.valueOf(tryParseLong(post.get("players_online"))).intValue();

        if (post.containsKey("ping")) {
            String pingVal = post.get("ping").toString();
            decoded.isPing = pingVal.equals("1") || Boolean.parseBoolean(pingVal);
        } else {
            decoded.isPing = false;
        }

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

            if (post.containsKey("auth_mode")) {
                String authMode = post.get("auth_mode").toString();

                try {
                    decoded.authMode = Integer.parseInt(authMode);
                } catch (NumberFormatException e) {
                    decoded.authMode = Boolean.parseBoolean(authMode) ? 1 : 0;
                }
            } else {
                decoded.authMode = 1;
            }

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

        decoded.customData = ImmutableMap.copyOf(extractCustomData(post));
        return decoded;
    }

    /**
     * Extract custom data from a json post
     *
     * @param post
     * @return
     */
    private Map<String, Map<String, Long>> extractCustomData(JSONObject post) {
        Map<String, Map<String, Long>> result = new HashMap<>();

        if (!post.containsKey("graphs")) {
            return result;
        }

        JSONObject graphs = (JSONObject) post.get("graphs");

        for (Object o : graphs.entrySet()) {
            Map.Entry<String, JSONObject> entry = (Map.Entry<String, JSONObject>) o;

            String graphName = entry.getKey();
            JSONObject columns = entry.getValue();

            Map<String, Long> columnValues = new HashMap<>();

            for (Object o2 : columns.entrySet()) {
                Map.Entry<String, Long> entryColumn = (Map.Entry<String, Long>) o2;

                String columnName = entryColumn.getKey();
                long value = tryParseLong(entryColumn.getValue());

                columnValues.put(columnName, value);
            }

            result.put(graphName, columnValues);
        }

        return result;
    }

    /**
     * Attempt to parse a long from an object
     *
     * @param input
     * @return
     */
    private long tryParseLong(Object input) {
        if (input == null) {
            return 0;
        }

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
