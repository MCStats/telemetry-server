package org.mcstats.guice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.mcstats.db.Database;
import org.mcstats.db.GraphStore;
import org.mcstats.db.ModelCache;
import org.mcstats.db.MongoDBGraphStore;
import org.mcstats.db.MySQLDatabase;
import org.mcstats.db.RedisCache;
import redis.clients.jedis.JedisPool;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class GuiceModule extends AbstractModule {

    @Override
    protected void configure() {
        Names.bindProperties(binder(), loadProperties());

        bind(Gson.class).toProvider(GsonProvider.class).in(Scopes.SINGLETON);

        bind(ModelCache.class).to(RedisCache.class);
        bind(Database.class).to(MySQLDatabase.class);
        bind(GraphStore.class).to(MongoDBGraphStore.class);
        bind(JedisPool.class).toProvider(JedisPoolProvider.class).in(Scopes.SINGLETON);
    }

    private static class GsonProvider implements Provider<Gson> {

        @Override
        public Gson get() {
            GsonBuilder builder = new GsonBuilder();
            return builder.create();
        }

    }

    private static class JedisPoolProvider implements Provider<JedisPool> {

        private final String hostname;
        private final int port;

        @Inject
        public JedisPoolProvider(@Named("redis.host") String hostname, @Named("redis.port") int port) {
            this.hostname = hostname;
            this.port = port;
        }

        @Override
        public JedisPool get() {
            GenericObjectPoolConfig config = new GenericObjectPoolConfig();
            config.setMaxTotal(64); // TODO config ?

            return new JedisPool(config, hostname, port);
        }

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
