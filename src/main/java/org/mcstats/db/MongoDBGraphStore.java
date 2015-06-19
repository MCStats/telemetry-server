package org.mcstats.db;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.mcstats.generator.GeneratedData;
import org.mcstats.handler.ReportHandler;
import org.mcstats.model.Plugin;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static com.mongodb.client.model.Filters.eq;

@Singleton
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

    @Inject
    public MongoDBGraphStore(@Named("mongo.host") String hostname,
                             @Named("mongo.port") int port,
                             @Named("mongo.db") String database,
                             @Named("mongo.collection") String graphDataCollectionName) {
        client = new MongoClient(hostname, port);

        client.setWriteConcern(WriteConcern.UNACKNOWLEDGED);

        db = client.getDatabase(database);
        graphDataCollection = db.getCollection(graphDataCollectionName);
        collStatistic = db.getCollection("statistic");

        logger.info("Connected to MongoDB");
    }

    /**
     * Finish graph generation
     */
    public void finishGeneration() {
        collStatistic.updateMany(eq("_id", 1), new Document("$set", new Document("max.epoch", ReportHandler.normalizeTime())));
    }

    @Override
    public void insert(Map<Plugin, Map<Integer, List<GeneratedData>>> data, int epoch) {
        List<Document> documentsToInsert = new ArrayList<>();

        data.forEach((plugin, pluginData) -> {
            pluginData.forEach((graphId, graphData) -> {
                documentsToInsert.add(createInsertDocument(plugin, graphId, graphData, epoch));
            });
        });

        graphDataCollection.insertMany(documentsToInsert);
    }

    /**
     * Creates the document to insert for the given data
     *
     * @param plugin
     * @param graphId
     * @param columnData
     * @param epoch
     * @return
     */
    private Document createInsertDocument(Plugin plugin, int graphId, List<GeneratedData> columnData, int epoch) {
        Document document = new Document().append("epoch", epoch).append("plugin", plugin.getId()).append("graph", graphId);
        List<BasicDBObject> data = new ArrayList<>();

        for (GeneratedData generatedData : columnData) {
            BasicDBObject col = new BasicDBObject();
            col.append("name", generatedData.getColumnName());

            int sum = generatedData.getSum();
            int count = generatedData.getCount();

            if (sum != 0) {
                col.append("sum", sum);
            }

            if (count != 0) {
                col.append("count", count);
            }

            data.add(col);
        }

        document.append("data", data);
        return document;
    }

}
