package org.mcstats.generator.aggregator;

import org.mcstats.generator.Aggregator;
import org.mcstats.generator.DataContainer;

import java.lang.reflect.Field;

public class ReflectionAggregator<T> implements Aggregator<T> {

    /**
     * The name of the graph to use
     */
    protected String graphName;

    /**
     * The name of the column to use. If null, then the field is the column's name
     */
    protected String columnName;

    /**
     * The method we are reflecting into
     */
    protected Field field;

    /**
     * Create a new reflection aggregator that will use the value returned by
     * the given field as the name of the column and the value to be summed
     * will be a constant 1.
     * All of the data will be inserted into the graph with name graphName
     *
     * @param graphName
     * @param fieldName
     */
    public ReflectionAggregator(Class<T> clazz, String fieldName, String graphName) {
        this(clazz, fieldName, graphName, null);
    }

    /**
     * Create a new reflection aggregator that will use the value returned
     * by the given field as the value of the data and the given columnName
     * as the name of the column.
     * All of the data will be inserted into the graph with name graphName
     *
     * @param fieldName
     * @param graphName
     * @param columnName
     */
    public ReflectionAggregator(Class<T> clazz, String fieldName, String graphName, String columnName) {
        try {
            this.field = clazz.getDeclaredField(fieldName);
            this.field.setAccessible(true);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }

        this.graphName = graphName;
        this.columnName = columnName;

        if (graphName == null) {
            throw new UnsupportedOperationException("graphName cannot be null");
        }
    }

    @Override
    public String toString() {
        return String.format("ReflectionAggregator(fieldName = %s, graphName = %s, columnName = %s)", field.getName(), graphName, columnName);
    }

    /**
     * Get the column name
     *
     * @return
     */
    public String getColumnName(T instance) {
        return columnName;
    }

    /**
     * Get the column's value
     *
     * @param fieldValue
     * @param columnName generally equal to getColumnName
     * @return
     */
    public long getColumnValue(Object fieldValue, String columnName) {
        long columnValue = 1;

        // attempt to parse it as a string
        if (columnName != null) {
            try {
                columnValue = Long.parseLong(fieldValue.toString());
            } catch (Exception e) {
                columnValue = 1;
            }
        }

        return columnValue;
    }

    @Override
    public void aggregate(DataContainer container, T instance) {
        if (field == null) {
            return;
        }

        try {
            Object rawValue = field.get(instance);
            String columnName = getColumnName(instance);
            long value;

            if (columnName == null) {
                columnName = rawValue.toString();
                value = 1;
            } else {
                value = getColumnValue(rawValue, columnName);
            }

            if (columnName.isEmpty()) {
                return;
            }

            container.add(graphName, columnName, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
