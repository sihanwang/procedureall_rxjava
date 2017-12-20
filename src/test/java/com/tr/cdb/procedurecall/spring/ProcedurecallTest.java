package com.tr.cdb.procedurecall.spring;


import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.jayway.restassured.RestAssured;
import com.pointcarbon.esb.transport.oracle.datasource.UcpDataSource;
import com.tr.cdb.procedurecall.service.JdbcVerticle;
import com.tr.cdb.procedurecall.service.ServerVerticle;
import com.tr.cdb.procedurecall.service.UserAttributesVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.impl.MessageImpl;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestOptions;
import io.vertx.ext.unit.TestSuite;
import io.vertx.ext.unit.report.ReportOptions;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import io.vertx.core.Vertx;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;



@Test
@ContextConfiguration("classpath:applicationContext-loadIT.xml")
public class ProcedurecallTest extends AbstractTestNGSpringContextTests {
    private static final Logger logger = LoggerFactory.getLogger(ProcedurecallTest.class);
    private @Value("${http.port:9092}") int httPort;
    private @Autowired
    MetricRegistry metricRegistry;
    private @Autowired HealthCheckRegistry healthCheckRegistry;
    private Vertx vertx;
    private EventBus eventBus;
    private @Autowired UcpDataSource dataSource;
    private final static String TEST_PROCEDURE_NAME = "plant_status";
    private final static String UNIT_TEST_PROCEDURE_ARGS = "'asset_ric=C}PX7309411329,C}RW7309411333'";
    private @Value("${aaa.webservice.host:}") String aaaWebserviceHost;
    private @Value("${islocal:}") Boolean isLocal;
    private final static String TEST_COOKIE = "iPlanetDirectoryPro=AQIC5wM2LY4Sfczc%2FWzUlha3MBUafhD3HUHrLif%2Ba5zyj9A%3D%40AAJTSQACMTAAAlNLABI1NjE0NTk0NTk4OTU4MjcyMjAAAlMxAAIwOA%3D%3D%23";
    private final static String TEST_UUID_VALUE = "PAXTRA6403";
    private  HttpClientRequest request;
    private Message<JsonObject> message;

        @Test
        public  void testDeploymentVertx(){
            logger.info("Start testing on deployment of verticles!");
            vertx=Vertx.vertx();
            TestSuite testSuite=TestSuite.create("Deployment_test_suite");
            testSuite.test("JDBC_Deployment_test", context -> {

                Async async = context.async();

                vertx.deployVerticle(new JdbcVerticle(dataSource), ar -> {
                    logger.info("JdbcVerticle test in progress!");
                    if (ar.succeeded()) {
                        logger.info("JdbcVerticle test succeed!");
                        async.complete();
                    } else {
                        logger.info("JdbcVerticle test failed!");
                        context.fail(ar.cause());
                    }
                });

                logger.info("JDBC_Deployment_test complete!");
            });

            testSuite.test("Server_Deployment_test", context -> {

                Async async = context.async();

                vertx.deployVerticle(new ServerVerticle(httPort, metricRegistry, healthCheckRegistry), ar -> {
                    logger.info("ServerVerticle test in progress!");

                    if (ar.succeeded()) {
                        logger.info("ServerVerticle test succeed!");
                        async.complete();
                    } else {
                        logger.info("ServerVerticle test failed!");
                        context.fail(ar.cause());
                    }
                });

                logger.info("Server_Deployment_test complete!");
            });

            testSuite.test("UserAttribute_Deployment_test", context -> {

                Async async = context.async();

                vertx.deployVerticle(new UserAttributesVerticle(aaaWebserviceHost, isLocal,dataSource), ar -> {
                    logger.info("ServerVerticle test in progress!");
                    if (ar.succeeded()) {
                        logger.info("ServerVerticle test succeed!");
                        async.complete();
                    } else {
                        logger.info("ServerVerticle test failed!");
                        context.fail(ar.cause());
                    }
                });

                logger.info("UserAttribute_Deployment_test complete!");
            });

            testSuite.run(new TestOptions().addReporter(new ReportOptions().setTo("console")));

        }



    @Test
    public void testEventBus()
    {


        logger.info("Start testing on units!");
        vertx=Vertx.vertx();

        TestSuite testSuite=TestSuite.create("units_test_suite");
        testSuite.before(context -> {

            logger.info("units_test_suite test suite setup");

            DeploymentOptions options = new DeploymentOptions()
                    .setConfig(new JsonObject().put("http.port", httPort)
                    );

            vertx.deployVerticle(new JdbcVerticle(dataSource), options, context.asyncAssertSuccess());
            vertx.deployVerticle(new UserAttributesVerticle(aaaWebserviceHost, isLocal, dataSource), options, context.asyncAssertSuccess());
            logger.info("Wait for JdbcVerticle to load the procedures list from DB.");
            try {
                Thread.currentThread().sleep(30000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).test("JDBCSERVICE_PROCEDUREARGS_test", context -> {

            logger.info("JDBCSERVICE_PROCEDUREARGS_test testing");

            vertx.eventBus().send(JdbcVerticle.JDBCSERVICE_PROCEDUREARGS
                    , new JsonObject().put("procedureName", TEST_PROCEDURE_NAME),
                    (AsyncResult<Message<JsonArray>> replyHandler) -> {
                        context.assertEquals(true, replyHandler.succeeded());
                        logger.info("JDBCSERVICE_PROCEDUREARGS_test succeed!");
                    });


            logger.info("JDBCSERVICE_PROCEDUREARGS_test complete!");

        }).test("JDBCSERVICE_PROCEDURECALL_test", context -> {


            logger.info("JDBCSERVICE_PROCEDURECALL_test testing");

            vertx.eventBus().send(JdbcVerticle.JDBCSERVICE_PROCEDURECALL
                    , new JsonObject().put("procedureName", TEST_PROCEDURE_NAME).put("args", UNIT_TEST_PROCEDURE_ARGS),
                    (AsyncResult<Message<JsonArray>> replyHandler) -> {
                        context.assertEquals(true, replyHandler.succeeded());
                    });


            logger.info("JDBCSERVICE_PROCEDURECALL_test complete!");


        }).test("USERATTR_SERVICE_ADDRESS_test", context -> {


            logger.info("USERATTR_SERVICE_ADDRESS_test testing");
            JsonObject req = new JsonObject().put("cookie", TEST_COOKIE).put("uuid", TEST_UUID_VALUE).put("serviceName", TEST_PROCEDURE_NAME);
            vertx.eventBus().send(UserAttributesVerticle.USERATTR_SERVICE_ADDRESS, req, (AsyncResult<Message<Integer>> replyHandler) -> {
                logger.info("replyHandler=" + replyHandler);
                if (replyHandler.succeeded()) {

                    logger.info("USERATTR_SERVICE_ADDRESS_test succeed!");
                } else {

                    logger.info("USERATTR_SERVICE_ADDRESS_test failed! " + replyHandler.cause().getMessage());

                }

            });


            logger.info("USERATTR_SERVICE_ADDRESS_test complete!");

        }).after(context -> {
                vertx.close();
                logger.info("units_test_suite cleanup");

            });

            testSuite.run(new TestOptions().addReporter(new ReportOptions().setTo("console")));

        }


    }
