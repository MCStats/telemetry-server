package org.mcstats.generator.aggregator;

import org.apache.commons.lang3.Validate;
import org.mcstats.generator.Aggregator;
import org.mcstats.generator.DataContainer;

import java.lang.reflect.Field;

public class ReflectionDonutAggregator<T> implements Aggregator<T> {

    /**
     * The name of the graph to use
     */
    private final String graphName;

    /**
     * The name of the column to use on the inner donut. If null, then the field is the column's name
     */
    private final String innerColumnName;

    /**
     * The name of the column to use on the outer donut. If null, then the field is the column's name
     */
    private final String outerColumnName;

    /**
     * The inner donut's field
     */
    private final Field innerField;

    /**
     * The outer donut's field
     */
    private final Field outerField;

    public ReflectionDonutAggregator(Class<T> clazz, String graphName, String innerFieldName, String outerFieldName) {
        this(clazz, graphName, innerFieldName, outerFieldName, null, null);
    }

    public ReflectionDonutAggregator(Class<T> clazz, String graphName, String innerFieldName, String outerFieldName, String innerColumnName, String outerColumnName) {
        Validate.notNull(graphName);

        try {
            this.innerField = clazz.getDeclaredField(innerFieldName);
            this.outerField = clazz.getDeclaredField(outerFieldName);
            this.innerField.setAccessible(true);
            this.outerField.setAccessible(true);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }

        this.graphName = graphName;
        this.innerColumnName = innerColumnName;
        this.outerColumnName = outerColumnName;
    }

    @Override
    public void aggregate(DataContainer container, T instance) {
        if (innerField == null || outerField == null) {
            return;
        }

        try {
            Object innerValue = innerField.get(instance);
            Object outerValue = outerField.get(instance);

            String innerColumnName = this.innerColumnName;
            String outerColumnName = this.outerColumnName;

            long value = 1;

            // attempt to parse it as a string
            if (innerColumnName == null) {
                innerColumnName = (String) innerValue;
            }

            if (outerColumnName == null) {
                outerColumnName = (String) outerValue;
            }

            if (innerColumnName.isEmpty()) {
                return;
            }

            // load the graph for the plugin
            container.add(graphName, innerColumnName + "~=~" + outerColumnName, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
