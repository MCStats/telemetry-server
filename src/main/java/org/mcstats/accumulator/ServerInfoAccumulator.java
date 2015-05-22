package org.mcstats.accumulator;

import org.mcstats.Accumulator;
import org.mcstats.AccumulatorContext;
import org.mcstats.MCStats;
import org.mcstats.decoder.DecodedRequest;
import org.mcstats.util.ServerBuildIdentifier;

public class ServerInfoAccumulator implements Accumulator {

    private final MCStats mcstats;
    private final ServerBuildIdentifier serverBuildIdentifier;

    public ServerInfoAccumulator(MCStats mcstats, ServerBuildIdentifier serverBuildIdentifier) {
        this.mcstats = mcstats;
        this.serverBuildIdentifier = serverBuildIdentifier;
    }

    @Override
    public void accumulate(AccumulatorContext context) {
        DecodedRequest request = context.getRequest();

        // TODO remove need for mcstats
        final String countryName = mcstats.getCountryName(request.getCountry());
        final String serverVersion = serverBuildIdentifier.getServerVersion(request.getServerVersion());
        final String minecraftVersion = serverBuildIdentifier.getMinecraftVersion(request.getServerVersion());

        context.addData("Global Statistics", "Players", request.getPlayersOnline());
        context.addData("Global Statistics", "Servers", 1);

        context.addData("Auth Mode", request.getAuthMode() == 1 ? "Online" : "Offline", 1);
        context.addData("Game Version", minecraftVersion, 1);

        context.addData("Server Locations", countryName, 1);

        context.addDonutData("Java Version", request.getJavaName(), request.getJavaVersion(), 1);

        context.addData("Server Software", serverVersion, 1);

        context.addData("System Arch", request.getOSArch(), 1);
        context.addDonutData("Operating System", request.getOSName(), request.getOSVersion(), 1);
        context.addData("System Cores", Integer.toString(request.getCores()), 1);
    }

}
