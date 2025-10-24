pipeline {
  agent {
    kubernetes {
      label 'linux'
      defaultContainer 'maven'
      yaml """
apiVersion: v1
kind: Pod
spec:
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
              name: dockerhub-creds-json     # <- el Secret que creamos
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
    timeout(time: 20, unit: 'MINUTES')
  }

  stages {
    stage('Checkout') {
      steps {
        echo "🚀 Iniciando build simple..."
        checkout scm
        sh 'git config --global --add safe.directory "$WORKSPACE" || true'
      }
    }

    stage('Build con Maven') {
      steps {
        container('maven') {
          dir("${APP_DIR}") {
            sh '''
              echo "Java version:" && java -version
              echo "Maven version:" && mvn -v

              echo "🏗️ Compilando proyecto..."
              mvn -B -DskipTests clean package spring-boot:repackage

              echo "📦 Archivos en target/:"
              ls -lah target || true
            '''
          }
        }
      }
    }

 stage('Archivar JAR') {
      steps {
        archiveArtifacts artifacts: "${APP_DIR}/target/*.jar", excludes: '**/*.original', fingerprint: true
      }
    }
  }



stage('Docker Build & Push (Kaniko)') {
  when {
    expression { fileExists("${APP_DIR}/Dockerfile") }   // solo si existe el Dockerfile
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

post {
    success { echo "✅ Build y push completados" }
    failure { echo "❌ Falló build/push" }
  }
}
