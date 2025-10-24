pipeline {
  agent {
    kubernetes {
      label 'linux'
      defaultContainer 'maven'
      yaml """
apiVersion: v1
kind: Pod
metadata:
  labels:
    jenkins/label: jenkins-jenkins-agent
spec:
  serviceAccountName: default
  containers:
    - name: maven
      image: maven:3.9.9-eclipse-temurin-17
      command:
        - cat
      tty: true
      volumeMounts:
        - name: m2-cache
          mountPath: /root/.m2
    - name: kaniko
      image: gcr.io/kaniko-project/executor:debug
      command:
        - /busybox/sh
        - -c
      args:
        - "sleep 365d"
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
    // >>> CAMBIA ESTO <<<
    DOCKER_REGISTRY = 'docker.io/tuusuario' // p.ej. docker.io/tuusuario ó ghcr.io/tuorg
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
    // Revisa cambios cada 5 minutos (opcional, si no usas webhooks)
    pollSCM('H/5 * * * *')
  }

  stages {
    stage('Checkout') {
      steps {
        echo "🚀 Iniciando pipeline: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
        checkout scm
        sh 'git log -1 --oneline || true'
      }
    }

    stage('Build (Maven)') {
      steps {
        container('maven') {
          sh '''
            echo "Java:"
            java -version
            echo "Maven:"
            mvn -v

            # Compila y reempaqueta el JAR ejecutable
            mvn -B -DskipTests clean package spring-boot:repackage

            echo "Contenido de target/:"
            ls -lah target || true
          '''
        }
      }
    }

    stage('Archive artifact') {
      steps {
        archiveArtifacts artifacts: 'target/*.jar,**/target/*.jar', excludes: '**/*.original', fingerprint: true
      }
    }

    stage('Docker Build & Push (Kaniko)') {
      when {
        anyOf {
          branch 'main'
          branch 'master'
          branch 'develop'
        }
        expression { fileExists('Dockerfile') }
      }
      steps {
        container('kaniko') {
          sh '''
            echo "🐳 Construyendo y publicando imagen con Kaniko..."
            /kaniko/executor \
              --context "${WORKSPACE}" \
              --dockerfile "${WORKSPACE}/Dockerfile" \
              --destination "${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}" \
              --destination "${DOCKER_REGISTRY}/${IMAGE_NAME}:latest" \
              --snapshotMode=redo \
              --use-new-run
          '''
        }
      }
    }

    stage('Deploy to Production') {
      when {
        branch 'main'
      }
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
      cleanWs()
    }
    success {
      echo "✅ Pipeline OK"
    }
    failure {
      echo "❌ Pipeline FAIL"
    }
    unstable {
      echo "⚠️ Pipeline inestable"
    }
    changed {
      echo "🔄 Estado del pipeline cambió desde el último build"
    }
  }
}
