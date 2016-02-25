package org.mcstats.db;

import org.eclipse.jetty.util.ConcurrentHashSet;
import org.mcstats.MCStats;
import org.mcstats.model.Plugin;
import org.mcstats.model.Server;
import org.mcstats.model.ServerPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PluginOnlyMySQLDatabase extends MySQLDatabase {

    private MCStats mcstats;

    /**
     * An integer to give out to identify certain objects (e.g. servers, versions)
     */
    private AtomicInteger currentId = new AtomicInteger(0);

    /**
     * A map of all servers
     */
    private Map<String, Server> servers = new ConcurrentHashMap<>();

    /**
     * A set of all blacklisted servers
     */
    private Set<Server> blacklistedServers = new ConcurrentHashSet<>();

    public PluginOnlyMySQLDatabase(MCStats mcstats, String hostname, String databaseName, String username, String password) {
        super(mcstats, hostname, databaseName, username, password);
        this.mcstats = mcstats;
    }

    public void resetIntervalData() {
        servers.clear();
        blacklistedServers.clear();
        currentId = new AtomicInteger(0);
    }

    @Override
    public boolean isServerBlacklisted(Server server) {
        return blacklistedServers.contains(server);
    }

    @Override
    public void blacklistServer(Server server) {
        blacklistedServers.add(server);
    }

    @Override
    public void saveServer(Server server) {
        // no persistence
    }

    @Override
    public Server loadServer(String guid) {
        return servers.get(guid);
    }

    @Override
    public Server createServer(String guid) {
        Server server = new Server(mcstats);
        server.setId(currentId.incrementAndGet());
        server.setGUID(guid);
        servers.put(guid, server);
        return server;
    }

    @Override
    public void saveServerPlugin(ServerPlugin serverPlugin) {
        // no persistence
    }

    @Override
    public List<ServerPlugin> loadServerPlugins(Server server) {
        // no persistence
        return new ArrayList<>();
    }

    @Override
    public ServerPlugin loadServerPlugin(Server server, Plugin plugin) {
        // no persistence
        return null;
    }

    @Override
    public ServerPlugin createServerPlugin(Server server, Plugin plugin, String version) {
        ServerPlugin serverPlugin = new ServerPlugin(mcstats, server, plugin);
        serverPlugin.setVersion(version);
        return serverPlugin;
    }

}
