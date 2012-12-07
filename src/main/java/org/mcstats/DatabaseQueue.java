package org.mcstats;

import org.apache.log4j.Logger;
import org.mcstats.model.RawQuery;
import org.mcstats.model.ServerPlugin;
import org.mcstats.sql.Savable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DatabaseQueue {

    private Logger logger = Logger.getLogger("DatabaseQueue");

    /**
     * The number of database queue workers
     */
    private static final int WORKER_COUNT = 2;

    /**
     * Max amount of flushes per round
     */
    private static final int FLUSHES_PER_ROUND = 5000;

    /**
     * The mcstats object
     */
    private final MCStats mcstats;

    /**
     * The queue of objects waiting to be saved to the database
     */
    private final Queue<Savable> queue = new ConcurrentLinkedQueue<Savable>();

    /**
     * The database workers
     */
    private final List<QueueWorker> workers = new ArrayList<QueueWorker>();

    public DatabaseQueue(MCStats mcstats) {
        this.mcstats = mcstats;

        // Create workers
        for (int i = 0; i < WORKER_COUNT; i++) {
            QueueWorker worker = new QueueWorker(i + 1);
            workers.add(worker);
            new Thread(worker, "DatabaseQueue Worker #" + worker.getId()).start();
            logger.info("Started DatabaseQueue Worker #" + worker.getId());
        }
    }

    /**
     * Queue an entity to be saved to the database
     *
     * @param savable
     */
    public void offer(Savable savable) {
        queue.offer(savable);
    }

    /**
     * Get the current queue size
     * @return
     */
    public int size() {
        return queue.size();
    }

    /**
     * Get an unmodifiable list of all the queue workers
     *
     * @return
     */
    public List<QueueWorker> getWorkers() {
        return Collections.unmodifiableList(workers);
    }

    public class QueueWorker implements Runnable {

        /**
         * This worker's unique id
         */
        private int id;

        /**
         * If this worker is currently busy
         */
        private boolean busy = false;

        /**
         * The time the current running job started at
         */
        private long jobStart = 0L;

        public QueueWorker(int id) {
            this.id = id;
        }

        public void run() {

            while (true) {

                try {
                    busy = false;
                    Thread.sleep(50L);
                    busy = true;
                } catch (InterruptedException e) {
                    continue;
                }

                // amount of entities we flushed
                int flushed = 0;

                // when we started flushing entities
                jobStart = System.currentTimeMillis();

                // group together server plugins
                List<ServerPlugin> serverPlugins = new LinkedList<ServerPlugin>();

                // Flush each entity
                Savable savable;
                while ((savable = queue.poll()) != null) {
                    // save it now
                    if (savable instanceof ServerPlugin) {
                        serverPlugins.add((ServerPlugin) savable);
                    } else {
                        try {
                            savable.saveNow();
                            flushed++;
                        } catch (Exception e) {
                            // Fallback gracefully so we don't exit the thread
                            e.printStackTrace();
                        }
                    }

                    if (flushed >= FLUSHES_PER_ROUND) {
                        break;
                    }
                }

                if (serverPlugins.size() > 0) {
                    String query = "UPDATE ServerPlugin SET Updated = UNIX_TIMESTAMP() WHERE ";

                    for (ServerPlugin serverPlugin : serverPlugins) {
                        query += "(Server = " + serverPlugin.getServer().getId() + " AND Plugin = " + serverPlugin.getPlugin().getId() + ") OR ";
                    }

                    // cut off the last OR
                    query = query.substring(0, query.length() - 4);
                    serverPlugins = null;
                    // execute it
                    new RawQuery(mcstats, query).saveNow();
                }

                // just so we don't spam the console if there's only 0 entities which we don't need to know about
                if (flushed > 0) {
                    // logger.debug("Flushed " + flushed + "/" + queue.size() + " entities to the database in " + (System.currentTimeMillis() - start) + "ms");
                }

            }

        }

        public int getId() {
            return id;
        }

        public boolean isBusy() {
            return busy;
        }

        public long getJobStart() {
            return jobStart;
        }

    }

}
