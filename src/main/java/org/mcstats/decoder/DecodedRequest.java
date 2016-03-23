package org.mcstats.decoder;

import com.google.common.collect.ImmutableMap;

import java.lang.reflect.Field;
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
     * The name of the plugin. This might be null, if it wasn't provided in the request body.
     */
    public String pluginName = null;

    /**
     * The guid in the request
     */
    public String guid;

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
    public ImmutableMap<String, Map<String, Long>> customData;

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

}
