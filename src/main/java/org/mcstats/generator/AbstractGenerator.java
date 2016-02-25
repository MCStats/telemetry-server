package org.mcstats.generator;

import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractGenerator<T> implements Generator<T> {

    /**
     * The list of aggregators to use on plugins
     */
    private final List<Aggregator<T>> aggregators = new ArrayList<>();

    /**
     * Add an aggregator to the generator.
     *
     * @param aggregator
     */
    public void addAggregator(Aggregator<T> aggregator) {
        aggregators.add(aggregator);
    }

    /**
     * Gets all instances of T. Used by generateAll.
     *
     * @return
     */
    public abstract List<T> getAllInstances();

    @Override
    public ImmutableMap<String, Map<String, Datum>> generateAll() {
        DataContainer container = new DataContainer();

        for (T instance : getAllInstances()) {
            for (Aggregator<T> aggregator : aggregators) {
                aggregator.aggregate(container, instance);
            }
        }

        return container.getData();
    }

    @Override
    public ImmutableMap<String, Map<String, Datum>> generatorFor(T instance) {
        DataContainer container = new DataContainer();

        for (Aggregator<T> aggregator : aggregators) {
            aggregator.aggregate(container, instance);
        }

        return container.getData();
    }

}
