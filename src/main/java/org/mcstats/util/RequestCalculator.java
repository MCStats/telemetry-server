package org.mcstats.util;

import java.util.concurrent.Callable;

public class RequestCalculator {

    /**
     * The method used to calculate the requests per second
     */
    public enum CalculationMethod {

        /**
         * Request average is calculated using data since the server was started
         */
        ALL_TIME,

        /**
         * Request average is calculated using data from the last 5 seconds
         */
        FIVE_SECONDS

    }

    /**
     * The calculation method
     */
    private final CalculationMethod calculationMethod;

    /**
     * The callable to obtain the current amount of requests
     */
    private final Callable<Long> requestsCallable;

    /**
     * The time the calculator started
     */
    private long start;

    /**
     * The last time the request reference was updated if we are utilizing it
     */
    private long lastReset;

    /**
     * The base number of requests to use for our frame of reference
     */
    private long requestsReference = 0;

    public RequestCalculator(CalculationMethod calculationMethod, Callable<Long> requestsCallable) {
        this.calculationMethod = calculationMethod;
        this.requestsCallable = requestsCallable;
        this.start = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return String.format("%.1f", calculateRequestsPerSecond());
    }

    /**
     * Get the time the calculator started, in milliseconds
     *
     * @return
     */
    public long getStart() {
        return start;
    }

    /**
     * Calculate the current amount of requests per second
     *
     * @return
     */
    public double calculateRequestsPerSecond() {
        long currentRequests;
        long requestsOffset;

        try {
            currentRequests = requestsCallable.call();
            requestsOffset = requestsReference;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }

        // We need the amount of seconds we need to calculate with
        int seconds;

        switch (calculationMethod) {
            case ALL_TIME:
                seconds = (int) Math.floor((System.currentTimeMillis() - start) / 1000);
                break;

            case FIVE_SECONDS:
                seconds = 5;

                // we're only using the last 5 seconds of data so advance the request offset
                // if it has been 5 seconds
                if ((System.currentTimeMillis() - lastReset) > 5000) {
                    requestsReference = currentRequests;
                    lastReset = System.currentTimeMillis();
                }
                break;

            default:
                throw new UnsupportedOperationException("Calculation method " + calculationMethod + " is not defined in calculateRequestsPerSecond()");
        }

        return (currentRequests - requestsOffset) / (double) seconds;
    }

}
