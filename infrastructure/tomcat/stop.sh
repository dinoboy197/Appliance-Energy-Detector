#!/bin/bash

# SAMPLE TOMCAT STOP SCRIPT
# THIS SCRIPT SHOULD WORK ON ALL LINUX DISTRIBUTIONS (DEBIAN, UBUNTU, MINT, REDHAT, FEDORA, ETC)
# FOR OTHER OPERATING SYSTEMS, YOU WILL NEED TO MODIFY THIS FILE

# REPLACE <tomcatbase>, <tomcathome> with appropriate entries for your installation

export CATALINA_BASE='<tomcatbase>'
export CATALINA_HOME='<tomcathome>'

sh $CATALINA_HOME/bin/shutdown.sh