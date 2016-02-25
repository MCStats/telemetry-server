package org.mcstats.generator;

public interface Aggregator<T> {

    /**
     * Aggregates values into the given container
     *
     * @param container
     */
    void aggregate(DataContainer container, T instance);

}
