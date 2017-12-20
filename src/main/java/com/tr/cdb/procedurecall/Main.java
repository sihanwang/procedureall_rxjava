package com.tr.cdb.procedurecall;

import com.pointcarbon.esb.bootstrap.AppContextController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.System.setProperty;

/**
 * Main class used to start the application.
 * It calls {@link AppContextController} proxy to perform both actions.
 */
public class Main {

    private Main (){

    }

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    static {
        setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");
    }

    public static void main(String[] args) throws Exception {


        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            if(e instanceof OutOfMemoryError) {
                try {
                    logger.error("Ran out of memory, exiting", e);
                }
                finally {
                    System.exit(1);
                }
            }
            else {
                logger.error("Uncaught exception for thread " + thread, e);
            }
        });

        AppContextController.loadAndStartContext();
    }

}
