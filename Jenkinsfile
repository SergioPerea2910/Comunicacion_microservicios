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

  environment {
    DOCKER_REGISTRY = 'docker.io/rojassluu'
    IMAGE_TAG       = "${env.BUILD_NUMBER}"
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

    stage('Build + Push + Deploy (por servicio)') {
      matrix {
        axes {
          axis {
            name 'SERVICE'
            values 'gateway-service', 'pedidos-service', 'usuarios-service'
          }
        }

        when {
          expression { fileExists("${SERVICE}/pom.xml") } // solo si existe la carpeta/servicio
        }

        stages {

          stage('Build (Maven)') {
            steps {
              container('maven') {
                dir("${SERVICE}") {
                  sh '''
                    echo "Java:" && java -version
                    echo "Maven:" && mvn -v
                    mvn -B -DskipTests clean package spring-boot:repackage
                    ls -lah target || true
                  '''
                }
              }
            }
          }

          stage('Archive JAR') {
            steps {
              archiveArtifacts artifacts: "${SERVICE}/target/*.jar",
                               excludes: '**/*.original',
                               fingerprint: true
            }
          }

          stage('Docker Build & Push (Kaniko)') {
            when { expression { fileExists("${SERVICE}/Dockerfile") } }
            steps {
              container('kaniko') {
                sh '''
                  echo "🐳 Kaniko: ${SERVICE}:${IMAGE_TAG}"

                  # Solo en main/master también publicamos :latest
                  EXTRA_DEST=""
                  if [ "${BRANCH_NAME}" = "main" ] || [ "${BRANCH_NAME}" = "master" ]; then
                    EXTRA_DEST="--destination ${DOCKER_REGISTRY}/${SERVICE}:latest"
                  fi

                  /kaniko/executor \
                    --context "${WORKSPACE}/${SERVICE}" \
                    --dockerfile "${WORKSPACE}/${SERVICE}/Dockerfile" \
                    --destination "${DOCKER_REGISTRY}/${SERVICE}:${IMAGE_TAG}" \
                    ${EXTRA_DEST} \
                    --snapshotMode=redo \
                    --use-new-run
                '''
              }
            }
          }

          stage('Deploy con Helm a Minikube') {
            when { expression { fileExists("${SERVICE}/charts/Chart.yaml") } }
            steps {
              container('kubectl-helm') {
                sh '''
                  echo "🚀 Helm deploy: ${SERVICE} tag=${IMAGE_TAG}"

                  # Namespace
                  kubectl create namespace microservicios --dry-run=client -o yaml | kubectl apply -f -

                  # Chart del servicio
                  cd "${WORKSPACE}/${SERVICE}"
                  helm dependency update charts/ || echo "Sin dependencias"

                  helm upgrade --install ${SERVICE} ./charts/ \
                    --namespace microservicios \
                    --set image.repository="${DOCKER_REGISTRY}/${SERVICE}" \
                    --set-string image.tag="${IMAGE_TAG}" \
                    --set image.pullPolicy=Always \
                    --wait --timeout=300s

                  echo "✅ Deployed image: ${DOCKER_REGISTRY}/${SERVICE}:${IMAGE_TAG}"
                  kubectl -n microservicios get deploy,po,svc -l app=${SERVICE} || true
                '''
              }
            }
          }

        } // stages por SERVICE
      } // matrix
    } // stage matrix

  }

  post {
    always  { echo "🧹 Fin de ejecución (global)" }
    success { echo "✅ Pipeline OK" }
    failure { echo "❌ Pipeline FAIL" }
  }
}
