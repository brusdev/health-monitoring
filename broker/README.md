# Broker

## Creating the broker key store
keytool -genkey -alias broker -keyalg RSA -keystore broker.ks
keytool -export -alias broker -keystore broker.ks -file broker_cert

## Creating the client trust store
keytool -genkey -alias client -keyalg RSA -keystore client.ks
keytool -import -alias broker -keystore client.ts -file broker_cert

## Creating the secretes for the acceptors
oc create secret generic ex-aao-amqp-secret --from-file=broker.ks --from-file=client.ts
oc create secret generic ex-aao-mqtt-secret --from-file=broker.ks --from-file=client.ts

## Deploying ActiveMQ Artemis
oc create -f artemis-ssl-amqp-mqtt.yaml 
