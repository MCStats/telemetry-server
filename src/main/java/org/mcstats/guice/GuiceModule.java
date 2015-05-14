package org.mcstats.guice;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import org.mcstats.db.Database;
import org.mcstats.db.GraphStore;
import org.mcstats.db.ModelCache;
import org.mcstats.db.MongoDBGraphStore;
import org.mcstats.db.MySQLDatabase;
import org.mcstats.db.RedisCache;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class GuiceModule extends AbstractModule {

    @Override
    protected void configure() {
        Names.bindProperties(binder(), loadProperties());

        // bind(MCStats.class);

        bind(ModelCache.class).to(RedisCache.class);
        bind(Database.class).to(MySQLDatabase.class);
        bind(GraphStore.class).to(MongoDBGraphStore.class);
    }

    private Properties loadProperties() {
        Properties properties = new Properties();

        try (FileReader reader = new FileReader("mcstats.properties")) {
            properties.load(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return properties;
    }

}
