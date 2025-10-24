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
"""
    }
  }

  // Si ya tienes webhook + plugin GitHub, puedes usar: triggers { githubPush() }
  triggers { pollSCM('H/2 * * * *') }

  environment {
    REGISTRY     = 'docker.io'
    REGISTRY_NS  = 'rojassluu'
    APP_NS       = 'microservicios'
    COMMIT_SHA   = "${env.GIT_COMMIT?.take(7) ?: 'dev'}"
    IMAGE_TAG    = "${COMMIT_SHA}"
  }

  stages {
    stage('Checkout') {
      steps { checkout scm }
    }

    stage('Build & Push images (Kaniko)') {
      steps {
        container('kaniko') {
          sh '''
            set -eux

            build () {
              local ctx="$1" img="$2"
              /kaniko/executor \
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

            echo "URL Gateway (NodePort si aplica):"
            minikube service gateway -n ${APP_NS} --url || true
          '''
        }
      }
    }
  }

  post {
    success { echo "✅ Build & Deploy OK — tag ${IMAGE_TAG}" }
    failure { echo "❌ Falló el pipeline (revisa la etapa en rojo)." }
  }
}
