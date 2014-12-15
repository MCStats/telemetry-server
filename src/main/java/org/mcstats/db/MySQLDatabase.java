package org.mcstats.db;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.log4j.Logger;
import org.mcstats.MCStats;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.model.PluginVersion;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    private final Map<String, PreparedStatement> statementCache = new HashMap<String, PreparedStatement>();

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
        ds.setInitialSize(50);
        ds.setMaxActive(50);
    }

    public void executeUpdate(String query) throws SQLException {
        Statement statement = null;
        Connection connection = null;

        try {
            connection = ds.getConnection();
            statement = connection.createStatement();
            statement.executeUpdate(query);
            QUERIES++;
        } finally {
            if (statement != null) {
                statement.close();
            }

            safeClose(connection);
        }
    }

    public Map<String, String> loadCountries() {
        Map<String, String> countries = new HashMap<String, String>();

        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT ShortCode, FullName FROM Country");
            ResultSet set = statement.executeQuery();

            while (set.next()) {
                countries.put(set.getString("ShortCode"), set.getString("FullName"));
            }

            safeClose(connection);
        } catch (SQLException e) {
        }

        return countries;
    }

    public Plugin createPlugin(String name) {
        Connection connection = null;

        try {
            connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO Plugin (Name, Author, Hidden, GlobalHits, Created) VALUES (?, '', 0, 0, UNIX_TIMESTAMP())");
            statement.setString(1, name);
            statement.executeUpdate();
            QUERIES++;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            safeClose(connection);
        }

        // re-load the plugin
        return loadPlugin(name);
    }

    public List<Plugin> loadPlugins() {
        List<Plugin> plugins = new ArrayList<Plugin>();

        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT ID, Parent, Name, Author, Hidden, GlobalHits, Rank, LastRank, LastRankChange, Created, LastUpdated, ServerCount30 FROM Plugin WHERE Parent = -1");
            ResultSet set = statement.executeQuery();

            while (set.next()) {
                plugins.add(resolvePlugin(set));
            }

            set.close();
            safeClose(connection);
            return plugins;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return plugins;
    }

    public Plugin loadPlugin(int id) {
        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT ID, Parent, Name, Author, Hidden, GlobalHits, Rank, LastRank, LastRankChange, Created, LastUpdated, ServerCount30 FROM Plugin WHERE ID = ?");
            statement.setInt(1, id);
            ResultSet set = statement.executeQuery();

            if (set.next()) {
                Plugin plugin = resolvePlugin(set);
                set.close();
                safeClose(connection);
                return plugin;
            }

            safeClose(connection);
            return null;
        } catch (SQLException e) {
            return null;
        }
    }

    public Plugin loadPlugin(String name) {
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT ID, Parent, Name, Author, Hidden, GlobalHits, Rank, LastRank, LastRankChange, Created, LastUpdated, ServerCount30 FROM Plugin WHERE Name = ?")) {
            statement.setString(1, name);
            ResultSet set = statement.executeQuery();

            if (set.next()) {
                Plugin plugin = resolvePlugin(set);
                set.close();
                safeClose(connection);
                return plugin;
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

    public PluginVersion createPluginVersion(Plugin plugin, String version) {
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO Versions (Plugin, Version, Created) VALUES (?, ?, UNIX_TIMESTAMP())")) {
            statement.setInt(1, plugin.getId());
            statement.setString(2, version);
            statement.executeUpdate();
            QUERIES++;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return loadPluginVersion(plugin, version);
    }

    public List<PluginVersion> loadPluginVersions(Plugin plugin) {
        List<PluginVersion> versions = new ArrayList<PluginVersion>();

        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT ID, Version, Created FROM Versions WHERE Plugin = ?");
            statement.setInt(1, plugin.getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    versions.add(resolvePluginVersion(plugin, set));
                }
            }

            QUERIES++;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return versions;
    }

    public PluginVersion loadPluginVersion(Plugin plugin, String version) {
        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT ID, Version, Created FROM Versions WHERE Plugin = ? AND Version = ?");
            statement.setInt(1, plugin.getId());
            statement.setString(2, version);
            ResultSet set = statement.executeQuery();

            if (set.next()) {
                PluginVersion pluginVersion = resolvePluginVersion(plugin, set);
                set.close();
                safeClose(connection);
                return pluginVersion;
            }

            safeClose(connection);
            QUERIES++;
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public ServerPlugin createServerPlugin(Server server, Plugin plugin, String version) {
        // make sure there's a Versions row for that version
        PluginVersion pluginVersion = plugin.getVersionByName(version);

        if (pluginVersion == null) {
            pluginVersion = loadPluginVersion(plugin, version);
        }

        // version still does not exist, so create it
        if (pluginVersion == null) {
            pluginVersion = createPluginVersion(plugin, version);
            plugin.addVersion(pluginVersion);
        }

        Connection connection = null;
        try {
            connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO ServerPlugin (Server, Plugin, Version, Updated) VALUES (?, ?, ?, UNIX_TIMESTAMP())");
            statement.setInt(1, server.getId());
            statement.setInt(2, plugin.getId());
            statement.setString(3, version);

            statement.executeUpdate();
        } catch (SQLException e) {
            logger.info("createServerPlugin() => " + e.getMessage());
        } finally {
            safeClose(connection);
        }

        QUERIES++;
        return loadServerPlugin(server, plugin);
    }

    public ServerPlugin loadServerPlugin(Server server, Plugin plugin) {
        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT Version, Revision, Updated FROM ServerPlugin WHERE Server = ? AND Plugin = ?");
            statement.setInt(1, server.getId());
            statement.setInt(2, plugin.getId());
            ResultSet set = statement.executeQuery();
            QUERIES++;

            if (set.next()) {
                String version = set.getString("Version");
                int revision = set.getInt("Revision");
                int updated = set.getInt("Updated");

                ServerPlugin serverPlugin = new ServerPlugin(this.mcstats, server, plugin);
                serverPlugin.setVersion(version);
                serverPlugin.setUpdated(updated);
                serverPlugin.setRevision(revision);
                serverPlugin.setModified(false);

                set.close();
                safeClose(connection);
                return serverPlugin;
            }

            safeClose(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public List<ServerPlugin> loadServerPlugins(Server server) {
        List<ServerPlugin> plugins = new ArrayList<ServerPlugin>();

        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT Plugin, Version, Revision, Updated FROM ServerPlugin WHERE Server = ?");
            statement.setInt(1, server.getId());
            ResultSet set = statement.executeQuery();
            QUERIES++;

            while (set.next()) {
                int pluginId = set.getInt("Plugin");
                String version = set.getString("Version");
                int revision = set.getInt("Revision");
                int updated = set.getInt("Updated");

                Plugin plugin = this.mcstats.loadPlugin(pluginId);

                if (plugin != null) {
                    ServerPlugin serverPlugin = new ServerPlugin(this.mcstats, server, plugin);
                    serverPlugin.setVersion(version);
                    serverPlugin.setRevision(revision);
                    serverPlugin.setUpdated(updated);
                    serverPlugin.setModified(false);

                    plugins.add(serverPlugin);
                }
            }
            set.close();
            safeClose(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return plugins;
    }

    public void saveServerPlugin(ServerPlugin serverPlugin) {
        Connection connection = null;
        try {
            connection = ds.getConnection();
            PreparedStatement statement;

            if (serverPlugin.isVersionModified()) {
                statement = connection.prepareStatement("UPDATE ServerPlugin SET Version = ? , Revision = ?, Updated = UNIX_TIMESTAMP() WHERE Server = ? AND Plugin = ?");
                statement.setString(1, serverPlugin.getVersion());
                statement.setInt(2, serverPlugin.getRevision());
                statement.setInt(3, serverPlugin.getServer().getId());
                statement.setInt(4, serverPlugin.getPlugin().getId());
            } else {
                statement = connection.prepareStatement("UPDATE ServerPlugin SET Updated = UNIX_TIMESTAMP() , Revision = ? WHERE Server = ? AND Plugin = ?");
                statement.setInt(1, serverPlugin.getRevision());
                statement.setInt(2, serverPlugin.getServer().getId());
                statement.setInt(3, serverPlugin.getPlugin().getId());
            }

            statement.executeUpdate();
            QUERIES++;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            safeClose(connection);
        }
    }

    public void addPluginVersionHistory(Server server, PluginVersion version) {
        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO VersionHistory (Plugin, Server, Version, Created) VALUES (?, ?, ?, UNIX_TIMESTAMP())");
            statement.setInt(1, version.getPlugin().getId());
            statement.setInt(2, server.getId());
            statement.setInt(3, version.getId());

            statement.executeUpdate();
            safeClose(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Server createServer(String guid) {
        Connection connection = null;

        try {
            connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO Server (GUID, Players, Country, ServerVersion, Created) VALUES (?, 0, 'ZZ', '', UNIX_TIMESTAMP())");
            statement.setString(1, guid);
            statement.executeUpdate();
            QUERIES++;
        } catch (SQLException e) {
            logger.info("createServer() => " + e.getMessage());
            return loadServer(guid);
        } finally {
            safeClose(connection);
        }

        // re-load the plugin
        return loadServer(guid);
    }

    public Server loadServer(String guid) {
        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT ID, GUID, Players, Country, ServerVersion, Created, ServerSoftware, MinecraftVersion, osname, osarch, osversion, cores, online_mode, java_name, java_version FROM Server WHERE GUID = ?");
            statement.setString(1, guid);
            ResultSet set = statement.executeQuery();
            QUERIES++;

            if (set.next()) {
                Server server = resolveServer(set);
                set.close();
                safeClose(connection);
                return server;
            }

            safeClose(connection);
        } catch (SQLException e) {
            return null;
        }

        return null;
    }

    public void saveServer(Server server) {
        Connection connection = null;
        try {
            connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("UPDATE Server SET GUID = ?, ServerVersion = ?, Players = ?, Country = ?, Created = ?, ServerSoftware = ?, MinecraftVersion = ?, osname = ?, osarch = ?, osversion = ?, cores = ?, online_mode = ?, java_name = ?, java_version = ? WHERE ID = ?");
            statement.setString(1, server.getGUID());
            statement.setString(2, server.getServerVersion());
            statement.setInt(3, server.getPlayers());
            statement.setString(4, server.getCountry());
            statement.setInt(5, server.getCreated());
            statement.setString(6, server.getServerSoftware());
            statement.setString(7, server.getMinecraftVersion());
            statement.setString(8, server.getOSName());
            statement.setString(9, server.getOSArch());
            statement.setString(10, server.getOSVersion());
            statement.setInt(11, server.getCores());
            statement.setInt(12, server.getOnlineMode());
            statement.setString(13, server.getJavaName());
            statement.setString(14, server.getJavaVersion());
            statement.setInt(15, server.getId());

            statement.executeUpdate();
            QUERIES++;
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            safeClose(connection);
        }
    }

    public Graph createGraph(Plugin plugin, String name) {
        Connection connection = null;

        try {
            connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO Graph (Plugin, Type, Active, Name, DisplayName, Scale) VALUES (?, ?, ?, ?, ?, ?)");
            statement.setInt(1, plugin.getId());
            statement.setInt(2, 0); // line
            statement.setInt(3, 0); // active
            statement.setString(4, name);
            statement.setString(5, name);
            statement.setString(6, "linear");
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            safeClose(connection);
        }

        return loadGraph(plugin, name);
    }

    public Graph loadGraph(Plugin plugin, String name) {
        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT ID, Type, Position, Active, Name, DisplayName, Scale FROM Graph WHERE Plugin = ? AND Name = ?");
            statement.setInt(1, plugin.getId());
            statement.setString(2, name);
            ResultSet set = statement.executeQuery();

            if (set.next()) {
                Graph graph = resolveGraph(plugin, set);
                set.close();
                safeClose(connection);
                return graph;
            }

            safeClose(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    public List<Graph> loadGraphs(Plugin plugin) {
        List<Graph> graphs = new ArrayList<Graph>();
        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT ID, Type, Position, Active, Name, DisplayName, Scale FROM Graph WHERE Plugin = ?");
            statement.setInt(1, plugin.getId());
            ResultSet set = statement.executeQuery();

            while (set.next()) {
                Graph graph = resolveGraph(plugin, set);
                graphs.add(graph);
            }

            set.close();
            safeClose(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return graphs;
    }

    public Column createColumn(Graph graph, String name) {
        if (name.length() > 100) {
            return null;
        }

        Connection connection = null;

        try {
            connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO CustomColumn (Plugin, Graph, Name) VALUES (?, ?, ?)");
            statement.setInt(1, graph.getPlugin().getId());
            statement.setInt(2, graph.getId());
            statement.setString(3, name);
            statement.executeUpdate();
            QUERIES++;
        } catch (SQLException e) {
                logger.info("Failed to create column " + name + " for graph: " + graph.getId());
        } finally {
            safeClose(connection);
        }

        return loadColumn(graph, name);
    }

    public Column loadColumn(Graph graph, String name) {
        if (name.length() > 100) {
            return null;
        }

        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT ID, Name FROM CustomColumn WHERE Graph = ? AND Name = ?");
            statement.setInt(1, graph.getId());
            statement.setString(2, name);
            ResultSet set = statement.executeQuery();
            QUERIES++;

            if (set.next()) {
                Column column = resolveColumn(graph.getPlugin(), graph, set);
                set.close();
                safeClose(connection);
                return column;
            }

            safeClose(connection);
        } catch (SQLException e) {
            logger.info("Failed to load column " + name + " for graph: " + graph.getId());
            e.printStackTrace();
        }

        return null;
    }

    public List<Column> loadColumns(Graph graph) {
        List<Column> columns = new ArrayList<Column>();
        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT ID, Name FROM CustomColumn WHERE Graph = ?");
            statement.setInt(1, graph.getId());
            ResultSet set = statement.executeQuery();
            QUERIES++;

            while (set.next()) {
                Column column = resolveColumn(graph.getPlugin(), graph, set);
                columns.add(column);
            }

            set.close();
            safeClose(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return columns;
    }

    public void blacklistServer(Server server) {
        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO ServerBlacklist (Server, Violations) VALUES (?, ?)");
            statement.setInt(1, server.getId());
            statement.setInt(2, server.getViolationCount());
            statement.executeUpdate();
            QUERIES++;

            // Now remove any version history
            statement = connection.prepareStatement("DELETE FROM VersionHistory WHERE Server = ?");
            statement.setInt(1, server.getId());

            // all good !
            safeClose(connection);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isServerBlacklisted(Server server) {
        try {
            Connection connection = ds.getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT Violations FROM ServerBlacklist WHERE Server = ?");
            statement.setInt(1, server.getId());
            QUERIES++;

            ResultSet set = statement.executeQuery();
            int violations = -1;
            if (set.next()) {
                violations = set.getInt("Violations");
            }

            set.close();
            safeClose(connection);
            return violations >= 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Close a connection
     *
     * @param connection
     */
    private void safeClose(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Resolve a graph from a ResultSet. Does not close the result set.
     *
     * @param set
     * @return
     * @throws SQLException
     */
    private Graph resolveGraph(Plugin plugin, ResultSet set) throws SQLException {
        Graph graph = new Graph(mcstats, plugin);
        graph.setId(set.getInt("ID"));
        graph.setType(set.getInt("Type"));
        graph.setPosition(set.getInt("Position"));
        graph.setActive(set.getInt("Active"));
        graph.setName(set.getString("Name"));
        graph.setDisplayName(set.getString("DisplayName"));
        graph.setScale(set.getString("Scale"));
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
        Column column = new Column(mcstats, graph, plugin);
        column.setId(set.getInt("ID"));
        column.setName(set.getString("Name"));
        return column;
    }

    /**
     * Resolve a server from a ResultSet. Does not close the result set.
     *
     * @param set
     * @return
     * @throws SQLException
     */
    private Server resolveServer(ResultSet set) throws SQLException {
        Server server = new Server(this.mcstats);
        server.setId(set.getInt("ID"));
        server.setGUID(set.getString("GUID"));
        server.setPlayers(set.getInt("Players"));
        server.setCountry(set.getString("Country"));
        server.setServerVersion(set.getString("ServerVersion"));
        server.setCreated(set.getInt("Created"));
        server.setServerSoftware(set.getString("ServerSoftware"));
        server.setMinecraftVersion(set.getString("MinecraftVersion"));
        server.setOSName(set.getString("osname"));
        server.setOSArch(set.getString("osarch"));
        server.setOSVersion(set.getString("osversion"));
        server.setCores(set.getInt("cores"));
        server.setOnlineMode(set.getInt("online_mode"));
        server.setJavaName(set.getString("java_name"));
        server.setJavaVersion(set.getString("java_version"));
        server.setModified(false);
        return server;
    }

    /**
     * Resolve a plugin version from a ResultSet. Does not close the result set.
     *
     * @param set
     * @return
     * @throws SQLException
     */
    private PluginVersion resolvePluginVersion(Plugin plugin, ResultSet set) throws SQLException {
        PluginVersion version = new PluginVersion(mcstats, plugin);
        version.setId(set.getInt("ID"));
        version.setVersion(set.getString("Version"));
        version.setCreated(set.getInt("Created"));
        return version;
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
