package org.mcstats.db;

import org.mcstats.generator.GeneratedData;
import org.mcstats.model.Plugin;
import org.mcstats.util.Tuple;

import java.util.List;

public interface GraphStore {

    /**
     * Insert data into the graph store
     *
     * @param data
     */
    void batchInsert(Plugin plugin, String graphName, List<Tuple<String, GeneratedData>> data, int epoch);

}
