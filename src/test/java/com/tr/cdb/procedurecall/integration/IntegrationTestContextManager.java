package com.tr.cdb.procedurecall.integration;

import com.tr.cdb.procedurecall.spring.Runner;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.TimeZone;

/**
 * Component responsible for providing proper execution environment for test suite.
 * 
 */
@Component
public class IntegrationTestContextManager {
    private static final Logger log = LoggerFactory.getLogger(IntegrationTestContextManager.class);

    private @Autowired Runner runner;

    @PostConstruct
    public void initSuiteExecEnv() throws Exception {
        log.info("[TEST]: STARTING THE RUNNER SERVICE");
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        DateTimeZone.setDefault(DateTimeZone.forID("UTC"));
        runner.run();
    }
}
