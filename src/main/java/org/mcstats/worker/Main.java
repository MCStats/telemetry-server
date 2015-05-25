package org.mcstats.worker;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mcstats.guice.GuiceModule;

public class Main {

    public static void main(String[] args) {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);

        Injector injector = Guice.createInjector(new GuiceModule());
        SQSWorker worker = injector.getInstance(SQSWorker.class);

        worker.start();
    }

}
