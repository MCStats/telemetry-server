package org.mcstats;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.log4j.BasicConfigurator;
import org.mcstats.guice.GuiceModule;

public class Main {

    public static void main(String[] args) {
        BasicConfigurator.configure();

        Injector injector = Guice.createInjector(new GuiceModule());
        Server server = injector.getInstance(Server.class);

        server.start();
    }

}
