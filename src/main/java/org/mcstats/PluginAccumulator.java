package org.mcstats;

import org.mcstats.accumulator.CustomDataAccumulator;
import org.mcstats.accumulator.MCStatsInfoAccumulator;
import org.mcstats.accumulator.ServerInfoAccumulator;
import org.mcstats.accumulator.VersionInfoAccumulator;
import org.mcstats.decoder.DecodedRequest;
import org.mcstats.model.Plugin;
import org.mcstats.model.ServerPlugin;
import org.mcstats.util.ServerBuildIdentifier;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A registry of accumulators that are available. Allows accumulating as well.
 */
public class PluginAccumulator {

    /**
     * All available accumulators
     */
    private final Set<Accumulator> accumulators = new HashSet<>();

    /**
     * The plugin used for All Servers
     */
    private final Plugin globalPlugin;

    @Inject
    public PluginAccumulator(MCStats mcstats, ServerBuildIdentifier serverBuildIdentifier) {
        globalPlugin = mcstats.loadPlugin("All Servers");

        // TODO add these dynamically?
        add(new MCStatsInfoAccumulator());
        add(new ServerInfoAccumulator(mcstats, serverBuildIdentifier));
        add(new VersionInfoAccumulator());
        add(new CustomDataAccumulator());
    }

    public Map<Plugin, Map<String, Map<String, Long>>> accumulate(DecodedRequest request, ServerPlugin serverPlugin, Set<String> versionChanges) {
        Map<Plugin, Map<String, Map<String, Long>>> result = new HashMap<>();

        for (Accumulator accumulator : accumulators) {
            AccumulatorContext context = new AccumulatorContext(request, versionChanges);

            // TODO return the list via abstract class instead maybe?
            accumulator.accumulate(context);

            context.getResult().forEach((graphName, data) -> data.forEach((columnName, value) -> {
                insertToAccumulation(result, serverPlugin.getPlugin(), graphName, columnName, value);

                if (accumulator.isGlobal()) {
                    insertToAccumulation(result, globalPlugin, graphName, columnName, value);
                }
            }));
        }

        return result;
    }

    /**
     * Adds an accumulator to the registry
     *
     * @param accumulator
     */
    public void add(Accumulator accumulator) {
        accumulators.add(accumulator);
    }

    /**
     * Inserts a value into the given accumulation result
     *
     * @param result
     * @param plugin
     * @param graphName
     * @param columnName
     * @param value
     */
    private void insertToAccumulation(Map<Plugin, Map<String, Map<String, Long>>> result, Plugin plugin, String graphName, String columnName, long value) {
        if (graphName == null || columnName == null) {
            return;
        }

        Map<String, Map<String, Long>> data = result.get(plugin);

        if (data == null) {
            data = new HashMap<>();
            result.put(plugin, data);
        }

        Map<String, Long> graphData = data.get(graphName);

        if (graphData == null) {
            graphData = new HashMap<>();
            data.put(graphName, graphData);
        }

        graphData.put(columnName, value);
    }

}