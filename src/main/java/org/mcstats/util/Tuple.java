package org.mcstats.util;

public class Tuple<X, Y> {

    /**
     * The first value in the tuple
     */
    private final X x;

    /**
     * The second value in the tuple
     */
    private final Y y;

    public Tuple(X x, Y y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Gets the first value in the tuple
     *
     * @return
     */
    public X first() {
        return x;
    }

    /**
     * Gets the second value in the tuple
     *
     * @return
     */
    public Y second() {
        return y;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Tuple)) {
            return false;
        }

        Tuple tuple = (Tuple) object;
        return tuple.x == x && tuple.y == y;
    }

    @Override
    public int hashCode() {
        return x.hashCode() ^ y.hashCode();
    }

}
