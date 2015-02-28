package org.mcstats;

public interface Accumulator {

    /**
     * Accumulates data for the given request
     *
     *
     * @param context@return
     */
    void accumulate(AccumulatorContext context);

    /**
     * Returns true if the accumulator can be used for accumulating data for all servers
     * (as a whole) as well as plugins.
     *
     * @return
     */
    default boolean isGlobal() {
        return true;
    }

}
