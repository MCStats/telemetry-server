package org.mcstats.db;

import org.mcstats.generator.GeneratedData;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.util.Tuple;

import java.util.List;

public interface GraphStore {

    /**
     * Insert data into the graph store
     *
     * @param column
     * @param epoch
     * @param sum
     * @param count
     * @param avg
     * @param max
     * @param min
     */
    void insert(Column column, int epoch, int sum, int count, int avg, int max, int min);

    /**
     * Insert data into the graph store
     *
     * @param data
     */
    void batchInsert(Graph graph, List<Tuple<Column, GeneratedData>> data, int epoch);

}
