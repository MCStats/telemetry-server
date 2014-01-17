package org.mcstats.generator;

import org.mcstats.model.Server;

public class DecoderAggregator<T> extends ReflectionAggregator {

    /**
     * The value decoder
     */
    private Decoder<T> decoder;

    public interface Decoder<T> {

        /**
         * Decode the given value into a string
         *
         * @param value
         * @return
         */
        public String decode(T value);

    }

    /**
     * DecoderAggregator that feeds the value of the field to the decoder and returns the column name that will be used
     *
     * @param fieldName
     * @param graphName
     * @param decoder
     */
    public DecoderAggregator(String fieldName, String graphName, Decoder<T> decoder) {
        super(fieldName, graphName);
        this.decoder = decoder;
    }

    @Override
    public String getColumnName(Server server) {
        try {
            @SuppressWarnings({"unchecked"})
            T value = (T) field.get(server);
            return decoder.decode(value);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    @Override
    public long getColumnValue(Object fieldValue, String usingColumn) {
        return 1;
    }

}
