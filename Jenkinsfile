pipeline {
  agent any
  options { timestamps() }

  // Disparadores
  triggers {
    githubPush()              // webhook GitHub (ngrok)
    // pollSCM('H/5 * * * *') // opcional: respaldo por polling
  }

  environment {
    // Registry
    REGISTRY        = 'docker.io'
    REGISTRY_NS     = 'rojassluu'
    DOCKER_CREDS_ID = 'dockerhub-creds'

    // Tag de build: SHA corto del commit (y también publicamos 'latest')
    COMMIT_SHA      = "${env.GIT_COMMIT?.take(7) ?: 'dev'}"
    IMAGE_TAG       = "${COMMIT_SHA}"

    // Puertos host (los contenedores escuchan 8080 dentro)
    PORT_USUARIOS   = '8082'
    PORT_PEDIDOS    = '8081'
    PORT_GATEWAY    = '8083'

    // Nombres de contenedor
    C_USUARIOS      = 'usuarios'
    C_PEDIDOS       = 'pedidos'
    C_GATEWAY       = 'gateway'

    // Red Docker para comunicación interna
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

    // ---------- BUILD ----------
    // Asume que hay un Dockerfile en:
    // ./gateway-service, ./pedidos-service, ./usuarios-service
    stage('Build images') {
      steps {
        sh '''
          set -eux

          docker build -t ${REGISTRY}/${REGISTRY_NS}/gateway-service:${IMAGE_TAG}   ./gateway-service
          docker build -t ${REGISTRY}/${REGISTRY_NS}/pedidos-service:${IMAGE_TAG}   ./pedidos-service
          docker build -t ${REGISTRY}/${REGISTRY_NS}/usuarios-service:${IMAGE_TAG}  ./usuarios-service

          # Etiqueta adicional 'latest'
          for svc in gateway-service pedidos-service usuarios-service; do
            docker tag ${REGISTRY}/${REGISTRY_NS}/$svc:${IMAGE_TAG} ${REGISTRY}/${REGISTRY_NS}/$svc:latest
          done

          docker images | grep ${REGISTRY_NS} || true
        '''
      }
    }

    // ---------- PUSH ----------
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

    // ---------- DEPLOY CONTINUO (sin zero-downtime) ----------
    // Requiere Jenkins con acceso al Docker host:
    // -v /var/run/docker.sock:/var/run/docker.sock  y  --privileged
    stage('Deploy (Docker local) - Continuous') {
      when { branch 'main' }   // despliega solo en main
      steps {
        sh '''
          set -euo pipefail

          DEPLOY_TAG="${IMAGE_TAG:-latest}"   # o fuerza "latest" si prefieres

          # 1) Red común
          docker network create "${NET_NAME}" >/dev/null 2>&1 || true

          # helper: parar/eliminar y volver a crear
          recreate() {
            local name="$1"; shift
            docker ps -a --filter "name=^${name}$" --format "{{.ID}}" | xargs -r docker stop
            docker ps -a --filter "name=^${name}$" --format "{{.ID}}" | xargs -r docker rm
            docker run -d --name "${name}" --network "${NET_NAME}" "$@"
          }

          # helper: healthcheck con reintentos
          hc() {
            local url="$1"
            for i in $(seq 1 25); do
              if curl -fsS "${url}" >/dev/null 2>&1; then
                echo "OK ${url}"
                return 0
              fi
              echo "Esperando ${url} (${i}/25) ..."
              sleep 1
            done
            echo "FALLO healthcheck: ${url}" >&2
            return 1
          }

          echo "=== Deploy continuo con tag ${DEPLOY_TAG} ==="

          # 2) USUARIOS (host:${PORT_USUARIOS} -> cont:8080)
          docker pull ${REGISTRY}/${REGISTRY_NS}/usuarios-service:${DEPLOY_TAG}
          recreate "${C_USUARIOS}" -p ${PORT_USUARIOS}:8080 \
            ${REGISTRY}/${REGISTRY_NS}/usuarios-service:${DEPLOY_TAG}
          hc "http://localhost:${PORT_USUARIOS}/actuator/health" || exit 1

          # 3) PEDIDOS (host:${PORT_PEDIDOS} -> cont:8080)
          docker pull ${REGISTRY}/${REGISTRY_NS}/pedidos-service:${DEPLOY_TAG}
          recreate "${C_PEDIDOS}" -p ${PORT_PEDIDOS}:8080 \
            ${REGISTRY}/${REGISTRY_NS}/pedidos-service:${DEPLOY_TAG}
          hc "http://localhost:${PORT_PEDIDOS}/actuator/health" || exit 1

          # 4) GATEWAY (host:${PORT_GATEWAY} -> cont:8080)
          docker pull ${REGISTRY}/${REGISTRY_NS}/gateway-service:${DEPLOY_TAG}
          recreate "${C_GATEWAY}" -p ${PORT_GATEWAY}:8080 \
            -e USUARIOS_URL=http://${C_USUARIOS}:8080 \
            -e PEDIDOS_URL=http://${C_PEDIDOS}:8080 \
            ${REGISTRY}/${REGISTRY_NS}/gateway-service:${DEPLOY_TAG}
          hc "http://localhost:${PORT_GATEWAY}/actuator/health" || exit 1

          echo "=== Contenedores desplegados ==="
          docker ps --format "table {{.Names}}\\t{{.Image}}\\t{{.Ports}}\\t{{.Status}}"
        '''
      }
    }

    // (opcional) Smoke rápido vía gateway
    stage('Smoke tests') {
      when { branch 'main' }
      steps {
        sh '''
          set -eux
          curl -fsS http://localhost:${PORT_GATEWAY}/actuator/health >/dev/null
          # Ajusta paths reales si quieres probar endpoints de negocio:
          # curl -fsS http://localhost:${PORT_GATEWAY}/api/usuarios >/dev/null || true
          # curl -fsS http://localhost:${PORT_GATEWAY}/api/pedidos  >/dev/null || true
        '''
      }
    }
  }

  post {
    always  { sh 'docker logout || true' }
    success { echo "OK: Build/Push/Deploy completado. Tag ${IMAGE_TAG} + latest publicados." }
    failure { echo "Falló el pipeline. Revisa la etapa marcada en rojo." }
  }
}
