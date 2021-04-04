FROM adoptopenjdk/openjdk11:x86_64-alpine-jre-11.0.9_11
#FROM gcr.io/distroless/java:11

ARG GITSHA
ARG TIMESTAMP

LABEL org.opencontainers.image.title="Expressions"
LABEL org.opencontainers.image.authors="Aaron Pritzlaff"
LABEL org.opencontainers.image.created="${TIMESTAMP}"
LABEL org.opencontainers.image.source="https://github.com/aaronp/expressions"
LABEL org.opencontainers.image.url="https://github.com/aaronp/expressions"
LABEL org.opencontainers.image.version="${GITSHA}"
LABEL commit="${GITSHA}"

COPY target/docker/app.jar /app/lib/app.jar

# this is for the 'basic' UI:
#COPY target/docker/www /app/www
#COPY target/docker/ui /app/ui

COPY ui/build/web /app/www

ADD build/boot.sh /app/boot.sh
ADD build/userConf.conf /app/data/userConf.conf
ADD build/application.conf /app/config/application.conf
ADD build/logback.xml /app/config/logback.xml

RUN echo " +------------------------------------------------------------------------" > /build.txt && \
    echo " + Built at: $TIMESTAMP " >> /build.txt && \
    echo " +  Version: $VERSION " >> /build.txt && \
    echo " +   Commit: $GITSHA " >> /build.txt && \
    echo " +------------------------------------------------------------------------" >> /build.txt

RUN mkdir /app/logs && \
    mkdir /app/jfr && \
    mkdir /app/www/css && \
    mkdir /app/www/js

EXPOSE 8080/tcp
EXPOSE 9090/tcp

WORKDIR /app
RUN chmod 700 /app/boot.sh
ENTRYPOINT ["/app/boot.sh"]