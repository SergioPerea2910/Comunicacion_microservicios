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
"""
    }
  }

  environment {
    APP_DIR = 'gateway-service'
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

    stage('Build (Maven)') {
      steps {
        container('maven') {
          dir("${APP_DIR}") {
            sh '''
              echo "Java:" && java -version
              echo "Maven:" && mvn -v
              mvn -B -DskipTests clean package spring-boot:repackage
              echo "📦 target/:"
              ls -lah target || true
            '''
          }
        }
      }
      post {
        failure {
          echo "❌ Falló el build con Maven; mostrando contenido de workspace"
          sh 'ls -lah'
        }
      }
    }

    stage('Archive artifact') {
      steps {
        archiveArtifacts artifacts: "${APP_DIR}/target/*.jar", excludes: '**/*.original', fingerprint: true
      }
    }

    stage('Docker Build & Push (Kaniko)') {
      when {
        expression { fileExists("${APP_DIR}/Dockerfile") }
        // anyOf { branch 'main'; branch 'master'; branch 'develop' } // opcional
      }
      steps {
        container('kaniko') {
          sh '''
            echo "🐳 Construyendo y publicando imagen con Kaniko..."
            /kaniko/executor \
              --context "${WORKSPACE}/${APP_DIR}" \
              --dockerfile "${WORKSPACE}/${APP_DIR}/Dockerfile" \
              --destination "${DOCKER_REGISTRY}/${APP_DIR}:${IMAGE_TAG}" \
              --destination "${DOCKER_REGISTRY}/${APP_DIR}:latest" \
              --snapshotMode=redo \
              --use-new-run
          '''
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
