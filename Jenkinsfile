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

  // Puedes usar triggers { githubPush() } si tienes el plugin GitHub configurado con webhooks
  triggers { pollSCM('H/2 * * * *') }

  environment {
    REGISTRY     = 'docker.io'
    REGISTRY_NS  = 'rojassluu'         // tu namespace en Docker Hub
    APP_NS       = 'microservicios'    // namespace de tus apps en Minikube/K8s
    COMMIT_SHA   = "${env.GIT_COMMIT?.take(7) ?: 'dev'}"
    IMAGE_TAG    = "${COMMIT_SHA}"
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Build & Push images (Kaniko)') {
      steps {
        container('kaniko') {
          sh '''
            set -eux

            build () {
              local ctx="$1" img="$2"
              echo "🔨 Construyendo imagen para $img..."
              /kaniko/executor \
                --verbosity=debug \
                --context="${ctx}" \
                --dockerfile="${ctx}/Dockerfile" \
                --destination ${REGISTRY}/${REGISTRY_NS}/${img}:${IMAGE_TAG} \
                --destination ${REGISTRY}/${REGISTRY_NS}/${img}:latest \
                --cache=true
            }

            build gateway-service  gateway-service
            build pedidos-service  pedidos-service
            build usuarios-service usuarios-service
          '''
        }
      }
    }

    stage('Deploy to Minikube (kubectl)') {
      steps {
        container('kubectl') {
          sh '''
            set -eux

            echo "🚀 Desplegando en Kubernetes (Minikube)..."

            kubectl apply -f k8s/namespace.yaml
            kubectl apply -f k8s/usuarios.yaml
            kubectl apply -f k8s/pedidos.yaml
            kubectl apply -f k8s/gateway.yaml

            kubectl -n ${APP_NS} set image deploy/usuarios usuarios=${REGISTRY}/${REGISTRY_NS}/usuarios-service:${IMAGE_TAG}
            kubectl -n ${APP_NS} set image deploy/pedidos  pedidos=${REGISTRY}/${REGISTRY_NS}/pedidos-service:${IMAGE_TAG}
            kubectl -n ${APP_NS} set image deploy/gateway  gateway=${REGISTRY}/${REGISTRY_NS}/gateway-service:${IMAGE_TAG}

            kubectl -n ${APP_NS} rollout status deploy/usuarios
            kubectl -n ${APP_NS} rollout status deploy/pedidos
            kubectl -n ${APP_NS} rollout status deploy/gateway

            echo "🌐 Gateway URL:"
            minikube service gateway -n ${APP_NS} --url || true
          '''
        }
      }
    }
  }

  post {
    success {
      echo "✅ Build & Deploy completado con éxito — tag ${IMAGE_TAG}"
    }
    failure {
      echo "❌ Falló el pipeline (revisa la etapa en rojo o logs de Kaniko)."
    }
  }
}
