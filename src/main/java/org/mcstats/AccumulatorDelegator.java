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

    private final MCStats mcstats;

    /**
     * All available accumulators
     */
    private final Set<Accumulator> accumulators = new HashSet<>();

    /**
     * The plugin used for All Servers
     */
    private final Plugin globalPlugin;

    public AccumulatorDelegator(MCStats mcstats) {
        this.mcstats = mcstats;
        globalPlugin = mcstats.loadPlugin("All Servers");
    }

    public List<Tuple<Column, Long>> accumulate(DecodedRequest request, ServerPlugin serverPlugin) {
        List<Tuple<Column, Long>> result = new ArrayList<>();

        for (Accumulator accumulator : accumulators) {
            AccumulatorContext context = new AccumulatorContext(request, serverPlugin);

            // TODO return the list via abstract class instead maybe?
            accumulator.accumulate(context);

            List<Tuple<Column, Long>> accumulated = context.getResult();

            result.addAll(accumulated);

            // if global is allowed: copy them
            if (accumulator.isGlobal()) {
                accumulated.forEach(t -> {
                    // NewColumn globalColumn =
                    Graph globalGraph = new Graph(globalPlugin, t.first().getGraph().getName());
                    Column globalColumn = new Column(globalGraph, t.first().getName());

                    result.add(new Tuple<>(globalColumn, t.second()));
                });
            }
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
