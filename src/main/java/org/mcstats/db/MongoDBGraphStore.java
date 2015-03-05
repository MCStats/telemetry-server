package org.mcstats.db;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;
import org.mcstats.MCStats;
import org.mcstats.generator.GeneratedData;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
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
    private DBCollection graphDataCollection;

    /**
     * The collection new graphdata is stored in
     */
    private DBCollection graphDataCollectionNew;

    /**
     * The statistic collection
     */
    private DBCollection collStatistic;

    public MongoDBGraphStore(MCStats mcstats) {
        try {
            client = new MongoClient(mcstats.getConfig().getProperty("mongo.host"));

            client.setWriteConcern(WriteConcern.UNACKNOWLEDGED);

            db = client.getDB(mcstats.getConfig().getProperty("mongo.db"));
            graphDataCollection = db.getCollection(mcstats.getConfig().getProperty("mongo.collection"));
            graphDataCollectionNew = db.getCollection(mcstats.getConfig().getProperty("mongo.collection") + "new");
            collStatistic = db.getCollection("statistic");

            logger.info("Connected to MongoDB");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the collection graph data is stored in
     *
     * @return
     */
    public DBCollection getGraphDataCollection() {
        return graphDataCollection;
    }

    /**
     * Returns the collection graph data is stored in
     *
     * @return
     */
    public DBCollection getGraphDataNewCollection() {
        return graphDataCollectionNew;
    }

    /**
     * Finish graph generation
     */
    public void finishGeneration() {
        DBObject query = new BasicDBObject().append("_id", 1);
        DBObject op = new BasicDBObject().append("$set", new BasicDBObject("max.epoch", ReportHandler.normalizeTime()));

        collStatistic.update(query, op, true, false);
    }

    public void insert(Column column, int epoch, int sum, int count, int avg, int max, int min) {
        Graph graph = column.getGraph();
        Plugin plugin = column.getGraph().getPlugin();

        // logger.info(String.format("insert(%s, %d, %d, %d, %d, %d, %d)", column.toString(), epoch, sum, count, avg, max, min));

        BasicDBObject toset = new BasicDBObject().append("epoch", epoch).append("plugin", plugin.getId()).append("graph", graph.getId());
        BasicDBObject data = new BasicDBObject();
        BasicDBObject col = new BasicDBObject();

        if (sum != 0) {
            col.append("sum", sum);
        }

        if (count != 0) {
            col.append("count", count);
        }

        /*
        if (avg != 0) {
            col.append("avg", avg);
        }

        if (max != 0) {
            col.append("max", max);
        }

        if (min != 0) {
            col.append("min", min);
        }
        */

        data.append(Integer.toString(column.getId()), col);
        toset.append("data", data);

        graphDataCollection.insert(toset);
    }

    public void batchInsert(Graph graph, List<Tuple<Column, GeneratedData>> batchData, int epoch) {
        Plugin plugin = graph.getPlugin();

        // logger.info(String.format("batchInsert(%s, %d, %d, %d, %d, %d, %d)", column.toString(), epoch, sum, count, avg, max, min));

        BasicDBObject toset = new BasicDBObject().append("epoch", epoch).append("plugin", plugin.getId()).append("graph", graph.getId());
        BasicDBObject data = new BasicDBObject();

        for (Tuple<Column, GeneratedData> tuple : batchData) {
            BasicDBObject col = new BasicDBObject();
            Column column = tuple.first();
            GeneratedData gdata = tuple.second();

            int sum = gdata.getSum();
            int count = gdata.getCount();
            int avg = gdata.getAverage();
            int max = gdata.getMax();
            int min = gdata.getMin();

            if (sum != 0) {
                col.append("sum", sum);
            }

            if (count != 0) {
                col.append("count", count);
            }

            /*
            if (avg != 0) {
                col.append("avg", avg);
            }

            if (max != 0) {
                col.append("max", max);
            }

            if (min != 0) {
                col.append("min", min);
            }
            */

            data.append(Integer.toString(column.getId()), col);
        }

        toset.append("data", data);

        graphDataCollection.insert(toset);
    }
}
