package org.mcstats.generator.aggregator;

import org.mcstats.generator.DataContainer;

public interface BasicAggregator<Target> {

    /**
     * Aggregates values into the given container
     *
     * @param container
     */
    void aggregate(DataContainer container, Target instance);

}
