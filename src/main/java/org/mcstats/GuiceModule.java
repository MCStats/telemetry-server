package org.mcstats;

import com.google.inject.AbstractModule;
import org.mcstats.db.ModelCache;
import org.mcstats.db.RedisCache;

public class GuiceModule extends AbstractModule {

    @Override
    protected void configure() {
        // bind(MCStats.class);

        bind(ModelCache.class).to(RedisCache.class);

        // TODO want annotation for fetching config values to remove dependency on
        // mcstats.getConfig(). This will make the database binds trivial.
        // bind(Database.class).to(MySQLDatabase.class);
        // bind(GraphStore.class).to(MongoDBGraphStore.class);
    }

}
