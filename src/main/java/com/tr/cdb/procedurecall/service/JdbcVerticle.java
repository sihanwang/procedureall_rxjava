package com.tr.cdb.procedurecall.service;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import javax.sql.DataSource;
import com.tr.cdb.procedurecall.spring.Runner;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.ext.jdbc.JDBCClient;
import io.vertx.rxjava.ext.sql.SQLRowStream;
import io.vertx.rxjava.ext.sql.SQLConnection;
import rx.Observable;
import rx.Single;
import oracle.sql.CLOB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * Created by artur on 16/12/15.
 */
public class JdbcVerticle extends AbstractVerticle {

	private static final Logger log = LoggerFactory.getLogger(JdbcVerticle.class);

	public static final String JDBCSERVICE_PROCEDURELIST = "jdbc.procedure.list.call";
	public static final String JDBCSERVICE_PROCEDUREARGS = "jdbc.procedure.args.call";
	public static final String JDBCSERVICE_PROCEDURECALL = "jdbc.procedure.call";
	public static final String JDBCSERVICE_PERMISSIONCALL = "jdbc.procedure.permission.call";

	private static final Integer LIST_REFRESH_INTERVAL_MILIS = 300000;

	private Map<String, String> serviceProcedure = new HashMap<>();
	private Map<String, List<JsonObject>> serviceArgs = new HashMap<>();

	private MetricRegistry metrics = SharedMetricRegistries.getOrCreate(Runner.METRICS);

	private final DataSource dataSource;
	private JDBCClient rxclient;

	public JdbcVerticle(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void start() throws Exception {
		super.start();
		io.vertx.ext.jdbc.JDBCClient client = io.vertx.ext.jdbc.JDBCClient.create(vertx.getDelegate(), dataSource);
		rxclient = JDBCClient.newInstance(client);

		Observable.interval(0, LIST_REFRESH_INTERVAL_MILIS, TimeUnit.MILLISECONDS).forEach(ln -> {

			log.info("Refreshing service list");
			String sqlQuery = "select * from table(PKG_DOMAIN_TARGET.get_entity_service())";

			Single<SQLConnection> connection = rxclient.rxGetConnection();

			Map<String, String> temp_serviceProcedure = new HashMap<>();
			Map<String, List<JsonObject>> temp_serviceArgs = new HashMap<>();

			connection.flatMapObservable(conn -> conn.rxQueryStream(sqlQuery).flatMapObservable(sqlrowstream -> {
				Observable<JsonArray> ja = sqlrowstream.toObservable();
				int pos_service_name = sqlrowstream.column("SERVICE_NAME");
				int pos_procedure_name = sqlrowstream.column("PROCEDURE_NAME");
				return ja.map(jsonarray -> {
					JsonObject service_procedure = new JsonObject();
					service_procedure.put("SERVICE_NAME", jsonarray.getString(pos_service_name));
					service_procedure.put("PROCEDURE_NAME", jsonarray.getString(pos_procedure_name));
					return service_procedure;
				});
			}).doAfterTerminate(() -> {
				log.info("Disconnecting database connection");
				conn.close();
				log.info("Disconnected database connection");
			})).subscribe(jason -> {
				log.info("Retrieved service name: " + jason.encode());

				String ServiceName = jason.getString("SERVICE_NAME");
				List<JsonObject> procedureArgs = temp_serviceArgs.get(ServiceName);
				if (procedureArgs == null) {
					procedureArgs = new ArrayList<>();
					temp_serviceArgs.put(ServiceName, procedureArgs);
					temp_serviceProcedure.put(ServiceName, jason.getString("PROCEDURE_NAME"));
				}
				procedureArgs.add(jason);
			},

					error -> {
						log.error(error.getMessage());
					},

					() -> {
						serviceProcedure = temp_serviceProcedure;
						serviceArgs = temp_serviceArgs;
						log.info("Refreshed service procedure mapping");
					});
		});

		startHandler();

	}

	private void startHandler() {

		vertx.eventBus().<JsonObject>consumer(JDBCSERVICE_PROCEDURECALL).toObservable().subscribe(event -> {

			String procedureName = event.body().getString("procedureName");
			String args = event.body().getString("args");
			log.info("handling jdbc call event={};{}", procedureName, args);

			if (!serviceProcedure.containsKey(procedureName)) {
				event.fail(404, "Service not found");
			}
			Long startTime = System.currentTimeMillis();

			String sqlQuery = String.format("select * from table(%s(%s))", serviceProcedure.get(procedureName), args);

			io.vertx.ext.jdbc.JDBCClient client = io.vertx.ext.jdbc.JDBCClient.create(vertx.getDelegate(), dataSource);
			rxclient = JDBCClient.newInstance(client);

			Single<SQLConnection> connection = rxclient.rxGetConnection();

			JsonArray response = new JsonArray();

			connection.flatMapObservable(conn -> conn.rxQueryStream(sqlQuery).flatMapObservable(sqlrowstream -> {
				Observable<JsonArray> ja = sqlrowstream.toObservable();

				HashMap<String, Integer> column_index_mapping = new HashMap<String, Integer>();
				sqlrowstream.columns().forEach(column -> {
					column_index_mapping.put(column, sqlrowstream.column(column));
				});

				return ja.map(jsonarray -> {

					JsonObject row_returned = new JsonObject();

					column_index_mapping.forEach((key, value) -> {
						row_returned.put(key, jsonarray.getValue(value));
					});

					return row_returned;
				});

			}).doAfterTerminate(() -> {
				log.info("Disconnecting database connection");
				conn.close();
				log.info("Disconnected database connection");
			}))

					.subscribe(jason -> {
						response.add(jason);
					},

							error -> {
								log.warn("Could not execute query because of: " + error.getMessage());
								event.fail(404, "Could not execute query because of: " + error.getMessage());
							},

							() -> {
								event.reply(response);
								log.info("Finished query on {} with args {} in {}ms", procedureName, args,
										System.currentTimeMillis() - startTime);
							});

		});

		vertx.eventBus().<JsonObject>consumer(JDBCSERVICE_PROCEDUREARGS).toObservable().subscribe(event -> {
			String procedureName = event.body().getString("procedureName");
			log.info("handling {} call event", JDBCSERVICE_PROCEDUREARGS);
			JsonArray response = new JsonArray();
			List<JsonObject> args = serviceArgs.get(procedureName);
			if (args != null) {
				args.forEach(response::add);
				event.reply(response);
			} else {
				event.fail(404, "Service name: " + procedureName + " doesn't exist");
			}

		});

		vertx.eventBus().<JsonObject>consumer(JDBCSERVICE_PROCEDURELIST).toObservable().subscribe(event -> {
			log.info("handling {} call event", JDBCSERVICE_PROCEDURELIST);
			JsonArray response = new JsonArray();
			serviceArgs.forEach((key, value) -> response.add(key).add(value));
			event.reply(response);
		});

	}

}
