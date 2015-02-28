package org.mcstats.accumulator;

import org.mcstats.Accumulator;
import org.mcstats.AccumulatorContext;

public class ServerSoftwareAccumulator implements Accumulator {

    @Override
    public void accumulate(AccumulatorContext context) {
        context.addData("Server Software", context.getServerPlugin().getServer().getServerSoftware(), 1);
    }

}
