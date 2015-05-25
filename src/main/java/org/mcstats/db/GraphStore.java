package org.mcstats.db;

import org.mcstats.generator.GeneratedData;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.util.Tuple;

import java.util.List;
import java.util.Map;

public interface GraphStore {

    /**
     * Insert data into the graph store
     *
     * @param data
     */
    void insert(Graph graph, List<Tuple<Column, GeneratedData>> data, int epoch);

    /**
     * Batches inserts the data for graphs into the graph store
     *
     * @param epoch
     */
    void insert(Map<Graph, List<Tuple<Column, GeneratedData>>> data, int epoch);

}
