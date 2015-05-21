package org.mcstats.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class ServerBuildIdentifier {

    /**
     * The file definitions are stored in
     */
    private static final String DEFINITIONS_RESOURCE = "/server-definitions.txt";

    /**
     * The default server when none is matched
     */
    private static final String DEFAULT_SERVER = "Unknown";

    /**
     * The list of server definitions. LinkedHashMap to retain order.
     */
    private final Map<String, String> definitions = new LinkedHashMap<>();

    private final LoadingCache<String, String> definitionCache = CacheBuilder.newBuilder()
            .maximumSize(10000) // 10k
            .build(new CacheLoader<String, String>() {

                public String load(String key) {
                    for (Map.Entry<String, String> entry : definitions.entrySet()) {
                        String definitionKey = entry.getKey();

                        if (key.contains(definitionKey)) {
                            return entry.getValue();
                        }
                    }

                    return DEFAULT_SERVER; // DEFAULT
                }

            });

    public ServerBuildIdentifier() {
        try {
            loadDefinitions();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get a server's server version
     *
     * @param server
     * @return
     */
    public String getServerVersion(String server) {
        try {
            return definitionCache.get(server);
        } catch (ExecutionException e) {
            e.printStackTrace();
            return DEFAULT_SERVER;
        }
    }

    /**
     * Get a minecraft version from a server string
     *
     * @param server
     * @return
     */
    public String getMinecraftVersion(String server) {
        server = server.replaceAll(" ", "");
        int index = server.indexOf("(MC:");

        if (index == -1) {
            return "Unknown";
        }

        return server.substring(index + 4, server.length() - 1);
    }

    /**
     * Load all of the server definitions
     *
     * @throws IOException
     */
    public void loadDefinitions() throws IOException {
        // clear out the old definitions
        clear();

        // read the file
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(DEFINITIONS_RESOURCE)))) {
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] def = line.split("\\s+");

                if (def.length == 2) {
                    definitions.put(def[0], def[1]);
                }
            }
        }
    }

    /**
     * Clear out the definitions
     */
    private void clear() {
        definitions.clear();
        definitionCache.invalidateAll();
    }

}
