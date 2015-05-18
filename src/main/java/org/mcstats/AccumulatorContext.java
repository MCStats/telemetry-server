package org.mcstats;

import org.mcstats.decoder.DecodedRequest;
import org.mcstats.model.ServerPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Context given to accumulators
 */
public class AccumulatorContext {

    /**
     * The request
     */
    private DecodedRequest request;

    /**
     * The plugin for the request
     */
    private ServerPlugin serverPlugin;

    /**
     * The accumulated result
     */
    private final Map<String, Map<String, Long>> result = new HashMap<>();

    public AccumulatorContext(DecodedRequest request, ServerPlugin serverPlugin) {
        this.request = request;
        this.serverPlugin = serverPlugin;
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

    public ServerPlugin getServerPlugin() {
        return serverPlugin;
    }

    public Map<String, Map<String, Long>> getResult() {
        return Collections.unmodifiableMap(result);
    }

}
