pipeline {
    agent any

    environment {
        REGISTRY = 'docker.io/lurojasy'
        TAG = 'latest'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/SergioPerea2910/Comunicacion_microservicios.git'
            }
        }

        stage('Build Docker Images') {
            steps {
                sh '''
                    docker build -t $REGISTRY/gateway-service:$TAG ./gateway-service
                    docker build -t $REGISTRY/pedidos-service:$TAG ./pedidos-service
                    docker build -t $REGISTRY/usuarios-service:$TAG ./usuarios-service
                '''
            }
        }

        stage('Push to DockerHub') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin
                        docker push $REGISTRY/gateway-service:$TAG
                        docker push $REGISTRY/pedidos-service:$TAG
                        docker push $REGISTRY/usuarios-service:$TAG
                    '''
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                sh '''
                    helm upgrade --install gateway ./helm/gateway
                    helm upgrade --install pedidos ./helm/pedidos
                    helm upgrade --install usuarios ./helm/usuarios
                '''
            }
        }
    }
}
