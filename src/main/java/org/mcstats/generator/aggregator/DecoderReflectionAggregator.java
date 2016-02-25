package org.mcstats.generator.aggregator;

import java.util.function.Function;

public class DecoderReflectionAggregator<T, V> extends ReflectionAggregator<T> {

    /**
     * The function used to get column names
     */
    private final Function<V, String> columnNameFunction;

    public DecoderReflectionAggregator(Class<T> clazz, String fieldName, String graphName, Function<V, String> columnNameFunction) {
        super(clazz, fieldName, graphName);
        this.columnNameFunction = columnNameFunction;
    }

    @SuppressWarnings("unchecked")
    @Override
    public String getColumnName(T instance) {
        try {
            V value = (V) field.get(instance);

            return columnNameFunction.apply(value);
        } catch (IllegalAccessException e) {
            return "Unknown";
        }
    }

    @Override
    public long getColumnValue(Object fieldValue, String columnName) {
        return 1;
    }

}
