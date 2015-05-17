package org.mcstats;

import org.apache.log4j.Logger;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.mcstats.handler.ReportHandler;
import org.mcstats.handler.StatusHandler;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class Server {

    private static final Logger logger = Logger.getLogger(Server.class);

    private final int listenPort;
    private final Provider<StatusHandler> statusHandler;
    private final Provider<ReportHandler> reportHandler;

    private org.eclipse.jetty.server.Server webServer;

    @Inject
    public Server(@Named("listen.port") int listenPort, Provider<StatusHandler> statusHandler, Provider<ReportHandler> reportHandler) {
        this.listenPort = listenPort;
        this.statusHandler = statusHandler;
        this.reportHandler = reportHandler;
    }

    /**
     * Starts the server
     */
    public void start() {
        webServer = new org.eclipse.jetty.server.Server(new QueuedThreadPool(4));

        // Create the handler list
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{statusHandler.get(), reportHandler.get()});

        webServer.setHandler(handlers);

        ServerConnector connector = new ServerConnector(webServer, 1, 1);
        connector.setPort(listenPort);
        webServer.addConnector(connector);

        try {
            webServer.start();
            logger.info("Created server on port " + listenPort);

            webServer.join();
        } catch (Exception e) {
            logger.error("Failed to create server", e);
            e.printStackTrace();
        }
    }

    /**
     * Stops the server
     */
    public void stop() {
        try {
            webServer.stop();
        } catch (Exception e) {
            logger.error("Failed to stop server", e);
            e.printStackTrace();
        }
    }

    /**
     * Get the number of currently open connections
     *
     * @return
     */
    public int openConnections() {
        int conn = 0;

        for (Connector connector : webServer.getConnectors()) {
            conn += connector.getConnectedEndPoints().size();
        }

        return conn;
    }

}
