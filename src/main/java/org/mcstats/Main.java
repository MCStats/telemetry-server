package org.mcstats;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import java.util.Properties;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class);

    public static void main(String[] args) {
        // log4j
        BasicConfigurator.configure();

        // start
        MCStats mcstats = new MCStats();

        mcstats.start();

        Properties config = mcstats.getConfig();

        int pluginTelemetryPort = Integer.parseInt(config.getProperty("listen.port"));
        int serverTelemetryPort = Integer.parseInt(config.getProperty("server-telemetry.port"));
        int statusPort = Integer.parseInt(config.getProperty("status.port"));

        Server server = new Server(mcstats, pluginTelemetryPort, serverTelemetryPort, statusPort);

        try {
            server.startAndJoin();
        } catch (Exception e) {
            logger.error("Failed to start and join servers", e);
        }
    }

}
