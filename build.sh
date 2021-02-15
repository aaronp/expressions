#!/usr/bin/env bash

GITSHA=`git rev-parse HEAD`
TIMESTAMP=`date -u +'%Y-%m-%dT%H:%M:%SZ'`
IMAGE=porpoiseltd/expressions
TAG=latest
echo "Building $IMAGE:$TAG w/ "
echo "  GITSHA: ${GITSHA}"
echo "  TIMESTAMP: ${TIMESTAMP}"

sbt assembleApp && docker build . --build-arg GITSHA="$GITSHA" --build-arg TIMESTAMP="$TIMESTAMP" -t "$IMAGE:$TAG"