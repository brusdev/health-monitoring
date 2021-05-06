package dev.brus.health.service;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.util.concurrent.CompletionStage;

import io.smallrye.reactive.messaging.amqp.IncomingAmqpMetadata;
import io.smallrye.reactive.messaging.amqp.OutgoingAmqpMetadata;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

@ApplicationScoped
public class HealthProcessor {
   public static final int SCORE_BAD = 0;
   public static final int SCORE_POOR = 1;
   public static final int SCORE_FAIR = 2;
   public static final int SCORE_GOOD = 3;
   public static final int SCORE_EXCELLENT = 4;

   private static final Logger logger = Logger.getLogger(HealthProcessor.class);

   @Inject
   @Channel("user-health-emitter")
   Emitter<JsonObject> healthEmitter;


   @Incoming("user-sensor-data")
   public CompletionStage<Void> handleSensorData(Message<byte[]> message) {
      message.getMetadata(IncomingAmqpMetadata.class).ifPresent(incomingMetadata -> {
         Buffer messageBuffer = Buffer.buffer(message.getPayload());

         JsonObject sensorData = messageBuffer.toJsonObject();
         logger.info("Hi Red Hat Developers");
         logger.info("sensorData " + sensorData);

         double heartrate = sensorData.getDouble("heartrate");
         double oxygen = sensorData.getDouble("oxygen");
         double temperature = sensorData.getDouble("temperature");

         int score = getHealthScore(heartrate, oxygen, temperature);
         JsonObject healthData = sensorData.copy().put("score", score);
         logger.info("healthData " + healthData);

         String userAddress = incomingMetadata.getAddress().substring(0, incomingMetadata.getAddress().indexOf('.', 6));

         healthEmitter.send(Message.of(healthData).addMetadata(
               OutgoingAmqpMetadata.builder().withAddress(userAddress + ".health.data").withDurable(true).build()));

         if (score < SCORE_FAIR) {
            logger.info("healthAlert " + healthData);

            healthEmitter.send(Message.of(healthData).addMetadata(
                  OutgoingAmqpMetadata.builder().withAddress(userAddress + ".health.alert").withDurable(true).build()));
         }
      });

      return message.ack();
   }

   @Incoming("user-sensor-event")
   public CompletionStage<Void> handleSensorEvent(Message<byte[]> message) {
      message.getMetadata(IncomingAmqpMetadata.class).ifPresent(incomingMetadata -> {
         Buffer messageBuffer = Buffer.buffer(message.getPayload());

         JsonObject sensorEvent = messageBuffer.toJsonObject();
         logger.info("sensorEvent " + sensorEvent);

         String code = sensorEvent.getString("code");

         String userAddress = incomingMetadata.getAddress().substring(0, incomingMetadata.getAddress().indexOf('.', 6));

         if (code.equals("1")) {
            logger.info("healthAlert " + sensorEvent);

            healthEmitter.send(Message.of(sensorEvent).addMetadata(
                  OutgoingAmqpMetadata.builder().withAddress(userAddress + ".health.alert").withDurable(true).build()));
         }
      });

      return message.ack();
   }

   private int getHealthScore(double heartrate, double oxygen, double temperature) {
      int healthScore = SCORE_EXCELLENT;

      healthScore = Math.min(healthScore, getHeartrateScore(heartrate));
      healthScore = Math.min(healthScore, getOxygenScore(oxygen));
      healthScore = Math.min(healthScore, getTemperatureScore(temperature));

      return healthScore;
   }

   private int getHeartrateScore(double heartrate) {
      if (heartrate >= 60 && heartrate < 70) {
         return SCORE_EXCELLENT;
      } else if (heartrate >= 55 && heartrate < 60 || heartrate >= 70 && heartrate < 75) {
         return SCORE_GOOD;
      } else if (heartrate >= 50 && heartrate < 55 || heartrate >= 75 && heartrate < 80) {
         return SCORE_FAIR;
      } else if (heartrate >= 45 && heartrate < 50 || heartrate >= 80 && heartrate < 85) {
         return SCORE_POOR;
      } else {
         return SCORE_BAD;
      }
   }

   private int getOxygenScore(double oxygen) {
      if (oxygen >= 97) {
         return SCORE_EXCELLENT;
      } else if (oxygen >= 95) {
         return SCORE_GOOD;
      } else if (oxygen >= 90) {
         return SCORE_FAIR;
      } else if (oxygen >= 85) {
         return SCORE_POOR;
      } else {
         return SCORE_BAD;
      }
   }

   private int getTemperatureScore(double temperature) {
      if (temperature >= 36 && temperature < 37) {
         return SCORE_EXCELLENT;
      } else if (temperature >= 35 && temperature < 36 || temperature >= 37 && temperature < 38) {
         return SCORE_GOOD;
      } else if (temperature >= 34 && temperature < 35 || temperature >= 38 && temperature < 39) {
         return SCORE_FAIR;
      } else if (temperature >= 33 && temperature < 34 || temperature >= 39 && temperature < 40) {
         return SCORE_POOR;
      } else {
         return SCORE_BAD;
      }
   }
}
