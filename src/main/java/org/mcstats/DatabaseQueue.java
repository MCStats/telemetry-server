package org.mcstats;

import org.apache.log4j.Logger;
import org.mcstats.db.Savable;
import org.mcstats.model.Plugin;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Singleton
public class DatabaseQueue {

    private Logger logger = Logger.getLogger("DatabaseQueue");

    /**
     * The mcstats object
     */
    private final MCStats mcstats;

    /**
     * The queue of objects waiting to be saved to the database
     */
    private final Queue<Savable> queue = new ConcurrentLinkedQueue<>();

    /**
     * The database workers
     */
    private final List<QueueWorker> workers = new ArrayList<>();

    /**
     * Max amount of flushes per round
     */
    private final int flushesPerRound;

    /**
     * The max size of the queue
     */
    private final int maxSize;

    @Inject
    public DatabaseQueue(MCStats mcstats,
                         @Named("queue.workers") int workerCount,
                         @Named("queue.flushes") int flushes,
                         @Named("queue.maxSize") int maxSize) {
        this.mcstats = mcstats;
        this.flushesPerRound = flushes;
        this.maxSize = maxSize;

        for (int i = 0; i < workerCount; i++) {
            QueueWorker worker = new QueueWorker(i + 1);
            workers.add(worker);
            new Thread(worker, "DatabaseQueue Worker #" + worker.getId()).start();
            logger.info("Started DatabaseQueue Worker #" + worker.getId());
        }
    }

    /**
     * Clear the queue.
     */
    public void clear() {
        queue.clear();
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
     *
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

                if (queue.size() >= maxSize) { // 500k
                    synchronized (queue) {
                        Iterator iter = queue.iterator();

                        while (iter.hasNext()) {
                            Savable savable = (Savable) iter.next();

                            if (!(savable instanceof Plugin)) {
                                iter.remove();
                            }
                        }
                    }
                    continue;
                }

                // amount of entities we flushed
                int flushed = 0;

                // when we started flushing entities
                jobStart = System.currentTimeMillis();

                // Flush each entity
                long start = System.currentTimeMillis();
                Savable savable;
                while ((savable = queue.poll()) != null) {
                    try {
                        savable.saveNow();
                        flushed++;
                    } catch (Exception e) {
                        // Fallback gracefully so we don't exit the thread
                        e.printStackTrace();
                    }

                    if (flushed >= flushesPerRound) {
                        break;
                    }
                }

                // just so we don't spam the console if there's only 0 entities which we don't need to know about
                if (flushed > 0) {
                    if (mcstats.isDebug()) {
                        logger.debug("Flushed " + flushed + "/" + queue.size() + " entities to the database in " + (System.currentTimeMillis() - start) + "ms");
                    }
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
