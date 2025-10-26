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
      workingDir: /home/jenkins/agent
      volumeMounts:
        - name: m2-cache
          mountPath: /root/.m2
        - name: workspace-volume
          mountPath: /home/jenkins/agent
    - name: kaniko
      image: gcr.io/kaniko-project/executor:debug
      command: ['/busybox/sh','-c']
      args: ['sleep 365d']
      workingDir: /home/jenkins/agent
      env:
        - name: DOCKER_CONFIG
          value: /kaniko/.docker
      volumeMounts:
        - name: docker-config
          mountPath: /kaniko/.docker
        - name: workspace-volume
          mountPath: /home/jenkins/agent
    - name: kubectl-helm
      image: dtzar/helm-kubectl:3.12.0
      command: ['cat']
      tty: true
      workingDir: /home/jenkins/agent
      volumeMounts:
        - name: kubeconfig
          mountPath: /root/.kube
        - name: workspace-volume
          mountPath: /home/jenkins/agent
  volumes:
    - name: m2-cache
      emptyDir: {}
    - name: workspace-volume
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
            archiveArtifacts artifacts: "${APP_DIR}/target/*.jar", excludes: '**/*.original', fingerprint: true
            if (fileExists("${APP_DIR}/Dockerfile")) {
              container('kaniko') {
                sh """
                  echo "🧩 Ejecutando Kaniko en /home/jenkins/agent"
                  /kaniko/executor \
                    --context "/home/jenkins/agent/${APP_DIR}" \
                    --dockerfile "/home/jenkins/agent/${APP_DIR}/Dockerfile" \
                    --destination "${DOCKER_REGISTRY}/${APP_DIR}:${IMAGE_TAG}" \
                    --destination "${DOCKER_REGISTRY}/${APP_DIR}:latest" \
                    --snapshot-mode redo \
                    --use-new-run \
                    --verbosity=info
                """
              }
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
