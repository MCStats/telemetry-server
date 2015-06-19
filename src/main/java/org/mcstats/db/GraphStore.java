package org.mcstats.db;

import org.mcstats.generator.GeneratedData;
import org.mcstats.model.Plugin;

import java.util.List;
import java.util.Map;

public interface GraphStore {

    /**
     * Batches inserts the data for graphs into the graph store
     *
     * @param epoch
     */
    void insert(Map<Plugin, Map<Integer, List<GeneratedData>>> data, int epoch);

}
