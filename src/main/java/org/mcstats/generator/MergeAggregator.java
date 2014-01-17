package org.mcstats.generator;

import org.mcstats.MCStats;
import org.mcstats.model.Column;

import java.util.HashMap;
import java.util.Map;

public class MergeAggregator implements GraphGenerator {

    /**
     * The list of generators that will be merged
     */
    private final GraphGenerator[] generators;

    public MergeAggregator(GraphGenerator... generators) {
        this.generators = generators;
    }

    /**
     * {@inheritDoc}
     */
    public Map<Column, GeneratedData> generate(MCStats mcstats) {
        Map<Column, GeneratedData> res = new HashMap<Column, GeneratedData>();

        for (GraphGenerator generator : generators) {
            res.putAll(generator.generate(mcstats));
        }

        return res;
    }

}
