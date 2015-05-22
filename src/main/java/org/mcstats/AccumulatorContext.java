package org.mcstats;

import org.mcstats.decoder.DecodedRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Context given to accumulators
 */
public class AccumulatorContext {

    /**
     * The request
     */
    private final DecodedRequest request;

    /**
     * Set of version changes
     */
    private final Set<String> versionChanges;

    /**
     * The accumulated result
     */
    private final Map<String, Map<String, Long>> result = new HashMap<>();

    public AccumulatorContext(DecodedRequest request, Set<String> versionChanges) {
        this.request = request;
        this.versionChanges = versionChanges;
    }

    /**
     * Accumulates data
     *
     * @param graphName
     * @param columnName
     * @param value
     */
    public void addData(String graphName, String columnName, long value) {
        Map<String, Long> data = result.get(graphName);

        if (data == null) {
            data = new HashMap<>();
            result.put(graphName, data);
        }

        data.put(columnName, value);
    }

    /**
     * Adds data using the donut graph data representation
     *
     * @param graphName
     * @param innerColumnName
     * @param outerColumnName
     * @param value
     */
    public void addDonutData(String graphName, String innerColumnName, String outerColumnName, long value) {
        addData(graphName, String.format("%s~=~%s", innerColumnName, outerColumnName), value);
    }

    public DecodedRequest getRequest() {
        return request;
    }

    public Set<String> getVersionChanges() {
        return versionChanges;
    }

    public Map<String, Map<String, Long>> getResult() {
        return Collections.unmodifiableMap(result);
    }

}
