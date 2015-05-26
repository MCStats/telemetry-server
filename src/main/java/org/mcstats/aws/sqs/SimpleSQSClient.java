package org.mcstats.aws.sqs;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.Message;
import org.apache.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

public class SimpleSQSClient {

    private static final int RECEIVE_WAIT_TIME_SECONDS = 20;
    private static final int VISIBILITY_TIMEOUT_SECONDS = 120;

    private static final Logger logger = Logger.getLogger(SimpleSQSClient.class);

    /**
     * SQS client
     */
    private final AmazonSQS sqs;

    /**
     * Receive wait time set on queues
     */
    private int receiveWaitTime = RECEIVE_WAIT_TIME_SECONDS;

    /**
     * Visibility timeout on queues
     */
    private int visibilityTimeout = VISIBILITY_TIMEOUT_SECONDS;

    @Inject
    public SimpleSQSClient(@Named("aws.access-key") String accessKey,
                           @Named("aws.secret-key") String secretKey) {
        sqs = new AmazonSQSClient(new BasicAWSCredentials(accessKey, secretKey));
    }

    /**
     * Creates an SQS queue. The queue's URL is returned if created successfully.
     *
     * @return queue URL
     */
    public String createQueue(String queueName) {
        CreateQueueRequest request = new CreateQueueRequest(queueName);
        request.addAttributesEntry("ReceiveMessageWaitTimeSeconds", Integer.toString(receiveWaitTime));
        request.addAttributesEntry("VisibilityTimeout", Integer.toString(visibilityTimeout));

        return sqs.createQueue(request).getQueueUrl();
    }

    /**
     * Sends a message to the given queue
     *
     * @param queueUrl
     * @param messageBody
     */
    public void sendMessage(String queueUrl, String messageBody) {
        sqs.sendMessage(queueUrl, messageBody);
    }

    /**
     * Deletes a message from the given queue
     *
     * @param queueUrl
     * @param message
     */
    public void deleteMessage(String queueUrl, Message message) {
        sqs.deleteMessage(queueUrl, message.getReceiptHandle());
    }

    /**
     * Receives messages from the SQS queue
     *
     * @param queueUrl
     * @return
     */
    public List<Message> receiveMessages(String queueUrl) {
        return sqs.receiveMessage(queueUrl).getMessages();
    }

    public int getReceiveWaitTime() {
        return receiveWaitTime;
    }

    public void setReceiveWaitTime(int receiveWaitTime) {
        this.receiveWaitTime = receiveWaitTime;
    }

    public int getVisibilityTimeout() {
        return visibilityTimeout;
    }

    public void setVisibilityTimeout(int visibilityTimeout) {
        this.visibilityTimeout = visibilityTimeout;
    }

}
