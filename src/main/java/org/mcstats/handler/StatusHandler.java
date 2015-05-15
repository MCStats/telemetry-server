package org.mcstats.handler;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.json.simple.JSONObject;
import org.mcstats.DatabaseQueue;
import org.mcstats.MCStats;
import org.mcstats.db.MySQLDatabase;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Singleton
public class StatusHandler extends AbstractHandler {

    private final MCStats mcstats;
    private final ReportHandler reportHandler;
    private final DatabaseQueue databaseQueue;

    @Inject
    public StatusHandler(MCStats mcstats, ReportHandler reportHandler, DatabaseQueue databaseQueue) {
        this.mcstats = mcstats;
        this.reportHandler = reportHandler;
        this.databaseQueue = databaseQueue;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (!shouldHandle(request)) {
            return;
        }

        baseRequest.setHandled(true);
        JSONObject responseJson = new JSONObject();

        {
            JSONObject requests = new JSONObject();

            requests.put("total", mcstats.getRequestsMade());
            requests.put("perSecond", mcstats.getRequestsAverage().getAverage());
            requests.put("processingTime", mcstats.getRequestProcessingTimeAverage().getAverage());

            responseJson.put("requests", requests);
        }

        {
            JSONObject queues = new JSONObject();

            queues.put("sql", databaseQueue.size());
            queues.put("threadPool", reportHandler.getExecutorQueueSize());

            responseJson.put("queues", queues);
        }

        {
            JSONObject queries = new JSONObject();

            // TODO redis?
            queries.put("sql", MySQLDatabase.QUERIES); // TODO fieldify

            responseJson.put("queries", queries);
        }

        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(responseJson.toJSONString());
    }

    private boolean shouldHandle(HttpServletRequest request) {
        String requestURI = request.getRequestURI();

        return requestURI.equals("/status");
    }

}
