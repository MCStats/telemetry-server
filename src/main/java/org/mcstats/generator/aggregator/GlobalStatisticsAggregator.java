package org.mcstats.generator.aggregator;

import org.mcstats.MCStats;
import org.mcstats.generator.GeneratedData;
import org.mcstats.generator.GraphGenerator;
import org.mcstats.model.Column;

import java.util.HashMap;
import java.util.Map;

public class GlobalStatisticsAggregator implements GraphGenerator {

    /**
     * Generator for the Servers column
     */
    private GraphGenerator servers = new IncrementAggregator("Global Statistics", "Servers");

    /**
     * Generator for the Players column
     */
    private GraphGenerator players = new ReflectionAggregator("players", "Global Statistics", "Players");

    /**
     * {@inheritDoc}
     */
    public Map<Column, GeneratedData> generate(MCStats mcstats) {
        Map<Column, GeneratedData> res = new HashMap<Column, GeneratedData>();

        res.putAll(servers.generate(mcstats));
        res.putAll(players.generate(mcstats));

        return res;
    }

}
