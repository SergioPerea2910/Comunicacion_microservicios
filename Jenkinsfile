pipeline {
  agent any

  parameters {
    string(name: 'ONLY', defaultValue: '', description: 'Opcional: gateway-service | pedidos-service | usuarios-service')
    booleanParam(name: 'DEPLOY', defaultValue: true, description: 'Desplegar con Helm al final de cada servicio')
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
        sh 'git log -1 --oneline || true'
      }
    }

    stage('Build + Push (+ Deploy) por servicio (secuencial)') {
      steps {
        script {
          def list = params.ONLY?.trim() ? [params.ONLY.trim()] : env.SERVICES.split(',') as List

          for (svc in list) {
            echo "▶️ Servicio: ${svc}"
            if (!fileExists("${svc}/pom.xml")) {
              echo "ℹ️  ${svc} no tiene pom.xml; se omite."
              continue
            }

            stage("Build (Maven): ${svc}") {
              dir("${svc}") {
                sh '''
                  echo "Java:" && java -version || true
                  echo "Maven:" && mvn -v || true
                  mvn -B -DskipTests clean package spring-boot:repackage
                  ls -lah target || true
                '''
              }
              archiveArtifacts artifacts: "${svc}/target/*.jar", excludes: '**/*.original', fingerprint: true
            }

            stage("Image (Docker build & push): ${svc}") {
              withEnv(["IMAGE_REPO=${DOCKER_REGISTRY}/${svc}"]) {
                sh '''
                  set -euo pipefail
                  echo "🐳 Build: ${IMAGE_REPO}:${IMAGE_TAG}"
                  cd "${WORKSPACE}/${svc}"

                  # Asegurar login ya hecho previamente en el nodo Jenkins
                  docker build -t "${IMAGE_REPO}:${IMAGE_TAG}" -t "${IMAGE_REPO}:latest" .

                  echo "📤 Push: ${IMAGE_REPO}:${IMAGE_TAG}"
                  docker push "${IMAGE_REPO}:${IMAGE_TAG}"

                  # Solo en main/master empujamos también latest (opcional)
                  if [ "${BRANCH_NAME:-}" = "main" ] || [ "${BRANCH_NAME:-}" = "master" ]; then
                    echo "📤 Push: ${IMAGE_REPO}:latest"
                    docker push "${IMAGE_REPO}:latest"
                  else
                    echo "⏭️  Rama ${BRANCH_NAME:-} sin push de :latest"
                  fi
                '''
              }
            }

            if (params.DEPLOY && fileExists("${svc}/charts/Chart.yaml")) {
              stage("Deploy (Helm): ${svc}") {
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
            } else {
              echo "⏭️  Despliegue omitido para ${svc} (DEPLOY=${params.DEPLOY}, chart=${fileExists(svc+'/charts/Chart.yaml')})"
            }

            sleep 2 // respirito entre servicios
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
