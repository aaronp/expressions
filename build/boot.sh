#!/usr/bin/env sh

LOGBACK_LOCATION=${LOGBACK_LOCATION:-/app/config/logback.xml}

# we initialise JVM_ARGS against 'JVM' so the docker container can easily '-e JVM=...'
JVM_ARGS="$JVM $JVM_ARGS"
JVM_ARGS="$JVM_ARGS -server"
JVM_ARGS="$JVM_ARGS -XX:MaxMetaspaceSize=256m"
JVM_ARGS="$JVM_ARGS -Xmn100m"
JVM_ARGS="$JVM_ARGS -XX:SurvivorRatio=6"
#JVM_ARGS="$JVM_ARGS -XX:StartFlightRecording=duration=2h,dumponexit=true,maxage=1d,maxsize=2g,delay=10s,filename=/app/jfr/app.jfr"
#JVM_ARGS="$JVM_ARGS -XX:+FlightRecorder"
#JVM_ARGS="$JVM_ARGS -XX:+CMSParallelRemarkEnabled"
JVM_ARGS="$JVM_ARGS -verbose:gc -Xlog:gc:/app/logs/gc.log"
JVM_ARGS="$JVM_ARGS -Dsun.net.inetaddr.ttl=3600"
JVM_ARGS="$JVM_ARGS -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/heapdump/dump.hprof"
JVM_ARGS="$JVM_ARGS -Dcom.sun.management.jmxremote.authenticate=false"
JVM_ARGS="$JVM_ARGS -Dcom.sun.management.jmxremote.ssl=false"
JVM_ARGS="$JVM_ARGS -Djava.security.egd=file:/dev/./urandom"
JVM_ARGS="$JVM_ARGS -Dlogback.configurationFile=$LOGBACK_LOCATION"

if [[ -n "${CLASSPATH}" ]]; then
  echo "CP is set to ${CLASSPATH}"
  CLASSPATH="/app/config:/app/lib/app.jar:${CLASSPATH}"
else
  echo "CP is not set: >${CLASSPATH}<"
  CLASSPATH="/app/config:/app/lib/app.jar"
fi

echo "Starting w/ JVM_ARGS=$JVM_ARGS CLASSPATH=${CLASSPATH} with $# args $@" && cat /build.txt

java ${JVM_ARGS} -cp ${CLASSPATH} expressions.rest.Main $@