package org.mcstats.db;

import org.mcstats.generator.GeneratedData;
import org.mcstats.model.PluginGraphColumn;
import org.mcstats.model.PluginGraph;
import org.mcstats.util.Tuple;

import java.util.List;
import java.util.Map;

public interface GraphStore {

    /**
     * Insert data into the graph store
     *
     * @param data
     */
    void insert(PluginGraph graph, List<Tuple<PluginGraphColumn, GeneratedData>> data, int epoch);

    /**
     * Batches inserts the data for graphs into the graph store
     *
     * @param epoch
     */
    void insert(Map<PluginGraph, List<Tuple<PluginGraphColumn, GeneratedData>>> data, int epoch);

}
