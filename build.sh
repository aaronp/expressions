#!/usr/bin/env bash

GITSHA=`git rev-parse HEAD`
TIMESTAMP=`date -u +'%Y-%m-%dT%H:%M:%SZ'`
IMAGE=nimbleapproach/expressions
#VERSION=`cat version.sbt | sed -e 's/.*"\(.*\)"/\1/g'`

function about {
  echo "Building $IMAGE:$VERSION w/ "
  echo "     GITSHA: ${GITSHA}"
  echo "  TIMESTAMP: ${TIMESTAMP}"
  echo "    VERSION: ${VERSION}"
}

function dockerBuild {
  echo docker build . --build-arg GITSHA="$GITSHA" --build-arg VERSION="$VERSION" --build-arg TIMESTAMP="$TIMESTAMP" -t "$IMAGE:$VERSION"
       docker build . --build-arg GITSHA="$GITSHA" --build-arg VERSION="$VERSION" --build-arg TIMESTAMP="$TIMESTAMP" -t "$IMAGE:$VERSION"
}

sbt assembleApp && VERSION=`cat ./target/docker/version.txt` && about && dockerBuild