package org.mcstats.generator;

import org.mcstats.MCStats;
import org.mcstats.model.Column;

import java.util.Map;

public interface GraphGenerator {

    /**
     * Generate graph data
     *
     * @param mcstats
     * @return the generated data
     */
    public Map<Column, Long> generate(MCStats mcstats);

}
