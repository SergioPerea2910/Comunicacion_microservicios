pipeline {
  agent any

  environment {
    REGISTRY    = 'docker.io'
    REGISTRY_NS = 'rojassluu'
    IMAGE_TAG   = 'latest'        // cámbialo a "${BUILD_NUMBER}" si prefieres
    DOCKER_CREDS_ID = 'dockerhub-creds'
  }

  stages {
    // Si creas el job como "Pipeline script from SCM", Jenkins hace el checkout automáticamente.

    stage('Docker login') {
      steps {
        withCredentials([usernamePassword(
          credentialsId: "${DOCKER_CREDS_ID}",
          usernameVariable: 'DOCKER_USER',
          passwordVariable: 'DOCKER_PASS'
        )]) {
          sh '''
            echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
          '''
        }
      }
    }

    stage('Build images') {
      steps {
        sh """
          docker build -t ${REGISTRY}/${REGISTRY_NS}/gateway-service:${IMAGE_TAG}   ./gateway-service
          docker build -t ${REGISTRY}/${REGISTRY_NS}/pedidos-service:${IMAGE_TAG}   ./pedidos-service
          docker build -t ${REGISTRY}/${REGISTRY_NS}/usuarios-service:${IMAGE_TAG}  ./usuarios-service
        """
      }
    }

    stage('Push images') {
      steps {
        sh """
          docker push ${REGISTRY}/${REGISTRY_NS}/gateway-service:${IMAGE_TAG}
          docker push ${REGISTRY}/${REGISTRY_NS}/pedidos-service:${IMAGE_TAG}
          docker push ${REGISTRY}/${REGISTRY_NS}/usuarios-service:${IMAGE_TAG}
        """
      }
    }
  }

  post {
    success { echo "OK: pushed tag ${IMAGE_TAG} a ${REGISTRY_NS}" }
    failure { echo "Falló el pipeline (revisa la etapa en rojo)." }
  }
}
