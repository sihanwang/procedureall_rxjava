package com.tr.cdb.procedurecall.spring;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.pointcarbon.esb.bootstrap.service.IFailAwareRunner;
import com.pointcarbon.esb.bootstrap.service.IMainRunner;
import com.pointcarbon.esb.monitoring.service.HealthCheckService;
import com.pointcarbon.esb.transport.oracle.datasource.UcpDataSource;
import com.tr.cdb.procedurecall.service.JdbcVerticle;
import com.tr.cdb.procedurecall.service.ServerVerticle;
import com.tr.cdb.procedurecall.service.UserAttributesVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.rxjava.core.Vertx;

import io.vertx.core.VertxOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import rx.Observable;

import io.vertx.rxjava.core.RxHelper;

import com.tr.cdb.procedurecall.reactive.RxUtil;
/**
 * Main application's Runner service. Implements {@link IMainRunner} called by bootstrap when starting/stopping the app.
 */
@Service
public class Runner implements IFailAwareRunner {
    private static Logger log = LoggerFactory.getLogger(Runner.class);

    private @Autowired HealthCheckService healthCheckService;
    private @Autowired MetricRegistry metricRegistry;
    private @Autowired HealthCheckRegistry healthCheckRegistry;

    private @Autowired UcpDataSource dataSource;


    private @Value("${aaa.webservice.host:}") String aaaWebserviceHost;
    private @Value("${islocal:}") Boolean isLocal;
    private @Value("${http.port:9092}") int httPort;


    public static final String METRICS = "metrics";
    private AtomicBoolean isActive = new AtomicBoolean(true);

    private Vertx vertx = Vertx.vertx(new VertxOptions().setMetricsOptions(
            new DropwizardMetricsOptions().setEnabled(true)
    ).setBlockedThreadCheckInterval(1000*60*60));

    @Override
    public void run() {

        log.info("::: STARTING RUNNER");
        
        
        Observable<Object> Deployments= RxUtil.ConcatVoidProcess(
        		() -> RxHelper.deployVerticle(vertx, new ServerVerticle(httPort, metricRegistry, healthCheckRegistry)),
        		() -> RxHelper.deployVerticle(vertx, new JdbcVerticle(dataSource)),//, options);
        		() -> RxHelper.deployVerticle(vertx, new UserAttributesVerticle(aaaWebserviceHost, isLocal,dataSource))
        		);
        		
        Observable.concat(initHealthService(),Deployments).subscribe(
        		obj -> {},
        		error -> log.info(error.getMessage())
        		);
        
    }

    private Observable<Object> initHealthService() {
    	
    return 	RxUtil.MergeVoidProcesses(
    		() -> SharedMetricRegistries.add(METRICS, metricRegistry),
    		() -> healthCheckService.registerMeter("DequeuedEvents"),
    		() -> healthCheckService.registerGauge("LastProcessedMessage")
    		);
    }

    @Override
    public void shutdown() {
        log.info("::: STOPPING RUNNER");
        //TODO read more how to terminate:
        vertx.close();
    }

    @Override
    public void runInPassiveMode() {
        run();
    }

    @Override
    public void switchToPassiveMode() {
    }

    @Override
    public void switchToActiveMode() {
    }
}
