package org.mcstats.decoder;

import org.json.simple.JSONObject;
import org.mcstats.model.Column;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class DecodedRequest {

    /**
     * Revision number of the client
     */
    public int revision;

    /**
     * If the request is not the first request on server startup or not
     */
    public boolean isPing;

    /**
     * The plugin data is being sent for
     */
    public String pluginName;

    /**
     * The serverId in the request
     */
    public String serverId;

    /**
     * Two letter country code this request is from
     */
    public String countryCode = "ZZ";

    /**
     * The version of the server software
     */
    public String serverVersion;

    /**
     * The version of the plugin
     */
    public String pluginVersion;

    /**
     * The number of players online
     */
    public int playersOnline;

    /**
     * OS name
     */
    public String osname;

    /**
     * OS version
     */
    public String osversion;

    /**
     * OS arch (e.g. x64_64)
     */
    public String osarch;

    /**
     * Major java version
     */
    public String javaName;

    /**
     * Minor java version
     */
    public String javaVersion;

    /**
     * Number of cores on the hardware running the server
     */
    public int cores;

    /**
     * The auth mode of the server
     */
    public int authMode;

    /**
     * Any custom data for the plugin this request is for
     */
    public Map<Column, Long> customData;

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        String newLine = System.getProperty("line.separator");
        result.append(getClass().getName());
        result.append(" {");
        result.append(newLine);
        Field fields[] = getClass().getDeclaredFields();
        Field arr[] = fields;

        for (Field field : arr) {
            result.append("  ");
            try {
                result.append(field.getName());
                result.append(": ");
                result.append(field.get(this));
            } catch (IllegalAccessException ex) {
                System.out.println(ex);
            }
            result.append(newLine);
        }

        result.append("}");
        return result.toString();
    }

    /**
     * Converts the request to JSON
     *
     * @return
     */
    public JSONObject toJson() {
        JSONObject root = new JSONObject();

        root.put("revision", revision);
        root.put("isPing", Boolean.toString(isPing));
        root.put("serverVersion", serverVersion);
        root.put("pluginVersion", pluginVersion);
        root.put("playersOnline", playersOnline);
        root.put("osname", osname);
        root.put("osversion", osversion);
        root.put("osarch", osarch);
        root.put("javaName", javaName);
        root.put("javaVersion", javaVersion);
        root.put("cores", cores);
        root.put("authMode", authMode);

        Map<String, Map<String, Long>> customDataRoot = new HashMap<>();

        customData.forEach((column, value) -> {
            String graphName = column.getGraph().getName();
            String columnName = column.getName();

            Map<String, Long> columnDataRoot = customDataRoot.getOrDefault(graphName, new HashMap<>());

            columnDataRoot.put(columnName, value);
        });

        root.put("customData", customData);

        return root;
    }

}
