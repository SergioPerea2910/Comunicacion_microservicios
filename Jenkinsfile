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
      image: maven:3.9.9-eclipse-temurin-17
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
    // Cambia si tu módulo no está en 'app'
    APP_DIR = 'GatewayApplication/'

    // Cambia a tu registro (docker.io/tuusuario o ghcr.io/tuorg)
    DOCKER_REGISTRY = 'docker.io/rojassluu'
    IMAGE_NAME      = 'comunicacion-microservicios'
    IMAGE_TAG       = "${env.BUILD_NUMBER}"
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
    timeout(time: 30, unit: 'MINUTES')
    skipStagesAfterUnstable()
    disableConcurrentBuilds()
  }

  triggers {
    // opcional si no usas webhooks
    pollSCM('H/5 * * * *')
  }

  stages {
    stage('Checkout') {
      steps {
        echo "🚀 Iniciando pipeline: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
        checkout scm

        // Evita 'detected dubious ownership' de Git en el workspace montado
        sh 'git config --global --add safe.directory "$WORKSPACE" || true'

        // Verificar última confirmación
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

              # Compilar y generar JAR ejecutable
              mvn -B -DskipTests clean package spring-boot:repackage

              echo "Contenido de target/:"
              ls -lah target || true
            '''
          }
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
        anyOf { branch 'main'; branch 'master'; branch 'develop' }
        expression { fileExists('Dockerfile') }
      }
      steps {
        container('kaniko') {
          sh '''
            echo "🐳 Construyendo y publicando imagen con Kaniko..."
            /kaniko/executor \
              --context "${WORKSPACE}" \
              --dockerfile "${WORKSPACE}/Dockerfile" \
              --build-arg JAR_FILE="${APP_DIR}/target/*.jar" \
              --destination "${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}" \
              --destination "${DOCKER_REGISTRY}/${IMAGE_NAME}:latest" \
              --snapshotMode=redo \
              --use-new-run
          '''
        }
      }
    }

    stage('Deploy to Production') {
      when { branch 'main' }
      steps {
        script {
          timeout(time: 10, unit: 'MINUTES') {
            input message: '¿Desplegar a producción?', ok: 'Desplegar', submitterParameter: 'DEPLOYER'
          }
          echo "🚀 Desplegando a Producción... (desplegado por: ${env.DEPLOYER})"
          // sh './deploy-production.sh'
          echo "✅ Aplicación desplegada en producción"
        }
      }
    }
  }

  post {
    always {
      echo "🧹 Limpieza de workspace"
      // Sustituto de cleanWs() sin plugin
      deleteDir()
    }
    success { echo "✅ Pipeline OK" }
    failure { echo "❌ Pipeline FAIL" }
    unstable { echo "⚠️ Pipeline inestable" }
    changed { echo "🔄 Estado del pipeline cambió" }
  }
}
