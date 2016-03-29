package org.mcstats.db;

public interface Savable {

    /**
     * Queue the entity to be saved to the database
     */
    void save();

    /**
     * Flush the entity to the database immediately
     */
    void saveNow();

}
