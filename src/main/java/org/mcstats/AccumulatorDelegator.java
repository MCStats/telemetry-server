package org.mcstats;

import org.mcstats.decoder.DecodedRequest;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.ServerPlugin;
import org.mcstats.util.Tuple;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A registry of accumulators that are available. Allows accumulating as well.
 */
public class AccumulatorDelegator {

    /**
     * All available accumulators
     */
    private final Set<Accumulator> accumulators = new HashSet<>();

    /**
     * The plugin used for All Servers
     */
    private final Plugin globalPlugin;

    public AccumulatorDelegator(MCStats mcstats) {
        globalPlugin = mcstats.loadPlugin("All Servers");
    }

    public List<Tuple<Column, Long>> accumulate(DecodedRequest request, ServerPlugin serverPlugin) {
        List<Tuple<Column, Long>> result = new ArrayList<>();

        for (Accumulator accumulator : accumulators) {
            AccumulatorContext context = new AccumulatorContext(request, serverPlugin);

            // TODO return the list via abstract class instead maybe?
            accumulator.accumulate(context);

            context.getResult().forEach((graphName, data) -> data.forEach((columnName, value) -> {
                Graph graph = new Graph(serverPlugin.getPlugin(), graphName);
                Column column = new Column(graph, columnName);

                result.add(new Tuple<>(column, value));

                if (accumulator.isGlobal()) {
                    Graph globalGraph = new Graph(globalPlugin, graphName);
                    Column globalColumn = new Column(globalGraph, columnName);

                    result.add(new Tuple<>(globalColumn, value));
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

}
