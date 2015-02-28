package org.mcstats.accumulator;

import org.mcstats.Accumulator;
import org.mcstats.AccumulatorContext;

public class GameVersionAccumulator implements Accumulator {

    @Override
    public void accumulate(AccumulatorContext context) {
        context.addData("Game Version", context.getServerPlugin().getServer().getMinecraftVersion(), 1);
    }

}
