package org.mcstats.accumulator;

import org.mcstats.Accumulator;
import org.mcstats.AccumulatorContext;

public class VersionInfoAccumulator implements Accumulator {

    @Override
    public void accumulate(AccumulatorContext context) {
        final String currentPluginVersion = context.getRequest().getPluginVersion();

        context.addData("Version Demographics", currentPluginVersion, 1);

        for (String version : context.getVersionChanges()) {
            if (!currentPluginVersion.equals(version)) {
                context.addData("Version Trends", version, 1);
            }
        }
    }

    @Override
    public boolean isGlobal() {
        return false;
    }

}
