package org.mcstats;

import org.apache.log4j.Logger;
import org.mcstats.sql.Savable;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DatabaseQueue {

    private Logger logger = Logger.getLogger("DatabaseQueue");

    /**
     * Max amount of flushes per round
     */
    private static final int FLUSHES_PER_ROUND = 10000;

    /**
     * The queue of objects waiting to be saved to the database
     */
    private final Queue<Savable> queue = new ConcurrentLinkedQueue<Savable>();

    public DatabaseQueue() {
        // Create workers
        for (int i = 0; i < 16; i++) {
            new Thread(new QueueWorker(), "DatabaseQueue Worker #" + (i + 1)).start();
            logger.info("Started DatabaseQueue Worker #" + (i + 1));
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

    private class QueueWorker implements Runnable {

        public void run() {

            while (true) {

                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    continue;
                }

                // amount of entities we flushed
                int flushed = 0;

                // when we started flushing entities
                long start = System.currentTimeMillis();

                // Flush each entity
                Savable savable;
                while ((savable = queue.poll()) != null) {
                    // save it now
                    try {
                        savable.saveNow();
                        flushed++;
                    } catch (Exception e) {
                        // Fallback gracefully so we don't exit the thread
                        e.printStackTrace();
                    }

                    if (flushed >= FLUSHES_PER_ROUND) {
                        break;
                    }
                }

                // just so we don't spam the console if there's only 0 entities which we don't need to know about
                if (flushed > 0) {
                    logger.debug("Flushed " + flushed + "/" + queue.size() + " entities to the database in " + (System.currentTimeMillis() - start) + "ms");
                }

            }

        }

    }

}
