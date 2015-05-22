package org.mcstats.decoder;

import java.lang.reflect.Field;
import java.util.Map;

public class DecodedRequest {

    /**
     * The plugin id the request is for.
     */
    public int plugin;

    /**
     * Revision number of the client
     */
    public int revision;

    /**
     * If the request is not the first request on server startup or not
     */
    public boolean isPing;

    /**
     * The uuid in the request
     */
    public String uuid;

    /**
     * The country the server is from
     */
    public String country;

    /**
     * The version of the server software
     */
    public String serverVersion = "Unknown";

    /**
     * The version of the plugin
     */
    public String pluginVersion = "Unknown";

    /**
     * The number of players online
     */
    public int playersOnline = 0;

    /**
     * OS name
     */
    public String osname = "Unknown";

    /**
     * OS version
     */
    public String osversion = "Unknown";

    /**
     * OS arch (e.g. x64_64)
     */
    public String osarch = "Unknown";

    /**
     * Major java version
     */
    public String javaName = "Unknown";

    /**
     * Minor java version
     */
    public String javaVersion = "Unknown";

    /**
     * Number of cores on the hardware running the server
     */
    public int cores = -1;

    /**
     * The auth mode of the server
     */
    public int authMode;

    /**
     * Any custom data in this request.
     *
     * graphName => { columnName: value }
     */
    public Map<String, Map<String, Long>> customData;

    public int getPluginId() {
        return plugin;
    }

    public int getRevision() {
        return revision;
    }

    public boolean isPing() {
        return isPing;
    }

    public String getUuid() {
        return uuid;
    }

    public String getCountry() {
        return country;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public int getPlayersOnline() {
        return playersOnline;
    }

    public String getOSName() {
        return osname;
    }

    public String getOSVersion() {
        return osversion;
    }

    public String getOSArch() {
        return osarch;
    }

    public String getJavaName() {
        return javaName;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public int getCores() {
        return cores;
    }

    public int getAuthMode() {
        return authMode;
    }

    public Map<String, Map<String, Long>> getCustomData() {
        return customData;
    }

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
