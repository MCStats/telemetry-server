package org.mcstats.processing;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.inject.Inject;
import org.apache.log4j.Logger;
import org.mcstats.MCStats;
import org.mcstats.decoder.DecodedRequest;
import org.mcstats.handler.ReportHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import javax.inject.Singleton;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Singleton
public class BatchPluginRequestProcessor {

    private static final Logger logger = Logger.getLogger(BatchPluginRequestProcessor.class);

    /**
     * The maximum amount of allowable version switches in a graph interval before they are blacklisted;
     */
    private static final int MAX_VIOLATIONS_ALLOWED = 7;

    public static final int NUM_THREADS = 16;

    /**
     * The pool used to service requests
     */
    private final ExecutorService servicePool = Executors.newFixedThreadPool(NUM_THREADS, new ThreadFactoryBuilder().setNameFormat(BatchPluginRequestProcessor.class.getSimpleName() + "-%d").build());

    /**
     * The queue used for requests
     */
    private final Queue<DecodedRequest> queue = new LinkedBlockingQueue<>();

    /**
     * SHA of the redis sum add script. TODO better way of storing the SHAs rather than locally?
     */
    private final String redisAddSumScriptSha;

    /**
     * Flag for if the processor is running or not.
     */
    private boolean running = true;

    private final Gson gson;
    private final JedisPool redisPool;

    @Inject
    public BatchPluginRequestProcessor(MCStats mcstats, Gson gson, JedisPool redisPool) {
        this.gson = gson;
        this.redisPool = redisPool;
        this.redisAddSumScriptSha = mcstats.loadRedisScript("/scripts/redis/zadd-sum.lua");

        for (int i = 0; i < NUM_THREADS; i++) {
            servicePool.execute(new Worker());
        }
    }

    /**
     * Submits a request to the processor
     *
     * @param request
     */
    public void submit(DecodedRequest request) {
        queue.add(request);
    }

    /**
     * Gets the number of requests waiting to be processed.
     *
     * @return
     */
    public int size() {
        return queue.size();
    }

    /**
     * Shuts down the processor
     */
    public void shutdown() {
        running = false;
        servicePool.shutdown();
    }

    private final class Worker implements Runnable {
        @Override
        public void run() {
            while (running) {
                try (Jedis redis = redisPool.getResource()) {
                    Pipeline pipeline = redis.pipelined();

                    // max to process at one time
                    int remaining = 1000;
                    int processed = 0;

                    // Bucket: time segment data is generated for
                    int bucket = ReportHandler.normalizeTime();

                    while (!queue.isEmpty() && --remaining >= 0) {
                        DecodedRequest request = queue.poll();

                        if (request == null) {
                            continue;
                        }

                        // Plugin versions added to a set to minimize overhead of calculated version changes
                        final String pluginVersionKey = "plugin-versions:" + bucket + ":" + request.uuid + ":" + request.plugin;
                        pipeline.sadd(pluginVersionKey, request.pluginVersion);

                        final String pluginsKey = "plugins:" + bucket;
                        pipeline.sadd(pluginsKey, Integer.toString(request.plugin));

                        final String pluginBucketKey = "plugin-data:" + bucket + ":" + request.plugin;
                        pipeline.hset(pluginBucketKey, request.uuid, gson.toJson(request));

                        processed++;
                    }

                    if (processed > 100) {
                        logger.debug("Processed " + processed + " requests");
                    }

                    pipeline.sync();
                    Thread.sleep(5L);
                } catch (InterruptedException e) {
                    logger.debug("Interrupted!", e);
                    break;
                }

            }
        }
    }

}
