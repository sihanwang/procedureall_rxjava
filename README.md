# CDB::PROCEDURECALL::SERVICE project

How to run
==========

Execute the `Main.main()`.

Release Workflow
================

Release uses cdb-rpm-maven-plugin and should be part of delivery pipeline defined in Jenkins.

Documentation
================

Open a browser and point to http://localhost:9092/cdb/procedurecall-service/doc/?raml=api/rest.raml  (check port - `{http.port}` - if this does not work)
Make sure to specify the `hostUrl` value accordingly to the runtime environment.

Monitoring
================

* Open a browser and point to http://localhost:`{http.port}`/cdb/procedurecall-service/status
* Open JConsole at localhost:`{jmx.remoteport}`

Both - `{http.port}` and `{jmx.remoteport}` are set in `config/${PC_ENVIRONMENT:local}-${flavor:development}/config.properties`.

Integration test
================

In order to run integration tests invoke `mvn clean test failsafe:integration-test`.

* `ProcedureCallServiceLoadIT` - Starts the service and connects to Beta-DB. 
Runs `INVOCATION_COUNT` number of requests in `THREAD_POOL_SIZE` parallel threads. Prints out execution metrics.
