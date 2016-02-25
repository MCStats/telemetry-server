package org.mcstats.generator.aggregator;

import org.mcstats.generator.DataContainer;

public interface GroupedAggregator<Target, GroupBy> {

    /**
     * Aggregates values into the given container
     *
     * @param container
     */
    void aggregate(DataContainer container, Target instance, GroupBy by);

}
