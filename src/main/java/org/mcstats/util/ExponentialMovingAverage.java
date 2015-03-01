package org.mcstats.util;

/**
 * Simple implementation of an EMA.
 */
public class ExponentialMovingAverage {

    /**
     * The alpha parameter
     */
    private double alpha;

    /**
     * Last computed value
     */
    private double lastValue;

    /**
     * True if the EMA has been initialized / seeded
     */
    private boolean initialized = false;

    public ExponentialMovingAverage(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Returns the current average
     *
     * @return
     */
    public double getAverage() {
        return lastValue;
    }

    /**
     * Updates the current average.
     *
     * @param value
     * @return
     */
    public double update(double value) {
        if (!initialized) {
            lastValue = value;
            initialized = true;
            return value;
        } else {
            lastValue = lastValue + alpha * (value - lastValue);
            return lastValue;
        }
    }

}
