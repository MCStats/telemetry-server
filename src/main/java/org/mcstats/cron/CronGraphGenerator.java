package org.mcstats.cron;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import org.mcstats.MCStats;
import org.mcstats.db.GraphStore;
import org.mcstats.db.MongoDBGraphStore;
import org.mcstats.generator.DecoderAggregator;
import org.mcstats.generator.GeneratedData;
import org.mcstats.generator.GraphGenerator;
import org.mcstats.generator.IncrementAggregator;
import org.mcstats.generator.MergeAggregator;
import org.mcstats.generator.ReflectionAggregator;
import org.mcstats.generator.ReflectionDonutAggregator;
import org.mcstats.generator.aggregator.CountryAggregator;
import org.mcstats.generator.aggregator.CustomDataAggregator;
import org.mcstats.generator.aggregator.RankAggregator;
import org.mcstats.generator.aggregator.RevisionAggregator;
import org.mcstats.generator.aggregator.VersionChangesAggregator;
import org.mcstats.generator.aggregator.VersionDemographicsAggregator;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;
import org.mcstats.util.Tuple;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public class CronGraphGenerator implements Runnable {

    private Logger logger = Logger.getLogger(getClass().getSimpleName());

    private MCStats mcstats;

    /**
     * A list of all generators
     */
    private List<GraphGenerator> generators = new LinkedList<>();

    private final AmazonS3 s3;
    private final String archiveS3Bucket;

    public CronGraphGenerator(MCStats mcstats) {
        this.mcstats = mcstats;
        this.archiveS3Bucket = mcstats.getConfig().getProperty("generator.archive_s3_bucket");
        s3 = new AmazonS3Client();

        // -- custom data

        // -- auth mode
        // -- game version
        // -- global stats
        // -- > Players
        // -- > Servers
        // -- java version, donut (Reflection2Aggregator)
        // -- operating system, donut
        // -- rank
        // -- revision
        // -- server locations
        // -- server software
        // -- system arch
        // -- system cores
        // -- version demographics
        // -- version trends

        generators.add(new MergeAggregator(new IncrementAggregator("Global Statistics", "Servers"), new ReflectionAggregator("players", "Global Statistics", "Players")));

        generators.add(new ReflectionAggregator("serverSoftware", "Server Software"));
        generators.add(new ReflectionAggregator("minecraftVersion", "Game Version"));
        generators.add(new ReflectionAggregator("osarch", "System Arch"));
        generators.add(new ReflectionAggregator("cores", "System Cores"));

        generators.add(new RevisionAggregator("MCStats Revision"));

        generators.add(new ReflectionDonutAggregator("osname", "osversion", "Operating System"));
        generators.add(new ReflectionDonutAggregator("java_name", "java_version", "Java Version"));

        generators.add(new VersionDemographicsAggregator("Version Demographics"));

        generators.add(new VersionChangesAggregator("Version Trends"));

        generators.add(new CountryAggregator("Server Locations"));

        generators.add(new CustomDataAggregator());

        generators.add(new RankAggregator());

        generators.add(new DecoderAggregator<Integer>("online_mode", "Auth Mode", value -> {
            switch (value) {
                case 1:
                    return "Online";
                case 0:
                    return "Offline";
                default:
                    return "Unknown";
            }
        }));
    }

    /**
     * {@inheritDoc}
     */
    public void run() {
        try {
            logger.info("Beginning graph generation");
            GraphStore store = mcstats.getGraphStore();
            ReportHandler.SOFT_IGNORE_REQUESTS = true;

            if (mcstats.countRecentServers() < 50000) {
                logger.info("Not enough data. Auto correcting internal caches.");
                mcstats.resetInternalCaches();
                return;
            }

            long start = System.currentTimeMillis();

            for (GraphGenerator generator : generators) {
                logger.info("Generating graph for: " + generator);

                Map<Column, GeneratedData> data = generator.generate(mcstats);

                int epoch = ReportHandler.normalizeTime();

                logger.info("Storing " + data.size() + " columns of data");

                Map<Graph, List<Tuple<Column, GeneratedData>>> grouped = new HashMap<>();

                // group together the data for each graph
                for (Map.Entry<Column, GeneratedData> entry : data.entrySet()) {
                    Column column = entry.getKey();
                    GeneratedData columnData = entry.getValue();

                    if (column == null || columnData == null) {
                        continue;
                    }

                    List<Tuple<Column, GeneratedData>> listdata = grouped.get(column.getGraph());

                    if (listdata == null) {
                        listdata = new ArrayList<>();
                        grouped.put(column.getGraph(), listdata);
                    }

                    listdata.add(new Tuple<>(column, columnData));
                }

                for (Map.Entry<Graph, List<Tuple<Column, GeneratedData>>> entry : grouped.entrySet()) {
                    List<Tuple<Column, GeneratedData>> listdata = entry.getValue();
                    store.batchInsert(entry.getKey(), listdata, epoch);
                }

                grouped.clear();
                data.clear();

                // logger.info("Aggregated: " + data);
            }

            logger.info("Beginning final stage of graph generation");

            for (Plugin plugin : mcstats.getCachedPlugins()) {
                int numServers30 = 0;

                for (ServerPlugin serverPlugin : mcstats.getServerPlugins(plugin)) {
                    if (serverPlugin.recentlyUpdated()) {
                        serverPlugin.getServer().setViolationCount(0);
                        // serverPlugin.getServer().save();
                        // serverPlugin.save();
                        numServers30++;
                    }
                }

                plugin.setServerCount30(numServers30);
                plugin.saveNow();
            }

            archiveServers(mcstats.getCachedServers(), ReportHandler.normalizeTime());

            ((MongoDBGraphStore) store).finishGeneration();
            mcstats.resetIntervalData();

            System.gc();
            System.runFinalization();
            System.gc();

            logger.info("Finished graph generation in " + (System.currentTimeMillis() - start) + "ms");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ReportHandler.SOFT_IGNORE_REQUESTS = false;
        }
    }

    /**
     * Archives the given servers.
     *
     * @param servers
     * @param epoch
     */
    private void archiveServers(List<Server> servers, int epoch) {
        // Generate the archive bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(out), "UTF-8"))) {
            for (Server server : servers) {
                server.toJson().writeJSONString(writer);
                writer.newLine();
            }
        } catch (IOException e) {
            logger.severe("Failed to archive servers: " + e.getMessage());
            e.printStackTrace();
        }

        byte[] archiveData = out.toByteArray();

        // Upload to S3
        String s3Key = String.format("server/%d.json.gz", epoch);

        try (InputStream inputStream = new ByteArrayInputStream(archiveData)) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(archiveData.length);
            meta.setContentType("application/json");
            meta.setContentEncoding("gzip");

            s3.putObject(archiveS3Bucket, s3Key, inputStream, meta);

            logger.info("Archived " + servers.size() + " servers to S3 successfully.");
        } catch (IOException e) {
            logger.severe("Failed to archive servers: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
