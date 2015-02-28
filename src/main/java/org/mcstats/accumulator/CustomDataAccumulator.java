package org.mcstats.accumulator;

import org.mcstats.Accumulator;
import org.mcstats.AccumulatorContext;
import org.mcstats.model.Column;

import java.util.Map;

public class CustomDataAccumulator implements Accumulator {

    @Override
    public void accumulate(AccumulatorContext context) {
        Map<Column, Long> customData = context.getServerPlugin().getCustomData();

        for (Map.Entry<Column, Long> entry : customData.entrySet()) {
            Column column = entry.getKey();
            long value = entry.getValue();

            context.addData(column.getGraph().getName(), column.getName(), value);
        }
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

}
