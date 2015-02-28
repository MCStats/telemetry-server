package org.mcstats.accumulator;

import org.mcstats.Accumulator;
import org.mcstats.AccumulatorContext;
import org.mcstats.util.Tuple;

public class VersionInfoAccumulator implements Accumulator {

    @Override
    public void accumulate(AccumulatorContext context) {
        context.addData("Version Demographics", context.getServerPlugin().getVersion(), 1);

        for (Tuple<String, String> tuple : context.getServerPlugin().getVersionChanges()) {
            // String oldVersion = tuple.first();
            String newVersion = tuple.second();

            context.addData("Version Trends", newVersion, 1);
        }
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

}
