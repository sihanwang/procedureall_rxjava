package com.tr.cdb.procedurecall.service;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import com.pointcarbon.esb.transport.oracle.datasource.UcpDataSource;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.Cache;
import com.tr.cdb.procedurecall.spring.Runner;
import io.vertx.core.eventbus.EventBus;
import io.vertx.rxjava.core.http.HttpClient;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.ext.jdbc.JDBCClient;
import rx.Observable;
import rx.Single;
import io.vertx.rxjava.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.Handler;
import io.vertx.rxjava.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.AbstractVerticle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static java.lang.Boolean.parseBoolean;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static net.logstash.logback.marker.Markers.append;

/**
 * Created by u0122150 on 1/7/2016.
 */
public class UserAttributesVerticle extends AbstractVerticle {

	private static final Logger logger = LoggerFactory.getLogger(UserAttributesVerticle.class);

	public static final String REUTERS_UUID = "reutersuuid";
	private static final String REQUEST_PATH = "/userattributesservice/userattribute";
	public static final String USERATTR_SERVICE_ADDRESS = "UserAttributes.request";
	private static final Integer LIST_REFRESH_INTERVAL_MILIS = 300000;
	private static final Integer TIMEOUT = 3000;
	private String aaaWebserviceAddress;
	public static Boolean isLocal;
	private final UcpDataSource dataSource;
	private JDBCClient rxclient;
	private MetricRegistry metrics = SharedMetricRegistries.getOrCreate(Runner.METRICS);
	private static final Cache<String, Object> cache = CacheBuilder.newBuilder().recordStats().maximumSize(1000)
			.expireAfterAccess(24, TimeUnit.HOURS).build();

	private static final Logger log = LoggerFactory.getLogger(UserAttributesVerticle.class);

	public UserAttributesVerticle(String AAAaddress, Boolean isLocal, UcpDataSource dataSource) {
		this.dataSource = dataSource;
		this.aaaWebserviceAddress = AAAaddress;
		this.isLocal = isLocal;
	}

	@Override
	public void start() throws Exception {
		super.start();
		
		io.vertx.ext.jdbc.JDBCClient client = io.vertx.ext.jdbc.JDBCClient.create(vertx.getDelegate(), dataSource);
		rxclient = JDBCClient.newInstance(client);
		
		Observable.interval(0, LIST_REFRESH_INTERVAL_MILIS, TimeUnit.MILLISECONDS).forEach(ln -> {
			
			logger.info("Refreshing permission list");
			
			String sqlQuery = "select po_code, service_name from table(pkg_domain_target.get_entity_service_po_code)";
			Single<SQLConnection> connection = rxclient.rxGetConnection();

			connection.flatMapObservable(conn -> conn.rxQueryStream(sqlQuery).flatMapObservable(sqlrowstream -> {
				Observable<JsonArray> ja = sqlrowstream.toObservable();
				int pos_po_code = sqlrowstream.column("PO_CODE");
				int pos_service_name = sqlrowstream.column("SERVICE_NAME");
				return ja.map(jsonarray -> {
					JsonObject service_procedure = new JsonObject();
					service_procedure.put("PO_CODE", jsonarray.getString(pos_po_code));
					service_procedure.put("SERVICE_NAME", jsonarray.getString(pos_service_name));
					return service_procedure;
				});
			})
			.doAfterTerminate(() -> {
				log.info("Disconnecting database connection");
				conn.close();
				log.info("Disconnected database connection");
			}))
			.subscribe(
					jason -> {
						log.info("Retrieved permission item: "+jason.encode());
						
						String ServiceName=jason.getString("SERVICE_NAME");
						String POCode=jason.getString("PO_CODE");
						cache.put(ServiceName, POCode);
					}, 
					
					error -> {
						log.error("Could not refresh permission list because of: "+error.getMessage());
						
					},

					() -> {

						logger.info("Refreshed permission list");
						
					}
			);
		});
		
		
		vertx.eventBus().<JsonObject>consumer(USERATTR_SERVICE_ADDRESS).toObservable().subscribe(message -> {
				Long startTime = System.currentTimeMillis();
				JsonObject json = message.body();
				String uuid = json.getString("uuid");
				String cookie = json.getString("cookie");
				String serviceName = json.getString("serviceName");
				Timer.Context timer = metrics.timer("UserAttributesVerticle." + serviceName).time();
				try {
					Integer statusCode = (Integer) cache.getIfPresent(uuid + serviceName);
					if (statusCode != null) {
						logger.info("We have a cache hit for uuid {} with serviceName {}", uuid, serviceName);
						message.reply(statusCode);
					} else {
						logger.info("We have a cache miss for uuid {} with serviceName {}", uuid, serviceName);
						Long startTimeBeforeAAACall = System.currentTimeMillis();
						log.info("This is to log before AAA call in userAttributeService: " + startTimeBeforeAAACall);
						HttpClientRequest request = authorizeRequest(message, json);
						Long timeTakenExecutingAAACall = System.currentTimeMillis() - startTimeBeforeAAACall;
						log.info("This is to log after AAA Call: " + timeTakenExecutingAAACall);
						request.setTimeout(TIMEOUT);
						request.exceptionHandler(throwable -> {
							logger.warn("Exception when calling AAA web service {}, returning status code {}",
									throwable, HttpResponseStatus.UNAUTHORIZED.code());
							message.reply(HttpResponseStatus.UNAUTHORIZED.code());
						});
						addUUID(request, uuid);
						addCookie(request, cookie);
						request.end();
					}

					logger.info("UserAttributesVerticle started");
				} finally {
					log.info("Finished AAA call on {} with UUID {} in {}ms", serviceName, uuid,
							System.currentTimeMillis() - startTime);

					long queryTime = System.currentTimeMillis() - startTime;
					log.info(
							append("queryTime", queryTime)
									.and(append("uuid", uuid).and(append("serviceName", serviceName))),
							"Finished AAA call  {} in {}ms", kv("uuid", uuid), kv("querytime", queryTime));

					timer.stop();

				}
			}

		);
	}

	public HttpClientRequest authorizeRequest(Message<JsonObject> message, JsonObject json) {

		HttpClient httpClient = vertx.createHttpClient();

		logger.info("Calling AAA on address:" + aaaWebserviceAddress + REQUEST_PATH + "/?ids="
				+ cache.getIfPresent(json.getString("serviceName")).toString());
		
		return httpClient.get(aaaWebserviceAddress,
				REQUEST_PATH + "/?ids=" + cache.getIfPresent(json.getString("serviceName")).toString(), resp -> {
					resp.bodyHandler(buffer -> {
						JsonObject response = new JsonObject(buffer.getString(0, buffer.length()));
						JsonObject attributes = response.getJsonArray("userAttributeList").getJsonObject(0);
						String name = attributes.getString("Name");
						Boolean isAuthorized = parseBoolean(attributes.getString("Value"));
						message.reply(
								isAuthorized ? HttpResponseStatus.OK.code() : HttpResponseStatus.UNAUTHORIZED.code());
						String uuid = json.getString("uuid");
						String serviceName = json.getString("serviceName");
						cache.put(uuid + serviceName,
								isAuthorized ? HttpResponseStatus.OK.code() : HttpResponseStatus.UNAUTHORIZED.code());
						logger.info("{} " + (isAuthorized ? "authorized" : "not authorized")
								+ " to access procedure with id {}", name, serviceName);
					});
				});
	}

	public static void addCookie(HttpClientRequest httpRequest, String cookie) {
		if (cookie != null && !cookie.isEmpty()) {
			logger.debug("adding cookie {} to request", cookie);
			httpRequest.putHeader("Cookie", cookie);
		}
	}

	public static void addUUID(HttpClientRequest httpRequest, String uuid) {
		if (uuid != null && !uuid.isEmpty()) {
			logger.debug("adding uuid {} to request", uuid);
			httpRequest.putHeader(REUTERS_UUID, uuid);
		}
	}

}
