package org.mcstats.generator;

import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds data for graph generation.
 */
public class DataContainer {

    private final Map<String, Map<String, Datum>> data = new ConcurrentHashMap<>();

    /**
     * Adds graph data to the container
     *
     * @param graphName
     * @param columnName
     * @param value
     */
    public void add(String graphName, String columnName, long value) {
        Map<String, Datum> columns = data.get(graphName);

        if (columns == null) {
            columns = new HashMap<>();
            data.put(graphName, columns);
        }

        Datum datum = columns.get(columnName);

        if (datum == null) {
            datum = new Datum();
            columns.put(columnName, datum);
        }

        datum.incrementCount();
        datum.incrementSum(value);
    }

    /**
     * Clears the container
     */
    public void clear() {
        data.clear();
    }

    /**
     * Returns an immutable snapshot of the current data.
     *
     * @return
     */
    public ImmutableMap<String, Map<String, Datum>> getData() {
        return ImmutableMap.copyOf(data);
    }

}
