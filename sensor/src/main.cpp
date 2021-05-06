#include <Arduino.h>

//WiFi
#include <DNSServer.h>
#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <WiFiManager.h>

//MQTT
#include <MQTT.h>

//MAX30102
#include <Wire.h>
#include "MAX30105.h"
#include "spo2_algorithm.h"


MQTTClient client;
WiFiClient wifiClient;
WiFiClientSecure wifiClientSecure;

const char user_id[] = "0";
/*
const char mqtt_host[] = "ex-aao-mqtt-0-svc-rte-health-monitoring.apps.cluster-b861.b861.sandbox760.opentlc.com";
const int mqtt_port = 443;
const bool mqtt_secure = true;

const char mqtt_host[] = "test.mosquitto.org";
const int mqtt_port = 8883;
const bool mqtt_secure = true;

const char mqtt_host[] = "192.168.10.16";
const int mqtt_port = 1883;
const bool mqtt_secure = false;
*/
const char mqtt_host[] = "ex-aao-mqtt-0-svc-rte-health-monitoring.apps.cluster-b861.b861.sandbox760.opentlc.com";
const int mqtt_port = 443;
const bool mqtt_secure = true;

const char mqtt_username[] = "admin";
const char mqtt_password[] = "admin";

MAX30105 particleSensor;
#define BUFFER_LENGTH 100
uint32_t irBuffer[BUFFER_LENGTH]; //infrared LED sensor data
uint32_t redBuffer[BUFFER_LENGTH];  //red LED sensor data
int32_t spo2; //SPO2 value
int8_t validSPO2; //indicator to show if the SPO2 calculation is valid
int32_t heartRate; //heart rate value
int8_t validHeartRate; //indicator to show if the heart rate calculation is valid
float temperature; //temparature

char sensorName[64];
char sensorData[64];
char sensorDataAddr[64];
char sensorEventAddr[64];

void client_connect() {
  Serial.print("checking wifi...");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(1000);
  }

  Serial.print("\nconnecting...");
  while (!client.connect(sensorName, mqtt_username, mqtt_password)) {
    Serial.print(".");
    delay(1000);
  }

  Serial.println("\nconnected!");

  sprintf(sensorData, "{\"user\":%s,\"code\":\"0\",\"desc\":\"connected\"}", user_id);
  client.publish(sensorEventAddr, sensorData);
}

void client_loop() {
  client.loop();
  delay(10);  // <- fixes some issues with WiFi stability

  if (!client.connected()) {
    client_connect();
  }
}

void sensor_read() {
  //read the first 100 samples, and determine the signal range
  for (byte i = 0 ; i < BUFFER_LENGTH ; i++)
  {
    while (particleSensor.available() == false) //do we have new data?
      particleSensor.check(); //Check the sensor for new data

    redBuffer[i] = particleSensor.getRed();
    irBuffer[i] = particleSensor.getIR();
    particleSensor.nextSample(); //We're finished with this sample so move to next sample
  }

  //calculate heart rate and SpO2 after first 100 samples (first 4 seconds of samples)
  maxim_heart_rate_and_oxygen_saturation(irBuffer, BUFFER_LENGTH, redBuffer, &spo2, &validSPO2, &heartRate, &validHeartRate);

  //dumping the first 25 sets of samples in the memory and shift the last 75 sets of samples to the top
  for (byte i = 25; i < 100; i++)
  {
    redBuffer[i - 25] = redBuffer[i];
    irBuffer[i - 25] = irBuffer[i];
  }

  //take 25 sets of samples before calculating the heart rate.
  for (byte i = 75; i < 100; i++)
  {
    while (particleSensor.available() == false) //do we have new data?
      particleSensor.check(); //Check the sensor for new data

    redBuffer[i] = particleSensor.getRed();
    irBuffer[i] = particleSensor.getIR();
    particleSensor.nextSample(); //We're finished with this sample so move to next sample
  }

  //after gathering 25 new samples recalculate HR and SP02
  maxim_heart_rate_and_oxygen_saturation(irBuffer, BUFFER_LENGTH, redBuffer, &spo2, &validSPO2, &heartRate, &validHeartRate);

  //read the temperature
  temperature = particleSensor.readTemperature();
}

void setup() {
  //initialize the serial port
  Serial.begin(115200);
  Serial.print("Setup ");

  sprintf(sensorName, "health-sensor-%s", user_id);
  sprintf(sensorDataAddr, "users/%s/sensor/data", user_id);
  sprintf(sensorEventAddr, "users/%s/sensor/event", user_id);
  Serial.println(sensorName);


  //initialize the WiFiManager
  WiFiManager wifiManager;
  wifiManager.autoConnect("health-sensor");
  Serial.println("WiFi connected");


  //initialize the MQTT client
  if (mqtt_secure) {
    wifiClientSecure.setInsecure();
    client.begin(mqtt_host, mqtt_port, wifiClientSecure);
  } else {
    client.begin(mqtt_host, mqtt_port, wifiClient);
  }

  sprintf(sensorData, "{\"user\":%s,\"code\":\"1\",\"desc\":\"disconnected\"}", user_id);
  client.setWill(sensorEventAddr, sensorData);

  client_connect();


  //initialize the sensor
  while (!particleSensor.begin(Wire, I2C_SPEED_FAST)) //Use default I2C port, 400kHz speed
  {
    Serial.println("MAX30105 was not found. Please check wiring/power.");
    delay(1000);
  }

  byte ledBrightness = 60; //Options: 0=Off to 255=50mA
  byte sampleAverage = 4; //Options: 1, 2, 4, 8, 16, 32
  byte ledMode = 2; //Options: 1 = Red only, 2 = Red + IR, 3 = Red + IR + Green
  byte sampleRate = 100; //Options: 50, 100, 200, 400, 800, 1000, 1600, 3200
  int pulseWidth = 411; //Options: 69, 118, 215, 411
  int adcRange = 4096; //Options: 2048, 4096, 8192, 16384

  particleSensor.setup(ledBrightness, sampleAverage, ledMode, sampleRate, pulseWidth, adcRange); //Configure sensor with these settings

  particleSensor.enableDIETEMPRDY(); //Enable the temp ready interrupt. This is required.
}

void loop() {
  Serial.print("loop ");

  client_loop();

  sensor_read();

  if (validHeartRate == 1 && validSPO2 == 1) {
    sprintf(sensorData, "{\"user\":%s,\"heartrate\":%d,\"oxygen\":%d,\"temperature\":%d}", user_id, heartRate, spo2, (int32_t)temperature);
    client.publish(sensorDataAddr, sensorData);

    Serial.println(sensorData);
  }
}

