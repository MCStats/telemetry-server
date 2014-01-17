package org.mcstats.generator.aggregator;

import org.mcstats.MCStats;
import org.mcstats.generator.GeneratedData;
import org.mcstats.generator.GraphGenerator;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;

import java.util.HashMap;
import java.util.Map;

public class RankAggregator implements GraphGenerator {

    private Map<Column, GeneratedData> aggregate(MCStats mcstats, Plugin plugin) {
        Map<Column, GeneratedData> data = new HashMap<Column, GeneratedData>();

        if (plugin.getId() == -1 || plugin.getParent() != -1) {
            return data;
        }

        Graph graph = mcstats.loadGraph(plugin, "Rank");
        Column column = graph.loadColumn("Rank");

        long columnValue = (long) plugin.getRank();

        GeneratedData current = data.get(column);

        if (current == null) {
            current = new GeneratedData();
            current.setCount(1);
            current.setMax((int) columnValue);
            current.setMin((int) columnValue);
            current.setSum((int) columnValue);
            data.put(column, current);
            return data;
        }

        current.incrementCount();
        current.incrementSum((int) columnValue);

        return data;
    }

    /**
     * {@inheritDoc}
     */
    public Map<Column, GeneratedData> generate(MCStats mcstats) {
        Map<Column, GeneratedData> data = new HashMap<Column, GeneratedData>();

        for (Plugin plugin : mcstats.getCachedPlugins()) {
            data.putAll(aggregate(mcstats, plugin));
        }

        return data;
    }

}
