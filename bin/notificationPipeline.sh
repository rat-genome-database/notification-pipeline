#!/bin/bash

. /etc/profile

APP_HOME=/home/rgddata/pipelines/notificationPipeline
PROPS_HOME=/home/rgddata/pipelines/properties

java -Dlocal.config=$PROPS_HOME/notification.properties -Dspring.config=$PROPS_HOME/lomu.xml -Dlog4j.configuration=file:$APP_HOME/properties/log4j.properties -jar $APP_HOME/lib/notificationPipeline.jar


