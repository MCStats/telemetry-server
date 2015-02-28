package org.mcstats.cron;

import org.mcstats.MCStats;
import org.mcstats.db.GraphStore;
import org.mcstats.handler.ReportHandler;

import java.util.logging.Logger;

public class CronGraphGenerator implements Runnable {

    private Logger logger = Logger.getLogger(getClass().getSimpleName());

    private MCStats mcstats;

    public CronGraphGenerator(MCStats mcstats) {
        this.mcstats = mcstats;

        // custom data

        // auth mode
        // game version
        // global stats
        // > Players
        // > Servers
        // java version, donut
        // operating system, donut
        // rank
        // revision
        // server locations
        // server software
        // system arch
        // system cores
        // version demographics
        // version trends
    }

    /**
     * {@inheritDoc}
     */
    public void run() {
        try {
            logger.info("Graph generating is not yet supported.");
            GraphStore store = mcstats.getGraphStore();
            ReportHandler.SOFT_IGNORE_REQUESTS = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ReportHandler.SOFT_IGNORE_REQUESTS = false;
        }
    }
}
