package dev.brus.health.service;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import io.vertx.amqp.AmqpClientOptions;
import io.vertx.mutiny.amqp.AmqpClient;
import io.vertx.mutiny.core.Vertx;

@ApplicationScoped
public class HealthClient {

   @Inject
   Vertx vertx;

   @Produces
   @Named("health-amqp-client")
   public AmqpClient getAmqpClient() {
      return AmqpClient.create(vertx, getAmqpClientOptions());
   }

   @Produces
   @Named("health-amqp-client-options")
   public AmqpClientOptions getAmqpClientOptions() {
      return new AmqpClientOptions().setHost("ex-aao-amqp-0-svc-rte-health-monitoring.apps.cluster-b861.b861.sandbox760.opentlc.com")
            .setPort(443).setUsername("admin").setPassword("admin").setSsl(true).setTrustAll(true)
            .setHostnameVerificationAlgorithm("");
   }

}