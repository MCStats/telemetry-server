package org.mcstats.generation.plugin;

import org.apache.log4j.Logger;
import org.mcstats.aws.sqs.SQSWorkQueueClient;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StartPluginGeneration {

    private final SQSWorkQueueClient sqs;
    private final PluginRanker pluginRanker;

    @Inject
    public StartPluginGeneration(SQSWorkQueueClient sqs, PluginRanker pluginRanker) {
        this.sqs = sqs;
        this.pluginRanker = pluginRanker;
    }

    /**
     * Starts generation for the given bucket
     *
     * @param bucket
     */
    public void run(int bucket) {
        pluginRanker.run(bucket);
        sqs.accumulateBucket(bucket);
    }

}
