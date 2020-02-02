#!/usr/bin/env bash

set -e

IMAGE_NAME=${IMAGE_NAME:-aliz/nexus3}
IMAGE_TAG=${IMAGE_TAG:-${BUILD_NUMBER:-$(eval date +%s)}}

DOCKER_REPOSITORY=${DOCKER_REPOSITORY}
DEPLOYMENT_NAME=${DEPLOYMENT_NAME:nexus-deployment}

build() {
  echo "Building Nexus3 ${IMAGE_NAME}:${IMAGE_TAG}"
  eval "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."

  docker_build_status=$?

  if [ $docker_build_status -eq 0 ]
  then
    echo 'Build finished'
  else
    echo 'Build Failed' >&2
    exit 1
  fi
}

push() {
  echo "Pushing image to repository ${DOCKER_REPOSITORY}"
  eval "docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${DOCKER_REPOSITORY}:${IMAGE_NAME}:${IMAGE_TAG}"

  eval "docker push ${DOCKER_REPOSITORY}:${IMAGE_NAME}:${IMAGE_TAG}"
  docker_push_status=$?

  if [ $docker_push_status -eq 0 ]
  then
    echo 'Push finished'
  else
    echo 'Push Failed' >&2
    exit 1
  fi
}

update_cluster() {
  echo "Updating kubernetes cluster"

  eval "kubectl --record deployments.apps/${DEPLOYMENT_NAME} set image deployment.v1.apps/${DEPLOYMENT_NAME} nexus=${DOCKER_REPOSITORY}:${IMAGE_NAME}:${IMAGE_TAG}"

  eval "kubectl rollout status deployments.apps/${DEPLOYMENT_NAME}"
}

build
push
update_cluster
