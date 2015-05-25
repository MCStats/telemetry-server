package org.mcstats.worker;

import com.amazonaws.services.sqs.model.Message;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.mcstats.aws.sqs.SQSQueueSubscriber;
import org.mcstats.aws.sqs.SimpleSQSClient;
import org.mcstats.generator.PluginGraphGenerator;
import org.mcstats.processing.PluginDataAccumulator;

import javax.inject.Inject;
import javax.inject.Named;

public class SQSWorker {

    private static final Logger logger = Logger.getLogger(SQSWorker.class);

    /**
     * The SQS queue subscriber
     */
    private final SQSQueueSubscriber subscriber;

    private final PluginDataAccumulator pluginDataAccumulator;
    private final PluginGraphGenerator pluginGraphGenerator;

    @Inject
    public SQSWorker(SimpleSQSClient sqs,
                     PluginDataAccumulator pluginDataAccumulator,
                     PluginGraphGenerator pluginGraphGenerator,
                     @Named("sqs.work-queue") String workQueueName) {
        this.pluginDataAccumulator = pluginDataAccumulator;
        this.pluginGraphGenerator = pluginGraphGenerator;

        this.subscriber = new SQSQueueSubscriber(sqs, workQueueName, this::processMessage);
    }

    private boolean processPluginMessage(JSONObject root) {
        String action = root.get("action").toString();

        switch (action) {
            case "accumulate": {
                int bucket = Integer.parseInt(root.get("bucket").toString());

                pluginDataAccumulator.accumulate(bucket);
                break;
            }

            case "generate": {
                int bucket = Integer.parseInt(root.get("bucket").toString());

                pluginGraphGenerator.generateBucket(bucket);
                break;
            }
        }

        return true;
    }

    /**
     * Processes a message received from SQS.
     *
     * @param message
     * @return
     */
    private boolean processMessage(Message message) {
        JSONObject root = (JSONObject) JSONValue.parse(message.getBody());
        logger.info("Received subscribed message: " + root.toString());

        String type = root.get("type").toString();

        switch (type) {
            case "plugin":
                return processPluginMessage(root);

            default:
                throw new UnsupportedOperationException("Unhandled message type: " + type);
        }
    }

    /**
     * Starts the worker
     */
    public void start() {
        subscriber.start();
    }

}
