package org.mcstats.db;

import org.mcstats.model.Column;

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
    public void insert(Column column, int epoch, int sum, int count, int avg, int max, int min);

}
