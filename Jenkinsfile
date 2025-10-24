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
     command:
       - "/busybox/sh"
       - "-c"
     args:
       - "sleep infinity"
     volumeMounts:
       - name: docker-config
         mountPath: /kaniko/.docker
  - name: kubectl
    image: bitnami/kubectl:latest
    command: ['sleep','infinity']
  volumes:
  - name: docker-config
    projected:
      sources:
      - secret:
          name: dockerhub-creds-json
"""
    }
  }

  triggers { githubPush() }     // dispara con cada push
  options  { timestamps() }

  environment {
    REGISTRY    = 'docker.io'
    REGISTRY_NS = 'rojassluu'           // <-- cambia si es otro namespace en Docker Hub
    APP_NS      = 'microservicios'
    IMAGE_TAG   = "${env.GIT_COMMIT?.take(7) ?: 'dev'}"
  }

  stages {
    stage('Checkout'){ steps { checkout scm } }

    stage('Docker auth secret (para Kaniko)') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                           usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
          sh '''
            kubectl -n jenkins delete secret dockerhub-creds-json --ignore-not-found
            kubectl -n jenkins create secret docker-registry dockerhub-creds-json \
              --docker-server=https://index.docker.io/v1/ \
              --docker-username="$DH_USER" \
              --docker-password="$DH_PASS" \
              --docker-email="no-reply@example.com"
          '''
        }
      }
    }

    stage('Build & Push (Kaniko)') {
      steps {
        container('kaniko') {
          sh '''
            set -eux
            build () {
              ctx="$1"; img="$2"
              /kaniko/executor \
                --context="${ctx}" --dockerfile="${ctx}/Dockerfile" \
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

    stage('Deploy a Minikube') {
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

            echo "URL Gateway:"
            minikube service gateway -n ${APP_NS} --url || true
          '''
        }
      }
    }
  }

  post {
    success { echo "✅ Build & Deploy OK — tag ${IMAGE_TAG}" }
    failure { echo "❌ Falló el pipeline (revisa la etapa en rojo)" }
  }
}
