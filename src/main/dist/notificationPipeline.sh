#!/bin/bash

. /etc/profile

APP_HOME=/home/rgddata/pipelines/notification-pipeline
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

cd $APP_HOME

if [ "$SERVER" == "REED" ]; then
  ./_run.sh culber2 "$@"
else
  # dev server: run on dev database and send all notifications to mtutaj@mcw.edu
  ./_run.sh default_db2  debug=mtutaj@mcw.edu "$@"
fi


