FROM openjdk:8

MAINTAINER NewMotion, ti@newmotion.com

ADD target /app
ADD examples /app/examples

WORKDIR /app

ENV CHARGEPOINT_SERIAL=56565656
ENV BACKOFFICE_URL=wss://test-chargenetwork.thenewmotion.com/ocppws/
ENV SCRIPT=examples/ocpp1x/continuous-remote-listening.scala

CMD ["sh", "-c", "java -jar scala-2.12/docile.jar -c $CHARGEPOINT_SERIAL --forever $BACKOFFICE_URL  $SCRIPT"]

