package org.mcstats.generator;

import com.google.common.collect.ImmutableMap;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.mcstats.generator.aggregator.DecoderReflectionAggregator;
import org.mcstats.generator.aggregator.IncrementAggregator;
import org.mcstats.generator.aggregator.ReflectionAggregator;
import org.mcstats.generator.aggregator.ReflectionDonutAggregator;
import org.mcstats.generator.aggregator.plugin.CountryAggregator;
import org.mcstats.generator.aggregator.plugin.CustomDataPluginAggregator;
import org.mcstats.generator.aggregator.plugin.RevisionPluginAggregator;
import org.mcstats.generator.aggregator.plugin.VersionDemographicsPluginAggregator;
import org.mcstats.generator.aggregator.plugin.VersionTrendsPluginAggregator;
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

        PluginGenerator generator = new PluginGenerator(() -> servers);

        generator.addAggregator(new ReflectionAggregator<>(Server.class, "Server Software", "serverSoftware"));
        generator.addAggregator(new ReflectionAggregator<>(Server.class, "Game Version", "minecraftVersion"));
        generator.addAggregator(new ReflectionAggregator<>(Server.class, "System Arch", "osarch"));
        generator.addAggregator(new ReflectionAggregator<>(Server.class, "System Cores", "cores"));

        generator.addAggregator(new ReflectionDonutAggregator<>(Server.class, "Operating System", "osname", "osversion"));
        generator.addAggregator(new ReflectionDonutAggregator<>(Server.class, "Java Version", "java_name", "java_version"));

        generator.addAggregator(new IncrementAggregator<>("Global Statistics", "Servers"));
        generator.addAggregator(new ReflectionAggregator<>(Server.class, "Global Statistics", "Players", "players"));

        generator.addAggregator(new DecoderReflectionAggregator<Server, Integer>(Server.class, "Auth Mode", "online_mode", value -> {
            switch (value) {
                case 1:
                    return "Online";
                case 0:
                    return "Offline";
                default:
                    return "Unknown";
            }
        }));

        generator.addAggregator(new CountryAggregator());
        generator.addAggregator(new RevisionPluginAggregator());
        generator.addAggregator(new CustomDataPluginAggregator());
        generator.addAggregator(new VersionDemographicsPluginAggregator());
        generator.addAggregator(new VersionTrendsPluginAggregator());

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
