package org.mcstats.aws.sqs;

import com.amazonaws.services.sqs.model.Message;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.mcstats.generation.plugin.StartPluginGeneration;
import org.mcstats.generation.plugin.PluginGraphGenerator;
import org.mcstats.generation.plugin.PluginDataAccumulator;

import javax.inject.Inject;
import javax.inject.Named;

public class SQSWorker {

    private static final Logger logger = Logger.getLogger(SQSWorker.class);

    /**
     * The SQS queue subscriber
     */
    private final SQSQueueSubscriber subscriber;

    private final StartPluginGeneration startPluginGeneration;
    private final PluginDataAccumulator pluginDataAccumulator;
    private final PluginGraphGenerator pluginGraphGenerator;

    @Inject
    public SQSWorker(SimpleSQSClient sqs,
                     StartPluginGeneration startPluginGeneration,
                     PluginDataAccumulator pluginDataAccumulator,
                     PluginGraphGenerator pluginGraphGenerator,
                     @Named("sqs.work-queue") String workQueueName) {
        this.startPluginGeneration = startPluginGeneration;
        this.pluginDataAccumulator = pluginDataAccumulator;
        this.pluginGraphGenerator = pluginGraphGenerator;

        this.subscriber = new SQSQueueSubscriber(sqs, workQueueName, this::processMessage);
    }

    private boolean processPluginMessage(JSONObject root) {
        String action = root.get("action").toString();

        switch (action) {
            case "startGraphGeneration": {
                int bucket = Integer.parseInt(root.get("bucket").toString());

                startPluginGeneration.run(bucket);
                break;
            }

            case "accumulate": {
                int bucket = Integer.parseInt(root.get("bucket").toString());

                pluginDataAccumulator.run(bucket);
                break;
            }

            case "generate": {
                int bucket = Integer.parseInt(root.get("bucket").toString());

                pluginGraphGenerator.run(bucket);
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

        if (root == null) {
            logger.info("Received invalid message: " + message.getBody());
            return true;
        }

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
