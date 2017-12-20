package com.tr.cdb.procedurecall.handler;


import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import com.tr.cdb.procedurecall.service.JdbcVerticle;
import com.tr.cdb.procedurecall.service.UserAttributesVerticle;
import com.tr.cdb.procedurecall.spring.Runner;
import io.netty.handler.codec.http.HttpResponseStatus;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.rxjava.core.eventbus.EventBus;

import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.ext.web.RoutingContext;
import net.spy.memcached.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by artur on 17/12/15.
 */
public class DataRequestHandler implements Handler<RoutingContext> {
    private static final Logger logger = LoggerFactory.getLogger(DataRequestHandler.class);

    private EventBus eventBus;

    public DataRequestHandler(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    private MetricRegistry metrics = SharedMetricRegistries.getOrCreate(Runner.METRICS);
    private final Timer timer = metrics.timer("DataRequestHandler.time");

    @Override
    public void handle(RoutingContext routingContext) {
        Timer.Context timerContext = timer.time();
        try {
            String procedureName = routingContext.request().getParam("procedureName");
            String parameters = extractParametersAndPadWithHyphens(routingContext);
            String uuid=routingContext.request().getHeader(UserAttributesVerticle.REUTERS_UUID);
            logger.info("check cookie: " + routingContext.request().getHeader("Cookie") + " and check reuters uuid " + uuid);
            
           if (UserAttributesVerticle.isLocal ) {
               logger.info("Application running in local, check if uuid is supplied");

               if(uuid==null || uuid.isEmpty()) {
                   logger.info("No UUID is supplied, run testing for local.");
                   procedureCall(routingContext, procedureName, parameters);
               }
               else
               {
                   logger.info("We have UUID, run test for AAA");
                   doProcedureCallWithAAA(routingContext, procedureName, parameters);
               }
            }
            else {
               logger.info("Application running in remote, check permission with AAA.");
                doProcedureCallWithAAA(routingContext, procedureName, parameters);
            }

        } catch (Exception ex) {
            logger.error(ex.toString());
            throw new RuntimeException(ex.getMessage());
        }
        timerContext.stop();
    }

    /**
     * Escape and pad parameter values with hyphens.
     * TODO: Add more safety here.
     * @param routingContext context
     * @return query parameter string
     */
    protected String extractParametersAndPadWithHyphens(RoutingContext routingContext) {
        MultiMap multiMap = routingContext.request().params().remove("procedureName").getDelegate();
        
        List<String> parlist_t= multiMap.entries().stream().map(Map.Entry::getValue).collect(Collectors.toList());
        String parlist= "'" + StringUtils.join(parlist_t, "','") + "'";

        return parlist;

    }

    protected void procedureCall(RoutingContext routingContext, String procedureName, String args) {
        final HttpServerResponse response = routingContext.response();
        response.putHeader("content-type", "application/json");
        eventBus.send(JdbcVerticle.JDBCSERVICE_PROCEDURECALL,
                new JsonObject().put("procedureName", procedureName).put("args", args),
                (replyHandler) -> {
                    if (replyHandler.succeeded()) {
                        response.end(((JsonArray)replyHandler.result().body()).encode());
                    } else {
                        logger.info("----------- FAILED!!!!!!!!!");
                        response.setStatusCode(404);
                        response.end(replyHandler.cause().getMessage());
                    }
                });
    }

    protected void doProcedureCallWithAAA(RoutingContext routingContext,String procedureName, String args) {
        try {


            String cookie = routingContext.request().getHeader("Cookie");
            String uuid=routingContext.request().getHeader( UserAttributesVerticle.REUTERS_UUID);

            logger.info("logging UUID: "+uuid+":"+" and cookie: "+cookie);
            JsonObject req =new JsonObject().put("cookie",cookie).put("uuid",uuid).put("serviceName",procedureName);

            final HttpServerResponse response = routingContext.response();
            eventBus.send(UserAttributesVerticle.USERATTR_SERVICE_ADDRESS, req,  (replyHandler) -> {

                if (((Integer)replyHandler.result().body())==HttpResponseStatus.OK.code()) {
                    procedureCall(routingContext,procedureName,args);
                } else {
                    // sent error
                    response.end(uuid+" do not have the permission to access procedure: "+procedureName+", error code: "+replyHandler.result().body().toString());
                }
            });
        }
        catch (Exception ex)
        {
            logger.error(ex.toString());
            throw new RuntimeException(ex.getMessage());
        }
    }



}
