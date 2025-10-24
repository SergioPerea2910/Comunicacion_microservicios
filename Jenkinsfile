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
      image: alpine/helm:3.12.0
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
    // Cambia si tu módulo no está en 'app'
    APP_DIR = 'gateway-service'

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
    // Trigger automático en push via webhook
    githubPush()
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
        expression { fileExists("${APP_DIR}/Dockerfile") }
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

    stage('Deploy with Helm to Minikube') {
      when {
        anyOf { branch 'main'; branch 'master'; branch 'develop' }
      }
      steps {
        container('kubectl-helm') {
          sh '''
            echo "🚀 Iniciando despliegue con Helm en Minikube..."

            # Verificar conexión a minikube
            kubectl cluster-info

            # Crear namespace si no existe
            kubectl create namespace microservicios --dry-run=client -o yaml | kubectl apply -f -

            # Cambiar al directorio del servicio
            cd "${WORKSPACE}/${APP_DIR}"

            # Actualizar dependencies del chart si existen
            helm dependency update charts/ || echo "No hay dependencias que actualizar"

            # Desplegar con Helm
            helm upgrade --install ${APP_DIR} ./charts/ \
              --namespace microservicios \
              --set image.repository="${DOCKER_REGISTRY}/${APP_DIR}" \
              --set image.tag="${IMAGE_TAG}" \
              --set image.pullPolicy=Always \
              --wait \
              --timeout=300s

            # Verificar el despliegue
            kubectl get pods -n microservicios -l app=${APP_DIR}
            kubectl get services -n microservicios -l app=${APP_DIR}

            echo "✅ Despliegue completado exitosamente"
          '''
        }
      }
    }
  }

  post {
    always {
      echo "🧹 Limpieza de workspace"
      // Sustituto de cleanWs() sin plugin
     // deleteDir()
    }
    success { echo "✅ Pipeline OK" }
    failure { echo "❌ Pipeline FAIL" }
    unstable { echo "⚠️ Pipeline inestable" }
    changed { echo "🔄 Estado del pipeline cambió" }
  }
}
