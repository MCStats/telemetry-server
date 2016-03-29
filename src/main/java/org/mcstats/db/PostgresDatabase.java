package org.mcstats.db;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.log4j.Logger;
import org.json.simple.JSONValue;
import org.mcstats.MCStats;
import org.mcstats.generator.Datum;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.postgresql.util.PGobject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PostgresDatabase implements Database, GraphStore {

    private Logger logger = Logger.getLogger(PostgresDatabase.class);

    public static long QUERIES = 0;

    /**
     * The mcstats object
     */
    private final MCStats mcstats;

    /**
     * The dataSource.getConnectionion() data source
     */
    private BasicDataSource ds;

    public PostgresDatabase(MCStats mcstats, String hostname, String databaseName, String username, String password) {
        if (hostname == null || databaseName == null || username == null || password == null) {
            throw new IllegalArgumentException("All arguments must not be null");
        }

        this.mcstats = mcstats;

        // Create the mysql data dataSource.getConnectionion() pool
        ds = new BasicDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setUrl("jdbc:postgresql://" + hostname + "/" + databaseName);
        ds.setInitialSize(10);
        ds.setMaxTotal(100);
    }

    /**
     * Gets the table name for a plugin's graphdata
     *
     * @param plugin
     * @return
     */
    private String getPluginGraphDataTableName(Plugin plugin) {
        return "plugin_graphdata_" + plugin.getId();
    }

    public Plugin createPlugin(String name) {
        try (Connection connection = ds.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO plugins (name, type, hidden, rank, last_rank, last_rank_change, created_at, updated_at) VALUES (?, 'plugin', 0, -1, -1, -1, NOW(), NOW())")) {
                statement.setString(1, name);
                statement.executeUpdate();
                QUERIES++;
            }

            Plugin plugin = loadPlugin(name);

            if (plugin != null) {
                String tableName = getPluginGraphDataTableName(plugin);

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + tableName + " ( LIKE graphdata_template INCLUDING ALL )");
                    QUERIES++;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // re-load the plugin
        return loadPlugin(name);
    }

    public List<Plugin> loadPlugins() {
        List<Plugin> plugins = new ArrayList<>();

        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM plugins")) {
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
             PreparedStatement statement = connection.prepareStatement("UPDATE plugins SET name = ?, hidden = ?, rank = ?, last_rank = ?, last_rank_change = ?, created_at = to_timestamp(?), updated_at = to_timestamp(?), active_server_count = ?, active_player_count = ? WHERE id = ?")) {
            statement.setString(1, plugin.getName());
            statement.setInt(2, plugin.isHidden() ? 1 : 0);
            statement.setInt(3, plugin.getRank());
            statement.setInt(4, plugin.getLastRank());
            statement.setInt(5, plugin.getLastRankChange());
            statement.setInt(6, plugin.getCreated());
            statement.setInt(7, plugin.getLastUpdated());
            statement.setInt(8, plugin.getActiveServerCount());
            statement.setInt(9, plugin.getActivePlayerCount());
            statement.setInt(10, plugin.getId());

            statement.executeUpdate();
            QUERIES++;
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Graph createGraph(Plugin plugin, String name) {
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO plugin_graphs (plugin_id, name, display_name, type, active, editable, position, scale, halfwidth) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, plugin.getId());
            statement.setString(2, name);
            statement.setString(3, name);
            statement.setInt(4, 0); // line
            statement.setInt(5, 0); // active
            statement.setInt(6, 1);
            statement.setInt(7, 1);
            statement.setString(8, "linear");
            statement.setInt(9, 0);

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return loadGraph(plugin, name);
    }

    public Graph loadGraph(Plugin plugin, String name) {
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

    public List<Graph> loadGraphs(Plugin plugin) {
        List<Graph> graphs = new ArrayList<>();
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM plugin_graphs WHERE plugin_id = ?")) {
            statement.setInt(1, plugin.getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Graph graph = resolveGraph(plugin, set);
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
    private Graph resolveGraph(Plugin plugin, ResultSet set) throws SQLException {
        Graph graph = new Graph(plugin);
        graph.setId(set.getInt("id"));
        graph.setName(set.getString("name"));
        graph.setDisplayName(set.getString("display_name"));
        graph.setType(set.getInt("type"));
        graph.setPosition(set.getInt("position"));
        graph.setActive(set.getInt("active"));
        graph.setScale(set.getString("scale"));
        return graph;
    }

    /**
     * Resolve a plugin from a ResultSet. Does not close the result set.
     *
     * @param set
     * @return
     */
    private Plugin resolvePlugin(ResultSet set) throws SQLException {
        Plugin plugin = new Plugin(mcstats);
        plugin.setId(set.getInt("id"));
        plugin.setName(set.getString("name"));
        // plugin.setAuthors(set.getString("author"));
        plugin.setAuthors(""); // TODO authors
        plugin.setHidden(set.getInt("hidden") == 1);
        plugin.setRank(set.getInt("rank"));
        plugin.setLastRank(set.getInt("last_rank"));
        plugin.setLastRankChange(set.getInt("last_rank_change"));
        plugin.setCreated((int) (set.getDate("created_at").getTime() / 1000L));
        plugin.setLastUpdated((int) (set.getDate("updated_at").getTime() / 1000L));
        plugin.setActiveServerCount(set.getInt("active_server_count"));
        plugin.setActivePlayerCount(set.getInt("active_player_count"));
        plugin.setModified(false);
        return plugin;
    }

    @Override
    public void insertGlobalPluginData(String graphName, Map<String, Datum> data, int epoch) {
        insertGraphData("plugin_graphdata_global", graphName, data, epoch);
    }

    @Override
    public void insertPluginData(Plugin plugin, String graphName, Map<String, Datum> data, int epoch) {
        insertGraphData(getPluginGraphDataTableName(plugin), graphName, data, epoch);
    }

    /**
     * Inserts graph data
     *
     * @param tableName
     * @param graphName
     * @param data
     * @param epoch
     */
    private void insertGraphData(String tableName, String graphName, Map<String, Datum> data, int epoch) {
        List<Map<String, Object>> dataJson = new ArrayList<>();

        data.forEach((name, datum) -> {
            dataJson.add(ImmutableMap.of("name", name, "sum", datum.getSum(), "count", datum.getCount()));
        });

        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + tableName + " (time, graph, data) VALUES (to_timestamp(?), ?, ?)")) {
            statement.setInt(1, epoch);
            statement.setString(2, graphName);

            PGobject jsonObject = new PGobject();
            jsonObject.setType("json");
            jsonObject.setValue(JSONValue.toJSONString(dataJson));

            statement.setObject(3, jsonObject);

            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
