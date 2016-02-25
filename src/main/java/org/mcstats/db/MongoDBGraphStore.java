package org.mcstats.db;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;
import org.mcstats.MCStats;
import org.mcstats.generator.GeneratedData;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Plugin;
import org.mcstats.util.Tuple;

import java.io.IOException;
import java.util.List;
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

    public void batchInsert(Plugin plugin, String graphName, List<Tuple<String, GeneratedData>> batchData, int epoch) {
        // logger.info(String.format("batchInsert(%s, %d, %d, %d, %d, %d, %d)", column.toString(), epoch, sum, count, avg, max, min));

        BasicDBObject toInsert = new BasicDBObject()
                .append("epoch", epoch)
                .append("plugin", plugin.getId())
                .append("graph", graphName);

        BasicDBList data = new BasicDBList();

        for (Tuple<String, GeneratedData> tuple : batchData) {
            BasicDBObject columnObject = new BasicDBObject();
            String columnName = tuple.first();
            GeneratedData gdata = tuple.second();

            int sum = gdata.getSum();
            int count = gdata.getCount();
            int avg = gdata.getAverage();
            int max = gdata.getMax();
            int min = gdata.getMin();

            columnObject.append("name", columnName);

            if (sum != 0) {
                columnObject.append("sum", sum);
            }

            if (count != 0) {
                columnObject.append("count", count);
            }

            data.add(columnObject);
        }

        toInsert.append("data", data);

        coll.insert(toInsert);
    }
}
