package org.mcstats.aws.sqs;

import com.amazonaws.services.sqs.model.Message;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.function.Function;

/**
 * Subscribes to a queue and sends received messages to a listener.
 */
public class SQSQueueSubscriber {

    private static final Logger logger = Logger.getLogger(SQSQueueSubscriber.class);

    /**
     * The sqs client
     */
    private final SimpleSQSClient sqs;

    /**
     * Url to the queue being subscribed to
     */
    private final String queueUrl;

    /**
     * Listener for messages. Returns true to ACK messages.
     */
    private final Function<Message, Boolean> listener;

    /**
     * Thread the subscriber is running in
     */
    private Thread thread = null;

    /**
     * Creates a new queue subscriber.
     *
     * @param sqs
     * @param queueName
     * @param listener the listener for new messages; returns true to ACK messages.
     */
    public SQSQueueSubscriber(SimpleSQSClient sqs, String queueName, Function<Message, Boolean> listener) {
        this.sqs = sqs;
        this.listener = listener;

        logger.info("Creating queue: " + queueName);
        queueUrl = sqs.createQueue(queueName);
    }

    /**
     * Starts the queue subscriber
     */
    public void start() {
        if (thread != null) {
            return;
        }

        thread = new Thread(new Subscriber(), "SQS-Subscriber");
        thread.start();

        logger.info("Subscribed to SQS queue at " + queueUrl);
    }

    private final class Subscriber implements Runnable {

        @Override
        public void run() {
            while (true) {
                List<Message> messages = sqs.receiveMessages(queueUrl);

                for (Message message : messages) {
                    boolean result = listener.apply(message);

                    if (result) {
                        sqs.deleteMessage(queueUrl, message);
                    }
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

    }

}
