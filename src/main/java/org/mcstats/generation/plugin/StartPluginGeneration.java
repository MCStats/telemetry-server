package org.mcstats.generation.plugin;

import org.apache.log4j.Logger;
import org.mcstats.aws.sqs.SQSWorkQueueClient;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StartPluginGeneration {

    private static final Logger logger = Logger.getLogger(StartPluginGeneration.class);

    private final SQSWorkQueueClient sqs;

    @Inject
    public StartPluginGeneration(SQSWorkQueueClient sqs) {
        this.sqs = sqs;
    }

    /**
     * Starts generation for the given bucket
     *
     * @param bucket
     */
    public void run(int bucket) {
        //
    }

}
