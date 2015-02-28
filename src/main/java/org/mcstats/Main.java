package org.mcstats;

import org.apache.log4j.BasicConfigurator;

public class Main {

    public static void main(String[] args) {
        // log4j
        BasicConfigurator.configure();

        // start
        MCStats.getInstance().init();
        MCStats.getInstance().createWebServer();
    }

}
