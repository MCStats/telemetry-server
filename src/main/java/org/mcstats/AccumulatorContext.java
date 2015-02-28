package org.mcstats;

import org.mcstats.decoder.DecodedRequest;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.ServerPlugin;
import org.mcstats.util.Tuple;

import java.util.ArrayList;
import java.util.List;

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
    private final List<Tuple<Column, Long>> result = new ArrayList<>();

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
        Graph graph = new Graph(serverPlugin.getPlugin(), graphName);
        Column column = new Column(graph, columnName);

        result.add(new Tuple<>(column, value));
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

    public List<Tuple<Column, Long>> getResult() {
        return result;
    }

}
