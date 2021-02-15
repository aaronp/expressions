#!/usr/bin/env sh

LOGBACK_LOCATION=${LOGBACK_LOCATION:-/app/config/logback.xml}

# we initialise JVM_ARGS against 'JVM' so the docker container can easily '-e JVM=...'
JVM_ARGS="$JVM $JVM_ARGS"
JVM_ARGS="$JVM_ARGS -server"
JVM_ARGS="$JVM_ARGS -XX:MaxMetaspaceSize=256m"
JVM_ARGS="$JVM_ARGS -Xmn100m"
JVM_ARGS="$JVM_ARGS -XX:SurvivorRatio=6"
JVM_ARGS="$JVM_ARGS -XX:StartFlightRecording=duration=2h,dumponexit=true,maxage=1d,maxsize=2g,delay=10s,filename=/app/jfr/app.jfr"
JVM_ARGS="$JVM_ARGS -XX:+FlightRecorder"
JVM_ARGS="$JVM_ARGS -XX:+CMSParallelRemarkEnabled"
JVM_ARGS="$JVM_ARGS -verbose:gc -Xlog:gc:/app/logs/gc.log"
JVM_ARGS="$JVM_ARGS -Dsun.net.inetaddr.ttl=3600"
JVM_ARGS="$JVM_ARGS -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/heapdump/dump.hprof"
JVM_ARGS="$JVM_ARGS -Dcom.sun.management.jmxremote.authenticate=false"
JVM_ARGS="$JVM_ARGS -Dcom.sun.management.jmxremote.ssl=false"
JVM_ARGS="$JVM_ARGS -Djava.security.egd=file:/dev/./urandom"
JVM_ARGS="$JVM_ARGS -Dlogback.configurationFile=$LOGBACK_LOCATION"
#JVM_ARGS="$JVM_ARGS -javaagent:/jmx/jmx_prometheus_javaagent-0.15.0.jar=9090:/app/jmx/jmx_config.yaml"

cat /build.txt
echo "Starting w/ JVM_ARGS=$JVM_ARGS with $# args $@ (first is '$1')"

# userConf.conf is set up empty, but is there for convenience if run with /app/data/ as a mapped drive
java ${JVM_ARGS} -cp /app/config:/app/lib/app.jar expressions.rest.Main  /app/data/userConf.conf $@