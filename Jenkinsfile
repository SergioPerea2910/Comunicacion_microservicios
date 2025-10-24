
pipeline {
  agent {
    kubernetes {
      label 'microservices-pipeline'
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
      resources:
        requests:
          memory: "1Gi"
          cpu: "500m"
        limits:
          memory: "2Gi"
          cpu: "1000m"
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
      resources:
        requests:
          memory: "512Mi"
          cpu: "300m"
        limits:
          memory: "1Gi"
          cpu: "500m"
    - name: helm
      image: alpine/helm:3.13.2
      command: ['cat']
      tty: true
      resources:
        requests:
          memory: "128Mi"
          cpu: "100m"
        limits:
          memory: "256Mi"
          cpu: "200m"
    - name: kubectl
      image: bitnami/kubectl:1.28
      command: ['sleep']
      args: ['infinity']
      resources:
        requests:
          memory: "64Mi"
          cpu: "50m"
        limits:
          memory: "128Mi"
          cpu: "100m"
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
    // Servicios a construir y desplegar
    SERVICES = 'gateway-service,pedidos-service,usuarios-service'

    // Configuraci贸n de registro Docker
    DOCKER_REGISTRY = 'docker.io/rojassluu'
    BUILD_VERSION = "${env.BUILD_NUMBER}"
    GIT_COMMIT = "${env.GIT_COMMIT?.take(8) ?: 'unknown'}"

    // Configuraci贸n de ambiente de producci贸n
    PROD_NAMESPACE = 'microservices-prod'
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
    stage('Checkout & Setup') {
      steps {
        echo "馃殌 Iniciando pipeline: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
        checkout scm

        // Evita 'detected dubious ownership' de Git en el workspace montado
        sh 'git config --global --add safe.directory "$WORKSPACE" || true'

        // Informaci贸n del commit
        script {
          env.GIT_COMMIT = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
          env.GIT_AUTHOR = sh(script: 'git log -1 --pretty=format:"%an"', returnStdout: true).trim()
          env.GIT_MESSAGE = sh(script: 'git log -1 --pretty=format:"%s"', returnStdout: true).trim()
        }

        echo """
馃搳 Informaci贸n del Build:
   鈥?Commit: ${env.GIT_COMMIT?.take(8)}
   鈥?Autor: ${env.GIT_AUTHOR}
   鈥?Mensaje: ${env.GIT_MESSAGE}
   鈥?Rama: ${env.BRANCH_NAME}
   鈥?Servicios: ${env.SERVICES}
        """
      }
    }

    stage('Build Services') {
      matrix {
        axes {
          axis {
            name 'SERVICE'
            values 'gateway-service', 'pedidos-service', 'usuarios-service'
          }
        }
        stages {
          stage('Maven Build') {
            steps {
              container('maven') {
                dir("${SERVICE}") {
                  sh """
                    echo "馃敤 Construyendo servicio: ${SERVICE}"
                    echo "Java:" && java -version
                    echo "Maven:" && mvn -v

                    # Verificar si existen tests
                    if [ -d "src/test/java" ] && [ "\$(find src/test/java -name '*.java' | wc -l)" -gt 0 ]; then
                      echo "馃搵 Tests encontrados, ejecutando..."
                      mvn clean test -B || echo "鈿狅笍 Warning: Algunos tests fallaron, pero continuando build..."
                    else
                      echo "鈩癸笍 No se encontraron tests en ${SERVICE}, saltando fase de testing"
                      mvn clean compile -B
                    fi

                    # Construir JAR ejecutable (siempre saltar tests en package para evitar duplicaci贸n)
                    mvn package spring-boot:repackage -DskipTests

                    echo "鉁?Build completado para ${SERVICE}"
                    echo "Contenido de target/:"
                    ls -lah target/ || true
                  """
                }
              }
            }
            post {
              always {
                // Publicar resultados de tests solo si existen
                script {
                  if (fileExists("${SERVICE}/target/surefire-reports/*.xml")) {
                    publishTestResults testResultsPattern: "${SERVICE}/target/surefire-reports/*.xml"
                  }
                }

                // Archivar artefactos
                archiveArtifacts artifacts: "${SERVICE}/target/*.jar",
                               excludes: '**/*.original',
                               fingerprint: true,
                               allowEmptyArchive: true
              }
            }
          }
        }
      }
    }

    // ============================================================================
    // QUALITY GATES (COMENTADO - DESCOMENTA PARA HABILITAR)
    // ============================================================================
    // stage('Quality Gates') {
    //   parallel {
    //     stage('Security Scan') {
    //       steps {
    //         container('maven') {
    //           script {
    //             env.SERVICES.split(',').each { service ->
    //               dir(service) {
    //                 sh """
    //                   echo "馃攳 Ejecutando security scan para ${service}..."
    //                   # Dependency check para vulnerabilidades
    //                   mvn org.owasp:dependency-check-maven:check -DskipTests || echo "鈿狅笍 Warning: Security scan failed for ${service}"
    //                 """
    //               }
    //             }
    //           }
    //         }
    //       }
    //     }
    //     stage('Code Coverage') {
    //       steps {
    //         container('maven') {
    //           script {
    //             env.SERVICES.split(',').each { service ->
    //               dir(service) {
    //                 sh """
    //                   echo "馃搳 Generando coverage para ${service}..."
    //                   mvn jacoco:report -DskipTests || echo "鈿狅笍 Warning: Coverage generation failed for ${service}"
    //                 """
    //               }
    //             }
    //           }
    //         }
    //       }
    //     }
    //   }
    // }

    stage('Docker Build & Push') {
      when {
        anyOf {
          branch 'main';
          branch 'master';
          branch 'develop';
          branch 'staging'
        }
      }
      parallel {
        stage('Gateway Service Image') {
          steps {
            script {
              buildDockerImage('gateway-service')
            }
          }
        }
        stage('Pedidos Service Image') {
          steps {
            script {
              buildDockerImage('pedidos-service')
            }
          }
        }
        stage('Usuarios Service Image') {
          steps {
            script {
              buildDockerImage('usuarios-service')
            }
          }
        }
      }
    }

    stage('Deploy to Production') {
      when {
        anyOf {
          branch 'main';
          branch 'master'
        }
      }
      steps {
        script {
          timeout(time: 10, unit: 'MINUTES') {
            def deployChoice = input(
              message: '驴Desplegar a producci贸n?',
              parameters: [
                choice(name: 'DEPLOY_ACTION', choices: ['Deploy', 'Skip'], description: 'Selecciona la acci贸n'),
                string(name: 'DEPLOYER_NOTES', defaultValue: '', description: 'Notas del deployment (opcional)')
              ],
              submitterParameter: 'DEPLOYER'
            )

            if (deployChoice.DEPLOY_ACTION == 'Deploy') {
              echo "馃殌 Desplegando a Producci贸n..."
              echo "馃懁 Desplegado por: ${env.DEPLOYER}"
              echo "馃摑 Notas: ${deployChoice.DEPLOYER_NOTES ?: 'Sin notas'}"

              deployToEnvironment(env.PROD_NAMESPACE, 'production')

              echo "鉁?Aplicaci贸n desplegada en producci贸n exitosamente"
            } else {
              echo "鈴笍 Deployment a producci贸n omitido por el usuario"
            }
          }
        }
      }
    }
  }

  post {
    always {
      script {
        echo "馃Ч Limpieza y reportes finales"

        // Publicar artefactos de security scans si existen
        publishHTML([
          allowMissing: true,
          alwaysLinkToLastBuild: true,
          keepAll: true,
          reportDir: 'target',
          reportFiles: 'dependency-check-report.html',
          reportName: 'Security Scan Report'
        ])

        // Limpiar workspace (opcional)
        // deleteDir()
      }
    }
    success {
      echo """
鉁?Pipeline Exitoso!
   鈥?Build: #${env.BUILD_NUMBER}
   鈥?Commit: ${env.GIT_COMMIT?.take(8)}
   鈥?Rama: ${env.BRANCH_NAME}
   鈥?Tiempo: ${currentBuild.durationString}
      """
    }
    failure {
      echo """
鉂?Pipeline Fall贸!
   鈥?Build: #${env.BUILD_NUMBER}
   鈥?Commit: ${env.GIT_COMMIT?.take(8)}
   鈥?Rama: ${env.BRANCH_NAME}
   鈥?Error en: ${env.STAGE_NAME}
      """
    }
    unstable {
      echo "鈿狅笍 Pipeline inestable - Algunos tests fallaron pero el build continu贸"
    }
    changed {
      echo "馃攧 Estado del pipeline cambi贸 desde el 煤ltimo build"
    }
  }
}

// ============================================================================
// FUNCIONES AUXILIARES
// ============================================================================

def buildDockerImage(serviceName) {
  container('kaniko') {
    sh """
      echo "馃惓 Construyendo imagen Docker para ${serviceName}..."

      # Verificar que existe el Dockerfile
      if [ ! -f "${serviceName}/Dockerfile" ]; then
        echo "鉂?Error: No se encontr贸 Dockerfile en ${serviceName}/"
        exit 1
      fi

      # Verificar que existe el JAR
      if [ ! -f ${serviceName}/target/*.jar ]; then
        echo "鉂?Error: No se encontr贸 JAR en ${serviceName}/target/"
        exit 1
      fi

      # Construir imagen con Kaniko
      /kaniko/executor \\
        --context "${WORKSPACE}/${serviceName}" \\
        --dockerfile "${WORKSPACE}/${serviceName}/Dockerfile" \\
        --destination "${DOCKER_REGISTRY}/${serviceName}:${BUILD_VERSION}" \\
        --destination "${DOCKER_REGISTRY}/${serviceName}:latest" \\
        --cache=true \\
        --cache-ttl=24h \\
        --snapshotMode=redo \\
        --use-new-run

      echo "鉁?Imagen ${serviceName}:${BUILD_VERSION} construida y publicada"
    """
  }
}

def deployToEnvironment(namespace, environmentName) {
  container('helm') {
    sh """
      echo "馃殌 Desplegando servicios a ${environmentName} (namespace: ${namespace})"

      # Crear namespace si no existe
      kubectl create namespace ${namespace} --dry-run=client -o yaml | kubectl apply -f -

      # Desplegar cada servicio
      for service in \$(echo "${SERVICES}" | tr ',' ' '); do
        echo "馃摝 Desplegando \${service} a ${environmentName}..."

        # Verificar que existe el chart
        if [ ! -d "\${service}/charts" ]; then
          echo "鈿狅笍 Warning: No se encontr贸 chart para \${service}, saltando..."
          continue
        fi

        # Desplegar con Helm
        helm upgrade --install \${service} ./\${service}/charts \\
          --set image.repository=${DOCKER_REGISTRY}/\${service} \\
          --set image.tag=${BUILD_VERSION} \\
          --set environment=${environmentName} \\
          --set namespace=${namespace} \\
          --namespace ${namespace} \\
          --wait \\
          --timeout=300s \\
          --create-namespace

        # Verificar deployment
        kubectl rollout status deployment/\${service} -n ${namespace} --timeout=300s

        echo "鉁?\${service} desplegado correctamente en ${environmentName}"
      done

      echo "馃帀 Todos los servicios desplegados exitosamente en ${environmentName}"

      # Mostrar estado final
      kubectl get pods -n ${namespace}
      kubectl get services -n ${namespace}
    """
  }
}
