package com.tr.cdb.procedurecall.integration;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Snapshot;
import com.jayway.restassured.RestAssured;
import com.pointcarbon.esb.bootstrap.AppContextController;
import com.tr.cdb.procedurecall.service.JdbcVerticle;
import com.tr.cdb.procedurecall.spring.Runner;
import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import io.vertx.core.Vertx;
import static java.lang.System.setProperty;


import static com.jayway.restassured.RestAssured.*;

/**
 * Created by artur on 07/01/16.
 *
 * Starts the service and connects to Beta-DB (configurable in {@code config-loadIT.properties}).
 * Runs INVOCATION_COUNT number of requests in THREAD_POOL_SIZE parallel threads.
 * Prints out execution metrics.
 *
 */

@Test
@ContextConfiguration("classpath:applicationContext-loadIT.xml")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ProcedureCallServiceLoadIT extends AbstractTestNGSpringContextTests {
    private static final Logger log = LoggerFactory.getLogger(ProcedureCallServiceLoadIT.class);

    private final static String TEST_JDBC_VERTICLE_CTX = "TEST_JDBC_VERTICLE_CTX";
    // Should match existing procedure name defined in DB. To select one check '/cdb/procedurecall-service/inventory'.
    private final static String TEST_PROCEDURE_NAME = "plant_status";
    // Should be valid parameters values. To select check '/cdb/procedurecall-service/meta/{procedure-name}'.
    private final static String TEST_PROCEDURE_ARGS = "?asset_ric=C}PX7309411329,C}RW7309411333";

    private final static String TEST_COOKIE = "iPlanetDirectoryPro=AQIC5wM2LY4Sfczc%2FWzUlha3MBUafhD3HUHrLif%2Ba5zyj9A%3D%40AAJTSQACMTAAAlNLABI1NjE0NTk0NTk4OTU4MjcyMjAAAlMxAAIwOA%3D%3D%23";
    private final static String TEST_UUID = "reutersuuid";
    private final static String TEST_UUID_VALUE = "PAXTRA6403";


    // How many times service should be called
    private final static int INVOCATION_COUNT = 1;
    // How many parallel requests
    private final static int THREAD_POOL_SIZE = 5;

    private @Value("${http.port:9092}") int httPort;


    private EventBus eventBus;

    private MetricRegistry metrics = SharedMetricRegistries.getOrCreate(Runner.METRICS);

    Long startTime;

    /* Make sure we will auto-tag Oracle connections. Only then will UcpDataSource log used connection details.
    * Useful to verify that all connections from the pool are used.
    */
    {
        setProperty("app.name", "cdb-procedurecall-service-test");
        setProperty("app.version", "1.0.0");
    }

    @BeforeClass
    public void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = Integer.getInteger("http.port", httPort);


        // Wait for JdbcVerticle to load the procedures list from DB.
        try {
            Thread.currentThread().sleep(30000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        startTime = System.currentTimeMillis();
    }

    @AfterClass
    public static void unconfigureRestAssured() {
        RestAssured.reset();
    }

    @AfterTest(groups = { TEST_JDBC_VERTICLE_CTX })
    public void displayMetrics() {
        long totalTime  =  System.currentTimeMillis()-startTime;
        Snapshot plantStatusTimer = metrics.timer("JdbcVerticle."+TEST_PROCEDURE_NAME).getSnapshot();
        log.info("-- JdbcVerticle.{} execution stats: --", TEST_PROCEDURE_NAME);
        log.info("-- total time={} ms.", totalTime);
        log.info("-- calls count={}", metrics.timer("JdbcVerticle." + TEST_PROCEDURE_NAME).getCount());
        log.info("-- clients count={}", THREAD_POOL_SIZE);
        log.info("-- mean={} ms.", plantStatusTimer.getMean()/1000000);
        log.info("-- median={} ms.", plantStatusTimer.getMedian()/1000000);
        log.info("-- max={} ms.", plantStatusTimer.getMax()/1000000);
        log.info("-- min={} ms.", plantStatusTimer.getMin() / 1000000);
        log.info("-- stdDev={}", plantStatusTimer.getStdDev());
    }

    @Test(threadPoolSize = THREAD_POOL_SIZE, invocationCount = INVOCATION_COUNT, groups = { TEST_JDBC_VERTICLE_CTX })
    public void callServiceWithPermission() {
        log.info("Test on permission procedure call data handler");
        given().cookie(TEST_COOKIE)
                .headers(TEST_UUID,TEST_UUID_VALUE)
                .when()
        .get("/cdb/procedurecall-service/data/" + TEST_PROCEDURE_NAME + TEST_PROCEDURE_ARGS)

                .then()
                .assertThat()
                .statusCode(200);
        log.info("Finish testing on permission procedure call data handler");
    }

    @Test(threadPoolSize = THREAD_POOL_SIZE, invocationCount = INVOCATION_COUNT, groups = { TEST_JDBC_VERTICLE_CTX })
    public void callService() {
        log.info("Test on normal procedure call data handler");
                get("/cdb/procedurecall-service/data/" + TEST_PROCEDURE_NAME + TEST_PROCEDURE_ARGS)
                .then()
                .assertThat()
                .statusCode(200);
        log.info("Finish testing on normal procedure call data handler");
    }

    @Test(threadPoolSize = THREAD_POOL_SIZE, invocationCount = INVOCATION_COUNT, groups = { TEST_JDBC_VERTICLE_CTX })
    public void testInventoryHandler()
    {
        log.info("Test on inventory data handler");
        get("/cdb/procedurecall-service/inventory")
                .then()
                .assertThat()
                .statusCode(200);
        log.info("Finish testing on inventory data handler");
    }

    @Test(threadPoolSize = THREAD_POOL_SIZE, invocationCount = INVOCATION_COUNT, groups = { TEST_JDBC_VERTICLE_CTX })
    public void testMetaHandler()
    {
        log.info("Test on meta data handler");
        get("/cdb/procedurecall-service/meta/"+TEST_PROCEDURE_NAME)
                .then()
                .assertThat()
                .statusCode(200);
        log.info("Finish testing on meta data handler");
    }



}
