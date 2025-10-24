pipeline {
  agent any

  options {
    timestamps()
  }

  triggers {
    githubPush()
  }

  environment {
    REGISTRY        = 'docker.io'
    REGISTRY_NS     = 'rojassluu'
    IMAGE_TAG       = 'latest'            // o "${BUILD_NUMBER}" o un SHA corto (ver nota abajo)
    DOCKER_CREDS_ID = 'dockerhub-creds'   // ID en Jenkins > Credentials
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Docker login') {
      steps {
        withCredentials([usernamePassword(
          credentialsId: "${DOCKER_CREDS_ID}",
          usernameVariable: 'DOCKER_USER',
          passwordVariable: 'DOCKER_PASS'
        )]) {
          sh '''
            set -eux
            echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
          '''
        }
      }
    }

    stage('Build images') {
      steps {
      container('kaniko') {
        sh """
            set -eux
            /kaniko/executor \
              --verbosity=debug \
              --context="gateway-service" \
              --dockerfile="gateway-service/Dockerfile" \
              --destination ${REGISTRY}/${REGISTRY_NS}/gateway-service:${IMAGE_TAG} \
              --destination ${REGISTRY}/${REGISTRY_NS}/gateway-service:latest \
              --cache=true
        """
         }
      }
    }
}