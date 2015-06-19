package org.mcstats.db;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.log4j.Logger;
import org.mcstats.DatabaseQueue;
import org.mcstats.model.PluginGraphColumn;
import org.mcstats.model.PluginGraph;
import org.mcstats.model.Plugin;

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
    }

    @Override
    public void saveLater(Savable savable) {
        databaseQueue.offer(savable);
    }

    public Plugin createPlugin(String name) {
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO Plugin (Name, Author, Hidden, GlobalHits, Created) VALUES (?, '', 0, 0, UNIX_TIMESTAMP())")) {
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
             PreparedStatement statement = connection.prepareStatement("SELECT ID, Parent, Name, Author, Hidden, GlobalHits, Rank, LastRank, LastRankChange, Created, LastUpdated, ServerCount30 FROM Plugin WHERE Parent = -1")) {
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
             PreparedStatement statement = connection.prepareStatement("SELECT ID, Parent, Name, Author, Hidden, GlobalHits, Rank, LastRank, LastRankChange, Created, LastUpdated, ServerCount30 FROM Plugin WHERE ID = ?")) {
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
             PreparedStatement statement = connection.prepareStatement("SELECT ID, Parent, Name, Author, Hidden, GlobalHits, Rank, LastRank, LastRankChange, Created, LastUpdated, ServerCount30 FROM Plugin WHERE Name = ?")) {
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
             PreparedStatement statement = connection.prepareStatement("UPDATE Plugin SET Name = ?, Hidden = ?, GlobalHits = ?, Rank = ?, LastRank = ?, LastRankChange = ?, Created = ?, LastUpdated = ?, ServerCount30 = ? WHERE ID = ?")) {
            statement.setString(1, plugin.getName());
            statement.setInt(2, plugin.getHidden());
            statement.setInt(3, plugin.getGlobalHits());
            statement.setInt(4, plugin.getRank());
            statement.setInt(5, plugin.getLastRank());
            statement.setInt(6, plugin.getLastRankChange());
            statement.setInt(7, plugin.getCreated());
            statement.setInt(8, plugin.getLastUpdated());
            statement.setInt(9, plugin.getServerCount30());
            statement.setInt(10, plugin.getId());

            statement.executeUpdate();
            QUERIES++;

            modelCache.cachePlugin(plugin);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public PluginGraph createGraph(Plugin plugin, String name) {
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO Graph (Plugin, Type, Active, Name, DisplayName, Scale) VALUES (?, ?, ?, ?, ?, ?)")) {
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
             PreparedStatement statement = connection.prepareStatement("SELECT ID, Type, Position, Active, Name, DisplayName, Scale FROM Graph WHERE Plugin = ? AND Name = ?")) {
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
             PreparedStatement statement = connection.prepareStatement("SELECT ID, Type, Position, Active, Name, DisplayName, Scale FROM Graph WHERE Plugin = ?")) {
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

    public PluginGraphColumn createColumn(PluginGraph graph, String name) {
        if (name.length() > 100) {
            return null;
        }

        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO CustomColumn (Plugin, Graph, Name) VALUES (?, ?, ?)")) {
            statement.setInt(1, graph.getPlugin().getId());
            statement.setInt(2, graph.getId());
            statement.setString(3, name);
            statement.executeUpdate();
            QUERIES++;
        } catch (SQLException e) {
            logger.info("Failed to create column " + name + " for graph: " + graph.getId());
        }

        return loadColumn(graph, name);
    }

    public PluginGraphColumn loadColumn(PluginGraph graph, String name) {
        if (name.length() > 100) {
            return null;
        }

        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT ID, Name FROM CustomColumn WHERE Graph = ? AND Name = ?")) {
            statement.setInt(1, graph.getId());
            statement.setString(2, name);

            try (ResultSet set = statement.executeQuery()) {
                QUERIES++;

                if (set.next()) {
                    return resolveColumn(graph.getPlugin(), graph, set);
                }
            }
        } catch (SQLException e) {
            logger.info("Failed to load column " + name + " for graph: " + graph.getId());
        }

        return null;
    }

    public List<PluginGraphColumn> loadColumns(PluginGraph graph) {
        List<PluginGraphColumn> columns = new ArrayList<>();
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT ID, Name FROM CustomColumn WHERE Graph = ?")) {
            statement.setInt(1, graph.getId());

            try (ResultSet set = statement.executeQuery()) {
                QUERIES++;

                while (set.next()) {
                    PluginGraphColumn column = resolveColumn(graph.getPlugin(), graph, set);
                    columns.add(column);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return columns;
    }

    /**
     * Resolve a graph from a ResultSet. Does not close the result set.
     *
     * @param set
     * @return
     * @throws SQLException
     */
    private PluginGraph resolveGraph(Plugin plugin, ResultSet set) throws SQLException {
        PluginGraph graph = new PluginGraph(plugin, set.getString("Name"));

        int id = set.getInt("ID");

        graph.initFromDatabase(id);
        return graph;
    }

    /**
     * Resolve a column from a REsultSet. Does not close the result set.
     *
     * @param plugin
     * @param graph
     * @param set
     * @return
     * @throws SQLException
     */
    private PluginGraphColumn resolveColumn(Plugin plugin, PluginGraph graph, ResultSet set) throws SQLException {
        PluginGraphColumn column = new PluginGraphColumn(graph, set.getString("Name"));

        int id = set.getInt("ID");

        column.initFromDatabase(id);
        return column;
    }

    /**
     * Resolve a plugin from a ResultSet. Does not close the result set.
     *
     * @param set
     * @return
     */
    private Plugin resolvePlugin(ResultSet set) throws SQLException {
        Plugin plugin = new Plugin(this, modelCache);
        plugin.setId(set.getInt("ID"));
        plugin.setParent(set.getInt("Parent"));
        plugin.setName(set.getString("Name"));
        plugin.setAuthors(set.getString("Author"));
        plugin.setHidden(set.getInt("Hidden"));
        plugin.setGlobalHits(set.getInt("GlobalHits"));
        plugin.setRank(set.getInt("Rank"));
        plugin.setLastRank(set.getInt("LastRank"));
        plugin.setLastRankChange(set.getInt("LastRankChange"));
        plugin.setCreated(set.getInt("Created"));
        plugin.setLastUpdated(set.getInt("LastUpdated"));
        plugin.setServerCount30(set.getInt("ServerCount30"));
        plugin.setModified(false);
        return plugin;
    }

}
