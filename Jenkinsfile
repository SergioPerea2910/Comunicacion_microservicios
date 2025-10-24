pipeline {
    agent {
        label 'linux'  // Ejecutar en agente Linux
    }

    environment {
        // Variables de entorno
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'  // Ajustar según tu versión de Java
        PATH = "${JAVA_HOME}/bin:${PATH}"
        MAVEN_OPTS = '-Xmx2048m'
        GRADLE_OPTS = '-Xmx2048m -Dorg.gradle.daemon=false'

        // Variables para Docker (si usas contenedores)
        DOCKER_REGISTRY = 'your-registry-url'
        IMAGE_NAME = 'comunicacion-microservicios'
        IMAGE_TAG = "${BUILD_NUMBER}"
    }

    tools {
        // Definir herramientas (ajustar versiones según tu configuración)
        maven 'Maven-3.9'      // Nombre configurado en Jenkins Global Tools
        jdk 'JDK-17'          // Nombre configurado en Jenkins Global Tools
        // gradle 'Gradle-7.6'  // Descomentar si usas Gradle
    }

    options {
        // Opciones del pipeline
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        skipStagesAfterUnstable()
        disableConcurrentBuilds()
    }

    triggers {
        // Trigger automático en commits
        pollSCM('H/5 * * * *')  // Revisar cada 5 minutos
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    echo "🚀 Iniciando pipeline para Comunicación Microservicios"
                    echo "Branch: ${env.BRANCH_NAME}"
                    echo "Build: ${env.BUILD_NUMBER}"
                }

                // Checkout del código
                checkout scm

                // Limpiar workspace anterior
                sh 'find . -name "target" -type d -exec rm -rf {} + 2>/dev/null || true'
                sh 'find . -name "build" -type d -exec rm -rf {} + 2>/dev/null || true'
            }
        }

        stage('Environment Setup') {
            steps {
                script {
                    // Detectar tipo de build tool
                    if (fileExists('pom.xml')) {
                        env.BUILD_TOOL = 'maven'
                        echo "📦 Detectado proyecto Maven"
                    } else if (fileExists('build.gradle') || fileExists('build.gradle.kts')) {
                        env.BUILD_TOOL = 'gradle'
                        echo "📦 Detectado proyecto Gradle"
                    } else {
                        error "❌ No se encontró pom.xml ni build.gradle"
                    }
                }

                // Verificar versiones
                sh '''
                    echo "🔍 Verificando entorno:"
                    java -version
                    echo "Java Home: $JAVA_HOME"

                    if [ "$BUILD_TOOL" = "maven" ]; then
                        mvn -version
                    elif [ "$BUILD_TOOL" = "gradle" ]; then
                        ./gradlew --version || gradle --version
                    fi
                '''
            }
        }

        stage('Build') {
            steps {
                script {
                    if (env.BUILD_TOOL == 'maven') {
                        echo "🏗️ Compilando con Maven..."
                        sh '''
                            mvn clean compile -B \
                                -Dmaven.test.skip=true \
                                -Dspring.profiles.active=test
                        '''
                    } else if (env.BUILD_TOOL == 'gradle') {
                        echo "🏗️ Compilando con Gradle..."
                        sh '''
                            chmod +x ./gradlew
                            ./gradlew clean compileJava -x test \
                                --no-daemon \
                                --parallel \
                                --build-cache
                        '''
                    }
                }
            }
        }

         stage('Dependency Check') {
             steps {
                 script {
                      echo "🔍 Verificando dependencias..."
                            if (env.BUILD_TOOL == 'maven') {
                                sh 'mvn dependency-check:check || true'
                            } else if (env.BUILD_TOOL == 'gradle') {
                                sh './gradlew dependencyCheckAnalyze || true'
                            }
                        }
                    }
                }
            }
        }

        stage('Package') {
            steps {
                script {
                    if (env.BUILD_TOOL == 'maven') {
                        echo "📦 Empaquetando con Maven..."
                        sh '''
                            mvn package -B \
                                -DskipTests=true \
                                -Dspring.profiles.active=production
                        '''
                    } else if (env.BUILD_TOOL == 'gradle') {
                        echo "📦 Empaquetando con Gradle..."
                        sh '''
                            ./gradlew bootJar \
                                --no-daemon \
                                -x test
                        '''
                    }
                }

                // Archivar artefactos
                archiveArtifacts artifacts: '**/target/*.jar,**/build/libs/*.jar',
                                fingerprint: true,
                                allowEmptyArchive: false
            }
        }

        stage('Docker Build') {
            when {
                anyOf {
                    branch 'main'
                    branch 'master'
                    branch 'develop'
                }
            }
            steps {
                script {
                    if (fileExists('Dockerfile')) {
                        echo "🐳 Construyendo imagen Docker..."
                        sh '''
                            docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                            docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest
                        '''

                        // Escaneo de seguridad de la imagen (opcional)
                        sh '''
                            docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
                                -v $(pwd):/root/.cache/ \
                                aquasec/trivy:latest image ${IMAGE_NAME}:${IMAGE_TAG} || true
                        '''
                    } else {
                        echo "⚠️ No se encontró Dockerfile, saltando construcción de imagen"
                    }
                }
            }
        }


        stage('Deploy to Production') {
            when {
                anyOf {
                    branch 'main'
                }
            }
            steps {
                script {
                    // Confirmación manual para producción
                    timeout(time: 10, unit: 'MINUTES') {
                        input message: '¿Desplegar a producción?',
                              ok: 'Desplegar',
                              submitterParameter: 'DEPLOYER'
                    }

                    echo "🚀 Desplegando a Producción..."
                    echo "Desplegado por: ${env.DEPLOYER}"

                    // Comandos de despliegue a producción
                    // sh './deploy-production.sh'

                    echo "✅ Aplicación desplegada en producción"
                }
            }
        }
    }

    post {
        always {
            // Limpieza
            echo "🧹 Limpiando workspace..."

            // Limpiar imágenes Docker locales
            sh '''
                docker system prune -f || true
                docker image prune -f || true
            '''

            // Limpiar archivos temporales
            sh 'find . -name "*.tmp" -delete || true'

            cleanWs()
        }

        success {
            echo "✅ Pipeline ejecutado exitosamente!"

            // Notificación de éxito (configurar según tus necesidades)
            // slackSend channel: '#deployments',
            //           color: 'good',
            //           message: "✅ Deploy exitoso: ${env.JOB_NAME} - ${env.BUILD_NUMBER}"

            // emailext to: 'team@company.com',
            //          subject: "✅ Build Success: ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
            //          body: "El build se completó exitosamente."
        }

        failure {
            echo "❌ Pipeline falló!"

            // Notificación de fallo
            // slackSend channel: '#deployments',
            //           color: 'danger',
            //           message: "❌ Deploy falló: ${env.JOB_NAME} - ${env.BUILD_NUMBER}"

            // emailext to: 'team@company.com',
            //          subject: "❌ Build Failed: ${env.JOB_NAME} - ${env.BUILD_NUMBER}",
            //          body: "El build falló. Revisar logs: ${env.BUILD_URL}"
        }

        unstable {
            echo "⚠️ Pipeline inestable (tests fallaron pero build OK)"
        }

        changed {
            echo "🔄 Estado del pipeline cambió desde el último build"
        }
    }
}