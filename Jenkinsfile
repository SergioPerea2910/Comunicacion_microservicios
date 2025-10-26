pipeline {
  agent {
    kubernetes {
      label 'linux'
      defaultContainer 'maven'
      yaml """
apiVersion: v1
kind: Pod
spec:
  serviceAccountName: default
  containers:
    - name: maven
      image: maven:3.9.9-eclipse-temurin-21
      command: ['cat']
      tty: true
      volumeMounts:
        - name: m2-cache
          mountPath: /root/.m2
    - name: kaniko
      image: gcr.io/kaniko-project/executor:debug
      command: ['/busybox/sh','-c']
      args: ['sleep 365d']
      env:
        - name: DOCKER_CONFIG
          value: /kaniko/.docker
      volumeMounts:
        - name: docker-config
          mountPath: /kaniko/.docker
    - name: kubectl-helm
      image: dtzar/helm-kubectl:3.12.0
      command: ['cat']
      tty: true
      volumeMounts:
        - name: kubeconfig
          mountPath: /root/.kube
  volumes:
    - name: m2-cache
      emptyDir: {}
    - name: docker-config
      projected:
        sources:
          - secret:
              name: dockerhub-creds-json
              items:
                - key: .dockerconfigjson
                  path: config.json
    - name: kubeconfig
      projected:
        sources:
          - secret:
              name: kubeconfig-secret
              items:
                - key: config
                  path: config
"""
    }
  }
  environment {
    DOCKER_REGISTRY = 'docker.io/rojassluu'
    IMAGE_TAG = "${env.BUILD_NUMBER}"
  }
  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timeout(time: 30, unit: 'MINUTES')
    disableConcurrentBuilds()
  }
  stages {
    stage('Checkout') {
      steps {
        echo "🚀 Iniciando pipeline..."
        checkout scm
        sh 'git config --global --add safe.directory "$WORKSPACE" || true'
        sh 'git log -1 --oneline || true'
      }
    }
    stage('Build, Docker & Deploy microservicios') {
      steps {
        script {
          def servicios = ['gateway-service', 'usuarios-service', 'pedidos-service']
          for (APP_DIR in servicios) {
            echo "▶️ Procesando: ${APP_DIR} ◀️"
            // Build
            container('maven') {
              dir("${APP_DIR}") {
                sh """
                  echo "Java:" && java -version
                  echo "Maven:" && mvn -v
                  mvn -B -DskipTests clean package spring-boot:repackage
                  echo "📦 target/:"
                  ls -lah target || true
                """
              }
            }
            // Archivar artefacto
            archiveArtifacts artifacts: "${APP_DIR}/target/*.jar", excludes: '**/*.original', fingerprint: true
            // Docker Build & Push
            if (fileExists("${APP_DIR}/Dockerfile")) {
              container('kaniko') {
                sh """
                  /kaniko/executor \
                    --context "${WORKSPACE}/${APP_DIR}" \
                    --dockerfile "${WORKSPACE}/${APP_DIR}/Dockerfile" \
                    --destination "${DOCKER_REGISTRY}/${APP_DIR}:${IMAGE_TAG}" \
                    --destination "${DOCKER_REGISTRY}/${APP_DIR}:latest" \
                    --snapshotMode=redo \
                    --use-new-run
                """
              }
            }
            // Deploy con Helm (usa secreto kubeconfig si es pod)
            container('kubectl-helm') {
              //export KUBECONFIG=/root/.kube/config
              sh """
                echo "🚀 Desplegando ${APP_DIR} con Helm..."
                export KUBECONFIG=/root/.kube/config
                kubectl create namespace microservicios --dry-run=client -o yaml | kubectl apply -f -
                cd "${WORKSPACE}/${APP_DIR}"
                helm dependency update charts/ || echo "No hay dependencias que actualizar"
                helm upgrade --install ${APP_DIR} ./charts/ \
                  --namespace microservicios \
                  --set image.repository="${DOCKER_REGISTRY}/${APP_DIR}" \
                  --set image.tag="${IMAGE_TAG}" \
                  --set image.pullPolicy=Always \
                  --wait \
                  --timeout=300s
                kubectl get pods -n microservicios -l app=${APP_DIR}
                kubectl get svc -n microservicios -l app=${APP_DIR}
                echo "✅ Despliegue completado: ${APP_DIR}\\n"
              """
            }
          }
        }
      }
    }
  }
  post {
    always { echo "🧹 Fin de ejecución (global)" }
    success { echo "✅ Pipeline OK" }
    failure { echo "❌ Pipeline FAIL" }
  }
}
