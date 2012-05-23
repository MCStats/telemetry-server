package org.mcstats.sql;

public interface Savable {

    /**
     * Queue the entity to be saved to the database
     */
    public void save();

    /**
     * Flush the entity to the database immediately
     */
    public void saveNow();

}
