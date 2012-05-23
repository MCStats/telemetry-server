package org.mcstats.model;

import org.mcstats.MCStats;
import org.mcstats.sql.Savable;

import java.sql.SQLException;

public final class RawQuery implements Savable {

    /**
     * The MCStats object
     */
    private final MCStats mcstats;

    /**
     * The raw query
     */
    private final String query;

    public RawQuery(MCStats mcstats, String query) {
        this.mcstats = mcstats;
        this.query = query;
    }

    public void save() {
        mcstats.getDatabaseQueue().offer(this);
    }

    public void saveNow() {
        try {
            mcstats.getDatabase().executeUpdate(query);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
