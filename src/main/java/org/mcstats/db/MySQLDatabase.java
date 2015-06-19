package org.mcstats.db;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.log4j.Logger;
import org.mcstats.DatabaseQueue;
import org.mcstats.model.Plugin;
import org.mcstats.model.PluginGraph;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class MySQLDatabase implements Database {

    private Logger logger = Logger.getLogger("Database");

    public static long QUERIES = 0;

    /**
     * The model cache
     */
    private final ModelCache modelCache;

    /**
     * Database queue for the database
     */
    private final DatabaseQueue databaseQueue;

    /**
     * The dataSource.getConnectionion() data source
     */
    private BasicDataSource ds;

    @Inject
    public MySQLDatabase(ModelCache modelCache,
                         DatabaseQueue databaseQueue,
                         @Named("mysql.hostname") String hostname,
                         @Named("mysql.database") String database,
                         @Named("mysql.username") String username,
                         @Named("mysql.password") String password) {
        if (hostname == null || database == null || username == null || password == null) {
            throw new IllegalArgumentException("All arguments must not be null");
        }

        this.modelCache = modelCache;
        this.databaseQueue = databaseQueue;

        ds = new BasicDataSource();
        ds.setDriverClassName("com.mysql.jdbc.Driver");
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setUrl("jdbc:mysql://" + hostname + "/" + database);
        ds.setInitialSize(4);
        ds.setMaxTotal(64);
        ds.setTestOnBorrow(true);
        ds.setValidationQuery("SELECT 1");

        ds.addConnectionProperty("zeroDateTimeBehavior", "convertToNull");
    }

    @Override
    public void saveLater(Savable savable) {
        databaseQueue.offer(savable);
    }

    public Plugin createPlugin(String name) {
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO plugins (name, type, created_at) VALUES (?, 'plugin', NOW())")) {
            statement.setString(1, name);
            statement.executeUpdate();
            QUERIES++;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return loadPlugin(name);
    }

    public List<Plugin> loadPlugins() {
        List<Plugin> plugins = new ArrayList<>();

        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM plugins")) { // TODO where parent = -1
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    plugins.add(resolvePlugin(set));
                }
            }

            return plugins;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return plugins;
    }

    public Plugin loadPlugin(int id) {
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM plugins WHERE id = ?")) {
            statement.setInt(1, id);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return resolvePlugin(set);
                }
            }

            return null;
        } catch (SQLException e) {
            return null;
        }
    }

    public Plugin loadPlugin(String name) {
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM plugins WHERE name = ?")) {
            statement.setString(1, name);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return resolvePlugin(set);
                }
            }

            return null;
        } catch (SQLException e) {
            return null;
        }
    }

    public void savePlugin(Plugin plugin) {
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE plugins SET name = ?, type = ?, hidden = ?, rank = ?, last_rank = ?, last_rank_change = ?, created_at = ?, updated_at = ? WHERE ID = ?")) {
            statement.setString(1, plugin.getName());
            statement.setString(2, plugin.getType());
            statement.setBoolean(3, plugin.isHidden());
            statement.setInt(4, plugin.getRank());
            statement.setInt(5, plugin.getLastRank());
            statement.setInt(6, plugin.getLastRankChange());
            statement.setDate(7, new java.sql.Date(plugin.getCreatedAt().getTime()));
            statement.setDate(8, new java.sql.Date(plugin.getUpdatedAt().getTime()));
            statement.setInt(9, plugin.getId());

            statement.executeUpdate();
            QUERIES++;

            modelCache.cachePlugin(plugin);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public PluginGraph createGraph(Plugin plugin, String name) {
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO plugin_graphs (plugin_id, type, active, name, display_name, scale, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())")) {
            statement.setInt(1, plugin.getId());
            statement.setInt(2, 0); // line
            statement.setInt(3, 0); // active
            statement.setString(4, name);
            statement.setString(5, name);
            statement.setString(6, "linear");
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return loadGraph(plugin, name);
    }

    public PluginGraph loadGraph(Plugin plugin, String name) {
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM plugin_graphs WHERE plugin_id = ? AND name = ?")) {
            statement.setInt(1, plugin.getId());
            statement.setString(2, name);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return resolveGraph(plugin, set);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public List<PluginGraph> loadGraphs(Plugin plugin) {
        List<PluginGraph> graphs = new ArrayList<>();
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM plugin_graphs WHERE plugin_id = ?")) {
            statement.setInt(1, plugin.getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    PluginGraph graph = resolveGraph(plugin, set);
                    graphs.add(graph);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return graphs;
    }

    /**
     * Resolve a graph from a ResultSet. Does not close the result set.
     *
     * @param set
     * @return
     * @throws SQLException
     */
    private PluginGraph resolveGraph(Plugin plugin, ResultSet set) throws SQLException {
        PluginGraph graph = new PluginGraph(plugin, set.getString("name"));

        int id = set.getInt("id");

        graph.initFromDatabase(id);
        return graph;
    }

    /**
     * Resolve a plugin from a ResultSet. Does not close the result set.
     *
     * @param set
     * @return
     */
    private Plugin resolvePlugin(ResultSet set) throws SQLException {
        Plugin plugin = new Plugin(this, modelCache);
        plugin.setId(set.getInt("id"));
        plugin.setName(set.getString("name"));
        plugin.setType(set.getString("type"));
        plugin.setHidden(set.getBoolean("hidden"));
        plugin.setRank(set.getInt("rank"));
        plugin.setLastRank(set.getInt("last_rank"));
        plugin.setLastRankChange(set.getInt("last_rank_change"));
        plugin.setCreatedAt(set.getDate("created_at"));
        plugin.setUpdatedAt(set.getDate("updated_at"));
        plugin.setModified(false);
        return plugin;
    }

}
