pipeline {
  agent {
    kubernetes {
      cloud 'kubernetes'
      defaultContainer 'jnlp'
      yaml """
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: jenkins
  containers:
    - name: kaniko
      image: gcr.io/kaniko-project/executor:debug
      env:
        - name: DOCKER_CONFIG
          value: /kaniko/.docker
      command: ["/busybox/sh","-c"]
      args: ["sleep infinity"]
      volumeMounts:
        - name: docker-config
          mountPath: /kaniko/.docker
    - name: kubectl
      image: bitnami/kubectl:latest
      command: ["sleep","infinity"]
  volumes:
    - name: docker-config
      projected:
        sources:
          - secret:
              name: dockerhub-creds-json
              items:
                - key: .dockerconfigjson
                  path: config.json
"""
    }
  }

  // Usa pollSCM mientras el webhook no esté 100% operativo
  triggers { pollSCM('H/2 * * * *') }

  environment {
    REGISTRY     = 'docker.io'
    REGISTRY_NS  = 'rojassluu'          // tu cuenta DockerHub
    APP_NS       = 'microservicios'
    IMAGE_TAG    = "${env.GIT_COMMIT?.take(7) ?: 'dev'}"
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Build & Push (gateway)') {
      steps {
        container('kaniko') {
          sh '''
            set -eux
            /kaniko/executor \
              --verbosity=debug \
              --context="gateway-service" \
              --dockerfile="gateway-service/Dockerfile" \
              --destination ${REGISTRY}/${REGISTRY_NS}/gateway-service:${IMAGE_TAG} \
              --destination ${REGISTRY}/${REGISTRY_NS}/gateway-service:latest \
              --cache=true
          '''
        }
      }
    }

    stage('Deploy (gateway)') {
      steps {
        container('kubectl') {
          sh '''
            set -eux
            kubectl create ns ${APP_NS} --dry-run=client -o yaml | kubectl apply -f -
            kubectl apply -f k8s/namespace.yaml || true
            kubectl apply -f k8s/gateway.yaml

            kubectl -n ${APP_NS} set image deploy/gateway \
              gateway=${REGISTRY}/${REGISTRY_NS}/gateway-service:${IMAGE_TAG}

            kubectl -n ${APP_NS} rollout status deploy/gateway --timeout=120s
            echo "URL Gateway:"
            minikube service gateway -n ${APP_NS} --url || true
          '''
        }
      }
    }

    // Descomenta cuando gateway funcione
    // stage('Build & Push (resto)') {
    //   steps {
    //     container('kaniko') {
    //       sh '''
    //         set -eux
    //         /kaniko/executor --context="pedidos-service"  --dockerfile="pedidos-service/Dockerfile"  --destination ${REGISTRY}/${REGISTRY_NS}/pedidos-service:${IMAGE_TAG}  --destination ${REGISTRY}/${REGISTRY_NS}/pedidos-service:latest  --cache=true
    //         /kaniko/executor --context="usuarios-service" --dockerfile="usuarios-service/Dockerfile" --destination ${REGISTRY}/${REGISTRY_NS}/usuarios-service:${IMAGE_TAG} --destination ${REGISTRY}/${REGISTRY_NS}/usuarios-service:latest --cache=true
    //       '''
    //     }
    //   }
    // }
    // stage('Deploy (resto)') {
    //   steps {
    //     container('kubectl') {
    //       sh '''
    //         set -eux
    //         kubectl apply -f k8s/pedidos.yaml
    //         kubectl apply -f k8s/usuarios.yaml
    //         kubectl -n ${APP_NS} set image deploy/pedidos  pedidos=${REGISTRY}/${REGISTRY_NS}/pedidos-service:${IMAGE_TAG}
    //         kubectl -n ${APP_NS} set image deploy/usuarios usuarios=${REGISTRY}/${REGISTRY_NS}/usuarios-service:${IMAGE_TAG}
    //         kubectl -n ${APP_NS} rollout status deploy/pedidos
    //         kubectl -n ${APP_NS} rollout status deploy/usuarios
    //       '''
    //     }
    //   }
    // }
  }

  post {
    success { echo "✅ OK — gateway ${IMAGE_TAG} construido, publicado y desplegado" }
    failure { echo "❌ Falló el pipeline (revisa el stage en rojo)" }
  }
}
