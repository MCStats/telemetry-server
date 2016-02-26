package org.mcstats.db;

import org.mcstats.generator.Datum;
import org.mcstats.model.Plugin;

import java.util.Map;

public interface GraphStore {

    /**
     * Insert data into the graph store
     *
     * @param data
     */
    void insertPluginData(int epoch, Plugin plugin, String graphName, Map<String, Datum> data);

}
