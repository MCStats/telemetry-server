package org.mcstats.generator;

public class GeneratedData {

    private final String columnName;

    /**
     * The sum of the data in the data set
     */
    private int sum = 0;

    /**
     * The number of values in the data set
     */
    private int count = 0;

    /**
     * The max value in the data set
     */
    private int max = 0;

    /**
     * The min value in the data set
     */
    private int min = 0;

    @Override
    public String toString() {
        return String.format("GeneratedData(sum=%d, count=%d, avg=%d, max=%d, min=%d)", sum, count, getAverage(), max, min);
    }

    public GeneratedData(String columnName, int sum, int count, int max, int min) {
        this.columnName = columnName;
        this.sum = sum;
        this.count = count;
        this.max = max;
        this.min = min;
    }

    public int getAverage() {
        return sum / count;
    }

    public void incrementSum(int delta) {
        this.sum += delta;

        if (sum > max) {
            max = sum;
        }
        if (sum < min) {
            min = sum;
        }
    }

    public void incrementCount() {
        this.count ++;
    }

    public String getColumnName() {
        return columnName;
    }

    public int getSum() {
        return sum;
    }

    public void setSum(int sum) {
        this.sum = sum;

        if (sum > max) {
            max = sum;
        }
        if (sum < min) {
            min = sum;
        }
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        this.min = min;
    }
}
