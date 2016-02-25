package org.mcstats.generator.aggregator;

import org.apache.commons.lang3.Validate;
import org.mcstats.generator.Aggregator;
import org.mcstats.generator.DataContainer;

public class IncrementAggregator<T> implements Aggregator<T> {

    private final String graphName;
    private final String columnName;

    public IncrementAggregator(String graphName, String columnName) {
        Validate.notNull(graphName);
        Validate.notNull(columnName);

        this.graphName = graphName;
        this.columnName = columnName;
    }


    @Override
    public void aggregate(DataContainer container, T instance) {
        container.add(graphName, columnName, 1);
    }

}
