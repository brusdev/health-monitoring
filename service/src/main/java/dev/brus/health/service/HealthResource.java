package dev.brus.health.service;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.resteasy.annotations.jaxrs.PathParam;
import org.reactivestreams.Publisher;

import io.smallrye.mutiny.Multi;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.amqp.AmqpClient;
import io.vertx.mutiny.amqp.AmqpMessage;
import io.vertx.mutiny.amqp.AmqpReceiver;

@Path("/health")
public class HealthResource {

    @Inject
    @Named("health-amqp-client")
    AmqpClient amqpClient;

    @Inject
    @Channel("user-health-alert")
    Publisher<JsonObject> healthAlert;

    @Inject
    @Channel("user-health-data")
    Publisher<JsonObject> healthData;

    @GET
    @Path("users/alerts")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Publisher<JsonObject> userHealthAlerts() {
        return healthAlert;
    }

    @GET
    @Path("users/data")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Publisher<JsonObject> userHealthData() {
        return healthData;
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("users/{userId}/alerts")
    public Multi<JsonObject> userHealthAlerts(@PathParam String userId) {
        return amqpClient.createReceiver("users." + userId + ".health.alert").onItem().transform(AmqpReceiver::toMulti)
                .await().indefinitely().map(AmqpMessage::bodyAsJsonObject);
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Path("users/{userId}/data")
    public Multi<JsonObject> userHealthData(@PathParam String userId) {
        return amqpClient.createReceiver("users." + userId + ".health.data").onItem().transform(AmqpReceiver::toMulti)
                .await().indefinitely().map(AmqpMessage::bodyAsJsonObject);
    }
}