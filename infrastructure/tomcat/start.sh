#!/bin/bash

# SAMPLE TOMCAT START SCRIPT
# THIS SCRIPT SHOULD WORK ON ALL DEBIAN-BASED LINUX DISTRIBUTIONS (DEBIAN, UBUNTU, MINT, ETC)
# FOR OTHER OPERATING SYSTEMS, YOU WILL NEED TO MODIFY THIS FILE

# REPLACE <tomcatbase>, <tomcathome> with appropriate entries for your installation

sudo date
output=`sudo service mysql start`

if [[ "$output" = *start/running* ]]
then
	echo "waiting for mysql to start"
	sleep 10
fi

export CATALINA_BASE='<tomcatbase>'
export CATALINA_HOME='<tomcathome>
export JAVA_OPTS="-Xms128m -Xmx1500m -DnoMinHeap"
export CATALINA_OPTS="-Dorg.newsclub.net.unix.library.path=<tomcathome>/junixsocket-1.3/lib-native -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=8999 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false"

sh $CATALINA_HOME/bin/catalina.sh jpda start