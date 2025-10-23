pipeline {
  agent any

  options {
    timestamps()
  }

  triggers {
    // Usa webhook de GitHub/GitLab si puedes:
    githubPush()            // -> GitHub
    // gitlabPush()         // -> GitLab (si usas GitLab)
    // Como respaldo (si no hay webhook), descomenta un poll cada 2 min:
    // pollSCM('H/2 * * * *')
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
        // Si tu job es "Pipeline from SCM", Jenkins hace checkout solo.
        // Lo dejo para jobs tipo "Pipeline script".
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
        sh """
          set -eux
          docker build -t ${REGISTRY}/${REGISTRY_NS}/gateway-service:${IMAGE_TAG}   ./gateway-service
          docker build -t ${REGISTRY}/${REGISTRY_NS}/pedidos-service:${IMAGE_TAG}   ./pedidos-service
          docker build -t ${REGISTRY}/${REGISTRY_NS}/usuarios-service:${IMAGE_TAG}  ./usuarios-service
        """
      }
    }

    stage('Push images') {
      steps {
        sh """
          set -eux
          docker push ${REGISTRY}/${REGISTRY_NS}/gateway-service:${IMAGE_TAG}
          docker push ${REGISTRY}/${REGISTRY_NS}/pedidos-service:${IMAGE_TAG}
          docker push ${REGISTRY}/${REGISTRY_NS}/usuarios-service:${IMAGE_TAG}
        """
      }
    }
  }

  post {
    always {
      sh 'docker logout || true'
    }
    success {
      echo "OK: pushed tag ${IMAGE_TAG} a ${REGISTRY_NS}"
    }
    failure {
      echo "Falló el pipeline (revisa la etapa en rojo)."
    }
  }
}
