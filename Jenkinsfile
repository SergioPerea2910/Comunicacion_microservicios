pipeline {
  agent any
  options { timestamps() }

  // Disparadores: Webhook de GitHub (vía ngrok) + respaldo por polling
  triggers {
    githubPush()
    // Descomenta si quieres respaldo cada 5 min:
    // pollSCM('H/5 * * * *')
  }

  environment {
    // Registry
    REGISTRY        = 'docker.io'
    REGISTRY_NS     = 'rojassluu'

    // Tag de las imágenes (usa SHA corto + latest)
    COMMIT_SHA      = "${env.GIT_COMMIT?.take(7) ?: 'dev'}"
    IMAGE_TAG       = "${COMMIT_SHA}"         // cambia a 'latest' si prefieres solo latest
    DOCKER_CREDS_ID = 'dockerhub-creds'

    // Puertos host (ajústalos si los quieres distintos)
    PORT_GATEWAY    = '8083'
    PORT_PEDIDOS    = '8081'
    PORT_USUARIOS   = '8082'

    // Nombres de contenedor
    C_GATEWAY       = 'gateway'
    C_PEDIDOS       = 'pedidos'
    C_USUARIOS      = 'usuarios'

    // Red Docker para comunicación por DNS entre contenedores
    NET_NAME        = 'ms_net'
  }

  stages {
    stage('Checkout') {
      steps { checkout scm }
    }

    stage('Docker login') {
      steps {
        withCredentials([usernamePassword(
          credentialsId: "${DOCKER_CREDS_ID}",
          usernameVariable: 'DOCKER_USER',
          passwordVariable: 'DOCKER_PASS'
        )]) {
          sh '''
            set -eux
            echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
          '''
        }
      }
    }

    // ================== BUILD ==================
    // Asume que cada micro tiene su Dockerfile en su carpeta:
    //   ./gateway-service, ./pedidos-service, ./usuarios-service
    stage('Build images') {
      steps {
        sh '''
          set -eux

          docker build -t ${REGISTRY}/${REGISTRY_NS}/gateway-service:${IMAGE_TAG}   ./gateway-service
          docker build -t ${REGISTRY}/${REGISTRY_NS}/pedidos-service:${IMAGE_TAG}   ./pedidos-service
          docker build -t ${REGISTRY}/${REGISTRY_NS}/usuarios-service:${IMAGE_TAG}  ./usuarios-service

          # Etiquetas 'latest' además del tag por commit
          for svc in gateway-service pedidos-service usuarios-service; do
            docker tag ${REGISTRY}/${REGISTRY_NS}/$svc:${IMAGE_TAG} ${REGISTRY}/${REGISTRY_NS}/$svc:latest
          done

          docker images | grep ${REGISTRY_NS} || true
        '''
      }
    }

    // ================== PUSH ==================
    stage('Push images') {
      steps {
        sh '''
          set -eux
          for svc in gateway-service pedidos-service usuarios-service; do
            docker push ${REGISTRY}/${REGISTRY_NS}/$svc:${IMAGE_TAG}
            docker push ${REGISTRY}/${REGISTRY_NS}/$svc:latest
          done
        '''
      }
    }

    // ================== DEPLOY LOCAL ==================
    // Requiere que el agente tenga acceso a Docker del host
    // (si Jenkins corre en Docker, lanzarlo con -v /var/run/docker.sock:/var/run/docker.sock y -u 0 --privileged)
    stage('Deploy (Docker local)') {
      when { branch 'main' }   // despliega sólo en main
      steps {
        sh '''
          set -eux

          # 1) Red común
          docker network create ${NET_NAME} || true

          # Función helper para recrear contenedores
          recreate() {
            local name="$1"
            shift
            docker ps -a --filter "name=${name}" --format "{{.ID}}" | xargs -r docker stop
            docker ps -a --filter "name=${name}" --format "{{.ID}}" | xargs -r docker rm
            docker run -d --name "${name}" --network ${NET_NAME} "$@"
          }

          # 2) Usuarios (expuesto host:${PORT_USUARIOS} -> container:8080)
          docker pull ${REGISTRY}/${REGISTRY_NS}/usuarios-service:latest
          recreate ${C_USUARIOS} -p ${PORT_USUARIOS}:8080 ${REGISTRY}/${REGISTRY_NS}/usuarios-service:latest

          # 3) Pedidos (expuesto host:${PORT_PEDIDOS} -> container:8080)
          docker pull ${REGISTRY}/${REGISTRY_NS}/pedidos-service:latest
          recreate ${C_PEDIDOS} -p ${PORT_PEDIDOS}:8080 ${REGISTRY}/${REGISTRY_NS}/pedidos-service:latest

          # 4) Gateway (expuesto host:${PORT_GATEWAY} -> container:8080)
          #    Pasa URLs internas resolviendo por nombre en la red Docker
          docker pull ${REGISTRY}/${REGISTRY_NS}/gateway-service:latest
          recreate ${C_GATEWAY} \
            -p ${PORT_GATEWAY}:8080 \
            -e USUARIOS_URL=http://${C_USUARIOS}:8080 \
            -e PEDIDOS_URL=http://${C_PEDIDOS}:8080 \
            ${REGISTRY}/${REGISTRY_NS}/gateway-service:latest

          # 5) Pequeña verificación
          sleep 3
          docker ps --format "table {{.Names}}\\t{{.Image}}\\t{{.Ports}}\\t{{.Status}}"
        '''
      }
    }

    // (opcional) Smoke tests rápidos después de deploy
    stage('Smoke tests') {
      when { branch 'main' }
      steps {
        sh '''
          set -eux
          # Ajusta los paths si difieren
          curl -fsS http://localhost:${PORT_USUARIOS}/actuator/health >/dev/null
          curl -fsS http://localhost:${PORT_PEDIDOS}/actuator/health  >/dev/null
          curl -fsS http://localhost:${PORT_GATEWAY}/actuator/health  >/dev/null
        '''
      }
    }
  }

  post {
    always { sh 'docker logout || true' }
    success { echo "OK: Deploy local actualizado. Tags ${IMAGE_TAG} y latest publicados." }
    failure { echo "Falló el pipeline. Revisa la etapa en rojo." }
  }
}
