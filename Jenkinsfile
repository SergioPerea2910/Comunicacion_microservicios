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
      image: dtzar/helm-kubectl:3.12.0   # helm + kubectl
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

  parameters {
    string(name: 'ONLY', defaultValue: '', description: 'Opcional: gateway-service | pedidos-service | usuarios-service')
    booleanParam(name: 'DEPLOY', defaultValue: true, description: 'Desplegar con Helm al final de cada build')
  }

  environment {
    DOCKER_REGISTRY = 'docker.io/rojassluu'
    IMAGE_TAG       = "${env.BUILD_NUMBER}"
    SERVICES        = "gateway-service,pedidos-service,usuarios-service"
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '15'))
    timeout(time: 60, unit: 'MINUTES')
    disableConcurrentBuilds()
    skipStagesAfterUnstable()
  }

  stages {
    stage('Checkout') {
      steps {
        echo "🚀 ${env.JOB_NAME} #${env.BUILD_NUMBER}"
        checkout scm
        sh 'git config --global --add safe.directory "$WORKSPACE" || true'
      }
    }

    stage('Build + Push (+ Deploy) por servicio (secuencial)') {
      steps {
        script {
          def list = params.ONLY?.trim() ? [params.ONLY.trim()] : env.SERVICES.split(',') as List
          for (svc in list) {
            echo "▶️ Servicio: ${svc}"
            if (!fileExists("${svc}/pom.xml")) {
              echo "ℹ️  ${svc} no tiene pom.xml; salto."
              continue
            }

            stage("Build: ${svc}") {
              container('maven') {
                dir("${svc}") {
                  sh '''
                    echo "Java:" && java -version
                    echo "Maven:" && mvn -v
                    mvn -B -DskipTests clean package spring-boot:repackage
                    ls -lah target || true
                  '''
                }
              }
              archiveArtifacts artifacts: "${svc}/target/*.jar", excludes: '**/*.original', fingerprint: true
            }

            stage("Image (Kaniko): ${svc}") {
              container('kaniko') {
                retry(2) { // reintenta por si hay handshake/tls intermitente
                  sh """
                    set -euo pipefail
                    IMAGE_REPO="${DOCKER_REGISTRY}/${svc}"
                    echo "🐳 Kaniko: \${IMAGE_REPO}:${IMAGE_TAG}"

                    EXTRA_DEST=""
                    if [ "\${BRANCH_NAME:-}" = "main" ] || [ "\${BRANCH_NAME:-}" = "master" ]; then
                      EXTRA_DEST="--destination \${IMAGE_REPO}:latest"
                    fi

                    test -f /kaniko/.docker/config.json || { echo "❌ Falta /kaniko/.docker/config.json"; exit 1; }

                    /kaniko/executor \
                      --context "${WORKSPACE}/${svc}" \
                      --dockerfile "${WORKSPACE}/${svc}/Dockerfile" \
                      --destination "\${IMAGE_REPO}:${IMAGE_TAG}" \
                      \${EXTRA_DEST} \
                      --snapshotMode=redo \
                      --use-new-run
                  """
                }
              }
            }

            if (params.DEPLOY && fileExists("${svc}/charts/Chart.yaml")) {
              stage("Deploy (Helm): ${svc}") {
                container('kubectl-helm') {
                  sh """
                    set -euo pipefail
                    echo "🚀 Helm deploy: ${svc} tag=${IMAGE_TAG}"

                    kubectl create namespace microservicios --dry-run=client -o yaml | kubectl apply -f -

                    cd "${WORKSPACE}/${svc}"
                    helm dependency update charts/ || echo "Sin dependencias"

                    helm upgrade --install ${svc} ./charts/ \
                      --namespace microservicios \
                      --set image.repository="${DOCKER_REGISTRY}/${svc}" \
                      --set-string image.tag="${IMAGE_TAG}" \
                      --set image.pullPolicy=Always \
                      --wait --timeout=300s

                    echo "✅ Deployed: ${DOCKER_REGISTRY}/${svc}:${IMAGE_TAG}"
                    kubectl -n microservicios get deploy,po,svc -l app=${svc} || true
                  """
                }
              }
            } else {
              echo "⏭️  Despliegue omitido para ${svc} (DEPLOY=${params.DEPLOY}, chart=${fileExists(svc+'/charts/Chart.yaml')})"
            }

            // pequeña pausa entre servicios para no estresar red/registro
            sleep 2
          }
        }
      }
    }
  }

  post {
    always  { echo "🧹 Fin de ejecución (global)" }
    success { echo "✅ Pipeline OK" }
    failure { echo "❌ Pipeline FAIL" }
  }
}
