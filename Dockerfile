FROM adoptopenjdk/openjdk11:x86_64-alpine-jre-11.0.9_11

COPY target/*.jar /app/lib/app.jar
ADD build/boot.sh /app/boot.sh
ADD build/userConf.conf /app/data/userConf.conf
ADD build/application.conf /app/config/application.conf
ADD build/logback.xml /app/config/logback.xml
ADD build/jmx_config.yaml /app/config/jmx_config.yaml
RUN wget -nv -P jmx https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.14.0/jmx_prometheus_javaagent-0.14.0.jar

WORKDIR /app
RUN chmod 700 /app/boot.sh
ENTRYPOINT ["/app/boot.sh"]