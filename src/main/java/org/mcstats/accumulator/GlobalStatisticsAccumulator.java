package org.mcstats.accumulator;

import org.mcstats.Accumulator;
import org.mcstats.AccumulatorContext;

public class GlobalStatisticsAccumulator implements Accumulator {

    @Override
    public void accumulate(AccumulatorContext context) {
        context.addData("Global Statistics", "Players", context.getServerPlugin().getServer().getPlayers());
        context.addData("Global Statistics", "Servers", 1);
    }

}
