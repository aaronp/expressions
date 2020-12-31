FROM adoptopenjdk/openjdk11:x86_64-alpine-jre-11.0.9_11
#FROM gcr.io/distroless/java:11

COPY target/docker/app.jar /app/lib/app.jar
COPY target/docker/www /app/www
COPY target/docker/ui /app/ui
ADD build/boot.sh /app/boot.sh
ADD build/userConf.conf /app/data/userConf.conf
ADD build/application.conf /app/config/application.conf
ADD build/logback.xml /app/config/logback.xml
ADD build/jmx_config.yaml /app/config/jmx_config.yaml
#RUN wget -nv -P jmx https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.15.0/jmx_prometheus_javaagent-0.15.0.jar

RUN mkdir /app/logs && \
    mkdir /app/jfr

EXPOSE 8080/tcp
EXPOSE 9090/tcp

WORKDIR /app
RUN chmod 700 /app/boot.sh
ENTRYPOINT ["/app/boot.sh"]