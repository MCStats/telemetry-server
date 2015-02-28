package org.mcstats.accumulator;

import org.mcstats.Accumulator;
import org.mcstats.AccumulatorContext;
import org.mcstats.MCStats;
import org.mcstats.model.Server;

public class ServerInfoAccumulator implements Accumulator {

    @Override
    public void accumulate(AccumulatorContext context) {
        Server server = context.getServerPlugin().getServer();

        // TODO remove need for MCStats.getInstance()
        String countryName = MCStats.getInstance().getCountryName(server.getCountry());

        context.addData("Global Statistics", "Players", server.getPlayers());
        context.addData("Global Statistics", "Servers", 1);

        context.addData("Auth Mode", server.getOnlineMode() == 1 ? "Online" : "Offline", 1);
        context.addData("Game Version", context.getServerPlugin().getServer().getMinecraftVersion(), 1);

        context.addData("Server Locations", countryName, 1);

        context.addDonutData("Java Version", server.getJavaName(), server.getJavaVersion(), 1);

        context.addData("Server Software", server.getServerSoftware(), 1);

        context.addData("System Arch", server.getOSArch(), 1);
        context.addDonutData("Operating System", server.getOSName(), server.getOSVersion(), 1);
        context.addData("System Cores", Integer.toString(server.getCores()), 1);
    }

}
