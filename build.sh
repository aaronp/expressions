#!/usr/bin/env bash

GITSHA=`git rev-parse HEAD`
TIMESTAMP=`date -u +'%Y-%m-%dT%H:%M:%SZ'`
IMAGE=nimbleapproach/expressions
VERSION=`cat version.sbt | sed -e 's/.*"\(.*\)"/\1/g'`
TAG="$VERSION"

echo "Building $IMAGE:$TAG w/ "
echo "  GITSHA: ${GITSHA}"
echo "  TIMESTAMP: ${TIMESTAMP}"

sbt assembleApp && VERSION=`cat ./target/docker/version.txt` && docker build . --build-arg GITSHA="$GITSHA" --build-arg VERSION="$VERSION" --build-arg TIMESTAMP="$TIMESTAMP" -t "$IMAGE:$TAG"