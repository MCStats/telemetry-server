package org.mcstats;

import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.mcstats.jetty.PluginTelemetryHandler;
import org.mcstats.jetty.ServerTelemetryHandler;
import org.mcstats.jetty.StatusHandler;

import java.util.ArrayList;
import java.util.List;

public class Server {

    private static final Logger logger = Logger.getLogger(Server.class);

    /**
     * List of all jetty servers
     */
    private final List<org.eclipse.jetty.server.Server> jettyServers = new ArrayList<>();

    public Server(MCStats mcstats, int pluginTelemetryPort, int serverTelemetryPort, int statusPort) {
        jettyServers.add(createWebServer(pluginTelemetryPort, new PluginTelemetryHandler(mcstats)));
        jettyServers.add(createWebServer(serverTelemetryPort, new ServerTelemetryHandler()));
        jettyServers.add(createWebServer(statusPort, new StatusHandler(mcstats)));

        logger.info("Created plugin telemetry server on port " + pluginTelemetryPort);
        logger.info("Created server telemetry server on port " + serverTelemetryPort);
        logger.info("Created status server on port " + statusPort);
    }

    /**
     * Starts all servers and joins them. This blocks until all servers are stopped.
     */
    public void startAndJoin() throws Exception {
        for (org.eclipse.jetty.server.Server server : jettyServers) {
            server.start();
        }

        for (org.eclipse.jetty.server.Server server : jettyServers) {
            server.join();
        }
    }

    /**
     * Stops all servers.
     */
    public void stop() throws Exception {
        for (org.eclipse.jetty.server.Server server : jettyServers) {
            server.stop();
        }

        jettyServers.clear();
    }

    /**
     * Returns the number of open connections to the server
     *
     * @return
     */
    public int openConnections() {
        int result = 0;

        for (org.eclipse.jetty.server.Server server : jettyServers) {
            for (Connector connector : server.getConnectors()) {
                result += connector.getConnectedEndPoints().size();
            }
        }

        return result;
    }

    /**
     * Creates a web server with the given port and handler
     *
     * @param port
     * @param handler
     * @return
     */
    private org.eclipse.jetty.server.Server createWebServer(int port, Handler handler) {
        org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server();

        server.setHandler(handler);

        ServerConnector connector = new ServerConnector(server, 1, 1);
        connector.setPort(port);
        connector.setAcceptQueueSize(2048);
        connector.setSoLingerTime(0);

        server.addConnector(connector);
        return server;
    }

}
