package org.mcstats.db;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;
import org.mcstats.MCStats;
import org.mcstats.generator.Datum;
import org.mcstats.jetty.PluginTelemetryHandler;
import org.mcstats.model.Plugin;

import java.io.IOException;
import java.util.Map;
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
        DBObject op = new BasicDBObject().append("$set", new BasicDBObject("max.epoch", PluginTelemetryHandler.normalizeTime()));

        collStatistic.update(query, op, true, false);
    }

    public void batchInsert(Plugin plugin, String graphName, Map<String, Datum> data, int epoch) {
        BasicDBObject toInsert = new BasicDBObject()
                .append("epoch", epoch)
                .append("plugin", plugin.getId())
                .append("graph", graphName);

        BasicDBList mongoData = new BasicDBList();

        data.forEach((columnName, datum) -> {
            long sum = datum.getSum();
            long count = datum.getCount();

            BasicDBObject columnObject = new BasicDBObject();

            columnObject.append("name", columnName);
            columnObject.append("sum", sum);
            columnObject.append("count", count);

            mongoData.add(columnObject);
        });

        toInsert.append("data", data);

        coll.insert(toInsert);
    }
}
