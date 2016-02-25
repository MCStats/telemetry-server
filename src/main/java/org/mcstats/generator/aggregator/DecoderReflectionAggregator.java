package org.mcstats.generator.aggregator;

import java.util.function.Function;

public class DecoderReflectionAggregator<T, V> extends ReflectionAggregator<T> {

    /**
     * The function used to get column names
     */
    private final Function<V, String> columnNameToValueFunction;

    public DecoderReflectionAggregator(Class<T> clazz, String graphName, String columnNameFieldName, Function<V, String> columnNameToValueFunction) {
        super(clazz, graphName, columnNameFieldName);
        this.columnNameToValueFunction = columnNameToValueFunction;
    }

    @SuppressWarnings("unchecked")
    @Override
    public String getColumnName(T instance) {
        try {
            V value = (V) field.get(instance);

            return columnNameToValueFunction.apply(value);
        } catch (IllegalAccessException e) {
            return "Unknown";
        }
    }

    @Override
    public long getColumnValue(Object fieldValue, String columnName) {
        return 1;
    }

}
