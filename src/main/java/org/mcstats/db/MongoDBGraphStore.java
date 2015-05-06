package org.mcstats.db;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.mcstats.MCStats;
import org.mcstats.generator.GeneratedData;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Column;
import org.mcstats.model.Graph;
import org.mcstats.model.Plugin;
import org.mcstats.util.Tuple;

import java.util.List;
import java.util.logging.Logger;

import static com.mongodb.client.model.Filters.eq;

public class MongoDBGraphStore implements GraphStore {

    private Logger logger = Logger.getLogger("MongoDB");

    /**
     * The mongo client
     */
    private MongoClient client;

    /**
     * The database used to store data
     */
    private MongoDatabase db;

    /**
     * The collection graphdata is stored in
     */
    private MongoCollection<Document> graphDataCollection;

    /**
     * The statistic collection
     */
    private MongoCollection<Document> collStatistic;

    public MongoDBGraphStore(MCStats mcstats) {
        client = new MongoClient(mcstats.getConfig().getProperty("mongo.host"), Integer.parseInt(mcstats.getConfig().getProperty("mongo.port")));

        client.setWriteConcern(WriteConcern.UNACKNOWLEDGED);

        db = client.getDatabase(mcstats.getConfig().getProperty("mongo.db"));
        graphDataCollection = db.getCollection(mcstats.getConfig().getProperty("mongo.collection"));
        collStatistic = db.getCollection("statistic");

        logger.info("Connected to MongoDB");
    }

    /**
     * Finish graph generation
     */
    public void finishGeneration() {
        collStatistic.updateMany(eq("_id", 1), new Document("$set", new Document("max.epoch", ReportHandler.normalizeTime())));
    }

    public void insert(Column column, int epoch, int sum, int count, int avg, int max, int min) {
        Graph graph = column.getGraph();
        Plugin plugin = column.getGraph().getPlugin();

        // logger.info(String.format("insert(%s, %d, %d, %d, %d, %d, %d)", column.toString(), epoch, sum, count, avg, max, min));

        Document toset = new Document().append("epoch", epoch).append("plugin", plugin.getId()).append("graph", graph.getId());
        Document data = new Document();
        Document col = new Document();

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

        graphDataCollection.insertOne(toset);
    }

    public void batchInsert(Graph graph, List<Tuple<Column, GeneratedData>> batchData, int epoch) {
        Plugin plugin = graph.getPlugin();

        // logger.info(String.format("batchInsert(%s, %d, %d, %d, %d, %d, %d)", column.toString(), epoch, sum, count, avg, max, min));

        Document toset = new Document().append("epoch", epoch).append("plugin", plugin.getId()).append("graph", graph.getId());
        Document data = new Document();

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

        graphDataCollection.insertOne(toset);
    }
}
