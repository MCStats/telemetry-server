package org.mcstats.generator;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public interface Generator<T> {

    /**
     * Generates data for all instances of T.
     *
     * @return
     */
    ImmutableMap<String, Map<String, Datum>> generateAll();

    /**
     * Generates data for the given instance
     *
     * @param instance
     * @return
     */
    ImmutableMap<String, Map<String, Datum>> generatorFor(T instance);

}
