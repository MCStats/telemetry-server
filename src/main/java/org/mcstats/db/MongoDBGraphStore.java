package org.mcstats.db;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;
import org.mcstats.MCStats;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;

import java.io.IOException;
import java.util.logging.Logger;

public class MongoDBGraphStore implements GraphStore {

    private Logger logger = Logger.getLogger("MongoDB");

    /**
     * The mongo client
     */
    private MongoClient client;

    /**
     * The database used to store data
     */
    private DB db;

    /**
     * The collection graphdata is stored in
     */
    private DBCollection coll;

    /**
     * The statistic collection
     */
    private DBCollection collStatistic;

    public MongoDBGraphStore(MCStats mcstats) {
        try {
            client = new MongoClient(mcstats.getConfig().getProperty("mongo.host"));

            client.setWriteConcern(WriteConcern.UNACKNOWLEDGED);

            db = client.getDB(mcstats.getConfig().getProperty("mongo.db"));
            coll = db.getCollection(mcstats.getConfig().getProperty("mongo.collection"));
            collStatistic = db.getCollection("statistic");

            logger.info("Connected to MongoDB");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Finish graph generation
     */
    public void finishGeneration() {
        DBObject query = new BasicDBObject().append("_id", 1);
        DBObject op = new BasicDBObject().append("$set", new BasicDBObject("max.epoch", ReportHandler.normalizeTime()));

        collStatistic.update(query, op, true, false);
    }

    /**
     * {@inheritDoc}
     */
    public void insert(Column column, int epoch, int sum, int count, int avg, int max, int min) {
        Graph graph = column.getGraph();
        Plugin plugin = column.getPlugin();

        // logger.info(String.format("insert(%s, %d, %d, %d, %d, %d, %d)", column.toString(), epoch, sum, count, avg, max, min));

        BasicDBObject toset = new BasicDBObject();

        if (sum != 0) {
            toset.append("data." + column.getId() + ".sum", sum);
        }

        if (count != 0) {
            toset.append("data." + column.getId() + ".count", count);
        }

        if (avg != 0) {
            toset.append("data." + column.getId() + ".avg", avg);
        }

        if (max != 0) {
            toset.append("data." + column.getId() + ".max", max);
        }

        if (min != 0) {
            toset.append("data." + column.getId() + ".min", min);
        }

        DBObject search = new BasicDBObject().append("epoch", epoch).append("plugin", plugin.getId()).append("graph", graph.getId());
        DBObject op = new BasicDBObject().append("$set", toset);
        coll.update(search, op, true /* upsert */, false /* multi */);
    }
}
