package org.mcstats.jetty;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.simple.JSONObject;
import org.mcstats.MCStats;
import org.mcstats.Server;
import org.mcstats.db.PostgresDatabase;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class StatusHandler extends AbstractHandler {

    private final Server server;

    @Deprecated
    private final MCStats mcstats;

    public StatusHandler(MCStats mcstats, Server server) {
        this.mcstats = mcstats;
        this.server = server;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        baseRequest.setHandled(true);
        JSONObject responseJson = new JSONObject();

        {
            JSONObject connections = new JSONObject();

            connections.put("open", server.openConnections());

            responseJson.put("connections", connections);
        }

        {
            JSONObject requests = new JSONObject();

            // requests.put("total", mcstats.getRequestsMade());
            requests.put("perSecond", mcstats.getRequestCalculatorAllTime().calculateRequestsPerSecond());
            // requests.put("processingTime", mcstats.getRequestProcessingTimeAverage().getAverage());

            responseJson.put("requests", requests);
        }

        {
            JSONObject pluginCaches = new JSONObject();

            // requests.put("total", mcstats.getRequestsMade());
            pluginCaches.put("plugins", mcstats.getCachedPlugins().size());
            pluginCaches.put("servers", mcstats.getCachedServers().size());
            pluginCaches.put("recentServers", mcstats.countRecentServers());
            // requests.put("processingTime", mcstats.getRequestProcessingTimeAverage().getAverage());

            responseJson.put("pluginCaches", pluginCaches);
        }

        {
            JSONObject queues = new JSONObject();

            queues.put("sql", mcstats.getDatabaseQueue().size());
            // queues.put("requestProcessor", requestProcessor.size());

            responseJson.put("queues", queues);
        }

        {
            JSONObject queries = new JSONObject();

            queries.put("sql", PostgresDatabase.QUERIES); // TODO fieldify

            responseJson.put("queries", queries);
        }

        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(responseJson.toJSONString());
    }

}