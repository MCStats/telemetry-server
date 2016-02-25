package org.mcstats.generator;

public class Datum {

    /**
     * The sum of the data in the data set
     */
    private long sum = 0;

    /**
     * The number of values in the data set
     */
    private long count = 0;

    /**
     * The max value in the data set
     */
    private long max = Integer.MIN_VALUE;

    /**
     * The min value in the data set
     */
    private long min = Integer.MAX_VALUE;

    @Override
    public String toString() {
        return String.format("Datum(sum=%d, count=%d, avg=%d, max=%d, min=%d)", sum, count, getAverage(), max, min);
    }

    public long getAverage() {
        return sum / count;
    }

    public void incrementSum(long delta) {
        this.sum += delta;

        if (delta > max) {
            max = delta;
        }
        if (delta < min) {
            min = delta;
        }
    }

    public void incrementCount() {
        this.count ++;
    }

    public long getSum() {
        return sum;
    }

    public void setSum(long sum) {
        this.sum = sum;

        if (sum > max) {
            max = sum;
        }
        if (sum < min) {
            min = sum;
        }
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public long getMax() {
        return max;
    }

    public void setMax(long max) {
        this.max = max;
    }

    public long getMin() {
        return min;
    }

    public void setMin(long min) {
        this.min = min;
    }

}
