package org.mcstats.db;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.log4j.Logger;
import org.mcstats.MCStats;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MySQLDatabase implements Database {

    private Logger logger = Logger.getLogger("Database");

    public static long QUERIES = 0;

    /**
     * The mcstats object
     */
    private final MCStats mcstats;

    /**
     * A map of the already prepared statements
     */
    private final Map<String, PreparedStatement> statementCache = new HashMap<>();

    /**
     * The dataSource.getConnectionion() data source
     */
    private BasicDataSource ds;

    public MySQLDatabase(MCStats mcstats, String hostname, String databaseName, String username, String password) {
        if (hostname == null || databaseName == null || username == null || password == null) {
            throw new IllegalArgumentException("All arguments must not be null");
        }

        this.mcstats = mcstats;

        // Create the mysql data dataSource.getConnectionion() pool
        ds = new BasicDataSource();
        ds.setDriverClassName("com.mysql.jdbc.Driver");
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setUrl("jdbc:mysql://" + hostname + "/" + databaseName);
        ds.setInitialSize(16);
        ds.setMaxTotal(64);
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

        // re-load the plugin
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
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Graph createGraph(Plugin plugin, String name) {
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

    public Graph loadGraph(Plugin plugin, String name) {
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

    public List<Graph> loadGraphs(Plugin plugin) {
        List<Graph> graphs = new ArrayList<>();
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT ID, Type, Position, Active, Name, DisplayName, Scale FROM Graph WHERE Plugin = ?")) {
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

    public Column createColumn(Graph graph, String name) {
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

    public Column loadColumn(Graph graph, String name) {
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

    public List<Column> loadColumns(Graph graph) {
        List<Column> columns = new ArrayList<>();
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT ID, Name FROM CustomColumn WHERE Graph = ?")) {
            statement.setInt(1, graph.getId());

            try (ResultSet set = statement.executeQuery()) {
                QUERIES++;

                while (set.next()) {
                    Column column = resolveColumn(graph.getPlugin(), graph, set);
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
    private Graph resolveGraph(Plugin plugin, ResultSet set) throws SQLException {
        Graph graph = new Graph(plugin, set.getString("Name"));

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
    private Column resolveColumn(Plugin plugin, Graph graph, ResultSet set) throws SQLException {
        Column column = new Column(graph, set.getString("Name"));

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
        Plugin plugin = new Plugin(mcstats);
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
