package org.mcstats.generator;

import com.google.common.collect.ImmutableMap;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.mcstats.generator.aggregator.DecoderReflectionAggregator;
import org.mcstats.generator.aggregator.IncrementAggregator;
import org.mcstats.generator.aggregator.ReflectionAggregator;
import org.mcstats.model.Server;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Class for running tests on sample server data
 */
public class TestPluginGenerator {

    private static final Logger logger = Logger.getLogger("TestAggregator");

    public TestPluginGenerator() {
        List<Server> servers = loadTestServers();

        logger.info("Loaded " + servers.size() + " test servers");

        AbstractGenerator<Server> generator = new AbstractGenerator<Server>() {
            @Override
            public List<Server> getAllInstances() {
                return servers;
            }
        };

        generator.addAggregator(new ReflectionAggregator<>(Server.class, "serverSoftware", "Server Software"));
        generator.addAggregator(new ReflectionAggregator<>(Server.class, "minecraftVersion", "Game Version"));
        generator.addAggregator(new ReflectionAggregator<>(Server.class, "osarch", "System Arch"));
        generator.addAggregator(new ReflectionAggregator<>(Server.class, "cores", "System Cores"));

        generator.addAggregator(new IncrementAggregator<>("Global Statistics", "Servers"));
        generator.addAggregator(new ReflectionAggregator<>(Server.class, "players", "Global Statistics", "Players"));

        generator.addAggregator(new DecoderReflectionAggregator<Server, Integer>(Server.class, "online_mode", "Auth Mode", value -> {
            switch (value) {
                case 1:
                    return "Online";
                case 0:
                    return "Offline";
                default:
                    return "Unknown";
            }
        }));

        ImmutableMap<String, Map<String, Datum>> data = generator.generateAll();

        data.forEach((graphName, columnData) -> {
            System.out.println("Graph: " + graphName);

            columnData.forEach((columnName, datum) -> {
                System.out.println("\tColumn: " + columnName + " -> " + datum);
            });
        });
    }

    private List<Server> loadTestServers() {
        List<Server> result = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get("servers-limit-1000.json"))) {
            String line;

            while ((line = reader.readLine()) != null) {
                JSONObject root = (JSONObject) JSONValue.parse(line);

                Server server = new Server(null);
                server.setServerSoftware(root.get("server_software").toString());
                server.setMinecraftVersion(root.get("minecraft_version").toString());
                server.setCores(Integer.parseInt(root.get("cores").toString()));
                server.setOSName(root.get("os_name").toString());
                server.setOSArch(root.get("os_arch").toString());
                server.setOSVersion(root.get("os_version").toString());
                server.setJavaName(root.get("java_name").toString());
                server.setJavaVersion(root.get("java_version").toString());
                server.setOnlineMode(Integer.parseInt(root.get("online_mode").toString()));
                server.setCountry(root.get("country").toString());
                server.setPlayers(Integer.parseInt(root.get("players").toString()));

                // TODO plugins

                result.add(server);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static void main(String[] args) {
        new TestPluginGenerator();
    }

}
