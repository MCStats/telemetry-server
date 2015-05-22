package org.mcstats.processing;

import java.util.HashMap;
import java.util.Map;

/**
 * A class that takes in graph data and merges it. Any root values reached
 * will be summed together.
 *
 * This class is thread-safe.
 */
public class GraphCauldron {

    /**
     * Raw data being merged
     */
    private final Map<String, Map<String, Long>> data = new HashMap<>();

    /**
     * Mixes data into the cauldron of delicious graph data
     *
     * @param newData
     */
    public void mix(Map<String, Map<String, Long>> newData) {
        synchronized (data) {
            newData.forEach((graphName, graphData) -> {
                final Map<String, Long> masterGraphData;

                if (data.containsKey(graphName)) {
                    masterGraphData = data.get(graphName);
                } else {
                    masterGraphData = new HashMap<>();
                    data.put(graphName, masterGraphData);
                }

                graphData.forEach((columnName, value) -> {
                    long newValue = value;

                    if (masterGraphData.containsKey(columnName)) {
                        newValue += masterGraphData.get(columnName);
                    }

                    masterGraphData.put(columnName, newValue);
                });
            });
        }
    }

    /**
     * Gets the data summed up to this current point.
     *
     * @return the data summed up to this current point. The returned map will be a copy so it will not change.
     */
    public Map<String, Map<String, Long>> getData() {
        synchronized (data) {
            return new HashMap<>(data);
        }
    }

}
