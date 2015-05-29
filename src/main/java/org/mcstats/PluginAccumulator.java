package org.mcstats;

import org.mcstats.accumulator.CustomDataAccumulator;
import org.mcstats.accumulator.MCStatsInfoAccumulator;
import org.mcstats.accumulator.ServerInfoAccumulator;
import org.mcstats.accumulator.VersionInfoAccumulator;
import org.mcstats.decoder.DecodedRequest;
import org.mcstats.model.Plugin;
import org.mcstats.util.ServerBuildIdentifier;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A registry of accumulators that are available. Allows accumulating as well.
 */
@Singleton
public class PluginAccumulator {

    /**
     * ID of the global plugin (for global stats)
     */
    public static final int GLOBAL_PLUGIN_ID = -1;

    /**
     * All available accumulators
     */
    private final Set<Accumulator> accumulators = new HashSet<>();

    @Inject
    public PluginAccumulator(MCStats mcstats, ServerBuildIdentifier serverBuildIdentifier) {
        // TODO add these dynamically?
        add(new MCStatsInfoAccumulator());
        add(new ServerInfoAccumulator(mcstats, serverBuildIdentifier));
        add(new VersionInfoAccumulator());
        add(new CustomDataAccumulator());
    }

    /**
     * Accumulates data based on plugin metadata only.
     *
     * @return
     */
    public Map<Integer, Map<String, Map<String, Long>>> accumulateForPlugin(Plugin plugin) {
        Map<Integer, Map<String, Map<String, Long>>> data = new HashMap<>();

        // TODO just manual (for now?) :-)
        insertToAccumulation(data, plugin.getId(), "Rank", "Rank", plugin.getRank());

        return data;
    }

    /**
     * Accumulates data for a server that sent data
     *
     * @param request
     * @param versionChanges
     * @return
     */
    public Map<Integer, Map<String, Map<String, Long>>> accumulateForServer(DecodedRequest request, Set<String> versionChanges) {
        Map<Integer, Map<String, Map<String, Long>>> result = new HashMap<>();

        for (Accumulator accumulator : accumulators) {
            AccumulatorContext context = new AccumulatorContext(request, versionChanges);

            // TODO return the list via abstract class instead maybe?
            accumulator.accumulate(context);

            context.getResult().forEach((graphName, data) -> data.forEach((columnName, value) -> {
                insertToAccumulation(result, request.getPluginId(), graphName, columnName, value);

                if (accumulator.isGlobal()) {
                    insertToAccumulation(result, GLOBAL_PLUGIN_ID, graphName, columnName, value);
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
     * @param pluginId
     * @param graphName
     * @param columnName
     * @param value
     */
    private void insertToAccumulation(Map<Integer, Map<String, Map<String, Long>>> result, int pluginId, String graphName, String columnName, long value) {
        if (graphName == null || columnName == null) {
            return;
        }

        Map<String, Map<String, Long>> data = result.get(pluginId);

        if (data == null) {
            data = new HashMap<>();
            result.put(pluginId, data);
        }

        Map<String, Long> graphData = data.get(graphName);

        if (graphData == null) {
            graphData = new HashMap<>();
            data.put(graphName, graphData);
        }

        graphData.put(columnName, value);
    }

}
