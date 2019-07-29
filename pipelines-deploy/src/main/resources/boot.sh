#!/usr/bin/env bash

export JVM_ARGS="-server"
export JVM_ARGS="$JVM_ARGS -Xms1g -Xmx1g"
export JVM_ARGS="$JVM_ARGS -XX:MaxMetaspaceSize=256m"
export JVM_ARGS="$JVM_ARGS -Xmn100m"
export JVM_ARGS="$JVM_ARGS -XX:SurvivorRatio=6"
export JVM_ARGS="$JVM_ARGS -XX:StartFlightRecording=duration=2h,dumponexit=true,maxage=1d,maxsize=2g,delay=10s,filename=/app/jfr/pipelines.jfr"
export JVM_ARGS="$JVM_ARGS -XX:+FlightRecorder"
export JVM_ARGS="$JVM_ARGS -XX:+CMSParallelRemarkEnabled"
#export JVM_ARGS="$JVM_ARGS -XX:+PrintGCDateStamps "
export JVM_ARGS="$JVM_ARGS -verbose:gc -Xlog:gc:/app/logs/gc.log"
#export JVM_ARGS="$JVM_ARGS -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=10 -XX:GCLogFileSize=100M"
export JVM_ARGS="$JVM_ARGS -Dsun.net.inetaddr.ttl=3600"
export JVM_ARGS="$JVM_ARGS -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/heapdump/dump.hprof"
#export JVM_ARGS="$JVM_ARGS -Djava.rmi.server.hostname=$IPADDR"
#export JVM_ARGS="$JVM_ARGS -Dcom.sun.management.jmxremote.port=$JMX_PORT"
export JVM_ARGS="$JVM_ARGS -Dcom.sun.management.jmxremote.authenticate=false"
export JVM_ARGS="$JVM_ARGS -Dcom.sun.management.jmxremote.ssl=false"
export JVM_ARGS="$JVM_ARGS -Dlogback.configurationFile=/app/config/logback.xml"

echo "Starting w/ $JVM_ARGS on $IPADDR with $# args $@ (first is '$1')"

java ${JVM_ARGS} -cp /app/config:/app/lib/app.jar pipelines.server.PipelinesMain $@ pipelines-docker.conf
