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
  volumes:
    - name: m2-cache
      emptyDir: {}
"""
    }
  }

  environment {
    APP_DIR = 'gateway-service'
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

  post {
    success {
      echo "✅ Build generado correctamente"
    }
    failure {
      echo "❌ Error en el build"
    }
  }
}
