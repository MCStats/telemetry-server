package org.mcstats.accumulator;

import org.mcstats.Accumulator;
import org.mcstats.AccumulatorContext;

public class MCStatsInfoAccumulator implements Accumulator {

    @Override
    public void accumulate(AccumulatorContext context) {
        context.addData("MCStats Revision", Integer.toString(context.getRequest().getRevision()), 1);
    }

}
