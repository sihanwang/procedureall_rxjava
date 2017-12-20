package com.tr.cdb.procedurecall.service;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.tr.cdb.procedurecall.handler.DataRequestHandler;
import com.tr.cdb.procedurecall.handler.InventoryRequestHandler;
import com.tr.cdb.procedurecall.handler.MetaRequestHandler;
import com.tr.healthcheck.sync.StatusHandler;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpServer;

import io.vertx.rxjava.ext.web.Router;
		
		
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import io.vertx.rxjava.ext.web.handler.StaticHandler;
import rx.functions.Action1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(ServerVerticle.class);

    public static final String APP_ROOT = "/cdb/procedurecall-service";

    public final int DEFAULT_HTTP_PORT;

    private final HealthCheckRegistry healthCheckRegistry;
    private final MetricRegistry metricRegistry;

    public ServerVerticle(final int httPort, MetricRegistry metricRegistry, HealthCheckRegistry healthCheckRegistry) {
        this.DEFAULT_HTTP_PORT = httPort;
        this.healthCheckRegistry = healthCheckRegistry;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void start() throws Exception {
        super.start();
        
        Router restApiRouter = Router.router(vertx);
        restApiRouter.route().handler(BodyHandler.create());
        restApiRouter.get("/inventory").handler(new InventoryRequestHandler(vertx.eventBus()));
        restApiRouter.get("/meta/:procedureName").handler(new MetaRequestHandler(vertx.eventBus()));
        restApiRouter.get("/data/:procedureName").handler(new DataRequestHandler(vertx.eventBus()));
        StatusHandler.setupStatusRoutes(metricRegistry, healthCheckRegistry, restApiRouter.getDelegate());

        Router metricsRouter = Router.router(vertx);
        StatusHandler.setupStatusRoutes(metricRegistry, healthCheckRegistry, metricsRouter.getDelegate());

        Router router = Router.router(vertx);
        router.mountSubRouter(APP_ROOT, restApiRouter); // put /status under common root as well.
        router.mountSubRouter(APP_ROOT, metricsRouter); // put /status under common root as well.

        router.route().handler(StaticHandler.create());
        HttpServer server = vertx.createHttpServer();
        
		server.requestHandler(router::accept).rxListen(config().getInteger("port", DEFAULT_HTTP_PORT)).subscribe( httpserver -> {logger.info("Server listening on port " + DEFAULT_HTTP_PORT);});
      
    }

}
