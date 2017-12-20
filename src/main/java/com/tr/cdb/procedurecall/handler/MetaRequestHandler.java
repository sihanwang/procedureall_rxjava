package com.tr.cdb.procedurecall.handler;

import com.tr.cdb.procedurecall.service.JdbcVerticle;
import io.vertx.core.Handler;
import io.vertx.rxjava.core.eventbus.EventBus;

import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.ext.web.RoutingContext;

/**
 *
 */
public class MetaRequestHandler implements Handler<RoutingContext> {

    private EventBus eventBus;

    public MetaRequestHandler(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        String procedureName = routingContext.request().getParam("procedureName");
        final HttpServerResponse response = routingContext.response();
        
        eventBus.send(JdbcVerticle.JDBCSERVICE_PROCEDUREARGS, new JsonObject().put("procedureName", procedureName), 
        		
        		(replyHandler) -> {
            if (replyHandler.succeeded()) {
                response.putHeader("content-type", "application/json");
                response.end(((JsonArray)replyHandler.result().body()).encode());
            } else {

                response.end(replyHandler.cause().getMessage());
            }
        });
        
    }

}

