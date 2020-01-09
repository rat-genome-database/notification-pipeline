#!/bin/bash

. /etc/profile

APP_HOME=/home/rgddata/pipelines/notificationPipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

cd $APP_HOME

if [ "$SERVER" == "REED" ]; then
  ./_run.sh lomu
else
  # dev server: run on dev database and send all notifications to mtutaj@mcw.edu
  ./_run.sh default_db  debug=mtutaj@mcw.edu
fi



