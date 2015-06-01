package org.mcstats.generation.plugin;

import org.apache.log4j.Logger;
import org.mcstats.MCStats;
import org.mcstats.aws.s3.AccumulatorStorage;
import org.mcstats.db.Database;
import org.mcstats.db.GraphStore;
import org.mcstats.db.ModelCache;
import org.mcstats.generator.GeneratedData;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.util.Tuple;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PluginGraphGenerator {

    private static final Logger logger = Logger.getLogger(PluginGraphGenerator.class);

    private final MCStats mcstats;
    private final Database database;
    private final ModelCache modelCache;
    private final GraphStore graphStore;
    private final AccumulatorStorage accumulatorStorage;
    private final int numThreads;

    @Inject
    public PluginGraphGenerator(@Named("generator.threads") int numThreads,
                                MCStats mcstats, Database database, ModelCache modelCache, GraphStore graphStore, AccumulatorStorage accumulatorStorage) {
        this.mcstats = mcstats;
        this.database = database;
        this.modelCache = modelCache;
        this.graphStore = graphStore;
        this.accumulatorStorage = accumulatorStorage;
        this.numThreads = numThreads;
    }

    /**
     * Generates data from the given bucket.
     *
     * @param bucket
     */
    public void run(int bucket) {
        logger.info("Generating data from bucket: " + bucket);

        long start = System.currentTimeMillis();

        Map<Integer, Map<String, Map<String, Long>>> data = accumulatorStorage.getPluginData(bucket);

        if (data == null) {
            logger.error("No bucket found: " + bucket);
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        Map<Graph, List<Tuple<Column, GeneratedData>>> generatedGraphs = new ConcurrentHashMap<>();

        data.keySet().forEach(pluginId -> executor.submit(() -> {
            Map<String, Map<String, Long>> pluginData = data.get(pluginId);

            final Plugin plugin = mcstats.loadPlugin(pluginId);

            logger.debug("Generating data for plugin: " + plugin.getId());

            pluginData.forEach((graphName, graphData) -> {
                List<Tuple<Column, GeneratedData>> generatedData = new ArrayList<>();
                Graph graph = plugin.getGraph(graphName);

                //
                Map<String, Column> columns = loadAllColumns(graph, graphData.keySet());

                graphData.forEach((columnName, value) -> {
                    // TODO get/create column
                    Column column = columns.get(columnName);

                    if (column == null) {
                        logger.error("Null column for pluginId: " + plugin.getId() + " graphName: " + graph.getName() + " columnName: " + columnName);
                        return;
                    }

                    generatedData.add(new Tuple<>(column, new GeneratedData(value.intValue(), 0, 0, 0)));
                });

                generatedGraphs.put(graph, generatedData);
            });
        }));

        executor.shutdown();

        try {
            if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                logger.error("Graph generation failed");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        graphStore.insert(generatedGraphs, bucket);

        long taken = System.currentTimeMillis() - start;
        logger.info("Generated bucket " + bucket + " in " + taken + " ms");
    }

    /**
     * Loads all of the given columns. They will be fetched or created, so all columns
     * should be in the resultant map.
     *
     * Map: Column-Name => Column
     *
     * Note: The returned map will be one which has case-insensitive keys, so it should
     * not be copied so that this can be exploited.
     *
     * @param graph
     * @param columnsToFetch
     * @return
     */
    private Map<String, Column> loadAllColumns(Graph graph, Set<String> columnsToFetch) {
        Map<String, Column> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        List<Column> columns = modelCache.getPluginGraphColumns(graph);
        Set<String> missingColumns = getMissingColumns(columns, columnsToFetch);

        if (missingColumns.size() == 0) {
            // All columns were already cached
            columns.forEach(column -> result.put(column.getName(), column));
            return result;
        }

        columns = database.loadColumns(graph);
        Set<String> columnsToCreate = getMissingColumns(columns, columnsToFetch);

        for (String columnName : columnsToCreate) {
            Column column = database.createColumn(graph, columnName);

            if (column == null) {
                continue;
            }

            columns.add(column);
        }

        modelCache.cachePluginGraphColumns(graph, columns);
        columns.forEach(column -> result.put(column.getName(), column));
        return result;
    }

    /**
     * Gets all of the columns that are not inside the columns list from a known list of names.
     *
     * @param knownColumns
     * @param columnsToFetch
     * @return
     */
    private Set<String> getMissingColumns(List<Column> knownColumns, Set<String> columnsToFetch) {
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        result.addAll(columnsToFetch);
        result.removeAll(knownColumns.stream().map(Column::getName).collect(Collectors.toSet()));

        return result;
    };

}