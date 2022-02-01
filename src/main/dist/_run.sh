#!/usr/bin/env bash
# parameter 1: name of db config file
# parameter 2 (optional): debug=..., where ... is an email account to which all notifications should be sent
#                         f.e. debug=mtutaj@mcw.edu
. /etc/profile
APPNAME=notificationPipeline
APPDIR=/home/rgddata/pipelines/$APPNAME
PROPS_HOME=/home/rgddata/pipelines/properties

cd $APPDIR

echo "   running on database [$1]"

java -Dspring.config=$APPDIR/../properties/$1.xml \
    -Dlog4j.configurationFile=file://$APPDIR/properties/log4j2.xml \
    -Dlocal.config=${PROPS_HOME}/notification.properties \
    -jar lib/$APPNAME.jar "$@"
