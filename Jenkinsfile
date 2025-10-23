pipeline {
  agent any
  options { timestamps() }

  triggers {
    githubPush()               // disparo por Webhook (tu ngrok)
  }

  environment {
    REGISTRY        = 'docker.io'
    REGISTRY_NS     = 'rojassluu'
    IMAGE_TAG       = 'latest'   // o "${env.GIT_COMMIT.take(7)}"
    DOCKER_CREDS_ID = 'dockerhub-creds'
  }

  stages {
    stage('Checkout') { steps { checkout scm } }

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
        sh '''
          set -eux
          for svc in gateway-service pedidos-service usuarios-service; do
            docker build -t ${REGISTRY}/${REGISTRY_NS}/$svc:${IMAGE_TAG} ./$svc
          done
        '''
      }
    }

    stage('Push images') {
      steps {
        sh '''
          set -eux
          for svc in gateway-service pedidos-service usuarios-service; do
            docker push ${REGISTRY}/${REGISTRY_NS}/$svc:${IMAGE_TAG}
          done
        '''
      }
    }

    // 🔥 Despliegue local en Docker (pull + recreate contenedores)
    stage('Deploy (local Docker)') {
      when { branch 'main' }   // deploy solo en main (ajusta si quieres)
      steps {
        sh '''
          set -eux
          # ejemplo: recrear gateway-service
          docker rm -f gateway || true
          docker pull ${REGISTRY}/${REGISTRY_NS}/gateway-service:${IMAGE_TAG}
          docker run -d --name gateway -p 8083:8080 \
            ${REGISTRY}/${REGISTRY_NS}/gateway-service:${IMAGE_TAG}

          # idem para pedidos-service y usuarios-service (ajusta puertos)
          docker rm -f pedidos || true
          docker pull ${REGISTRY}/${REGISTRY_NS}/pedidos-service:${IMAGE_TAG}
          docker run -d --name pedidos -p 8081:8080 \
            ${REGISTRY}/${REGISTRY_NS}/pedidos-service:${IMAGE_TAG}

          docker rm -f usuarios || true
          docker pull ${REGISTRY}/${REGISTRY_NS}/usuarios-service:${IMAGE_TAG}
          docker run -d --name usuarios -p 8082:8080 \
            ${REGISTRY}/${REGISTRY_NS}/usuarios-service:${IMAGE_TAG}
        '''
      }
    }
  }

  post {
    always { sh 'docker logout || true' }
  }
}
