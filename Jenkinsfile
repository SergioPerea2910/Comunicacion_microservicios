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
      image: dtzar/helm-kubectl:3.12.0   # <- trae kubectl + helm
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
    stage('Procesar servicios') {
      steps {
        script {
          def servicios = [
            'gateway-service',
            'usuarios-service',
            'pedidos-service'
          ]

          for (micro in servicios) {
            echo "!!!------ Iniciando pipeline para: ${micro} ------!!!"
            
            dir("${micro}") {
              // Build y empaquetado
			  echo "!!!------ Build y empaquetado para: ${micro} ------!!!"
              container('maven') {
                sh '''
                  echo "!!!------Compilando ${micro} ------!!!"
                  mvn -B -DskipTests clean package spring-boot:repackage
                  ls -lah target || true
                '''
              }
              // Archivado del artefacto
              archiveArtifacts artifacts: "target/*.jar", excludes: '**/*.original', fingerprint: true

              // Construcción de imagen y push (si hay Dockerfile)
			  
              if (fileExists("Dockerfile")) {
                container('kaniko') {
				  echo "!!!------Construcción de imagen y push ${micro} ------!!!"
                  sh """
                    IMAGE_REPO="${DOCKER_REGISTRY}/${micro}"
                    /kaniko/executor \
                      --context "." \
                      --dockerfile "Dockerfile" \
                      --destination "${IMAGE_REPO}:${IMAGE_TAG}" \
                      --destination "${IMAGE_REPO}:latest" \
                      --snapshotMode=redo \
                      --use-new-run
                  """
                }
              }

              // Despliegue con Helm
              container('kubectl-helm') {
			    echo "!!!------Despliegue con Helm para: ${micro} ------!!!"
                sh """
                  kubectl create namespace microservicios --dry-run=client -o yaml | kubectl apply -f -
                  helm dependency update charts/ || echo "No hay dependencias"
                  helm upgrade --install ${micro} ./charts/ \
                    --namespace microservicios \
                    --set image.repository="${DOCKER_REGISTRY}/${micro}" \
                    --set image.tag="${IMAGE_TAG}" \
                    --set image.pullPolicy=Always \
                    --wait \
                    --timeout=300s
                  kubectl get pods -n microservicios -l app=${micro}
                  kubectl get svc -n microservicios -l app=${micro}
                """
              }
            }
            echo "------ Finalizado pipeline para: ${micro} ------"
          }
        }
      }
    }
  }
  post {
    always { echo "Fin de ejecución" }
    success { echo "✅ Pipeline OK" }
    failure { echo "❌ Pipeline FAIL" }
  }
}
###
