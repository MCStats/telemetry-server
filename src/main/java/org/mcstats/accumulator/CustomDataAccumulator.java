package org.mcstats.accumulator;

import org.mcstats.Accumulator;
import org.mcstats.AccumulatorContext;

import java.util.Map;

public class CustomDataAccumulator implements Accumulator {

    @Override
    public void accumulate(AccumulatorContext context) {
        Map<String, Map<String, Long>> data = context.getRequest().getCustomData();

        data.forEach((graphName, columns) -> columns.forEach((columnName, value) -> context.addData(graphName, columnName, value)));
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

}
