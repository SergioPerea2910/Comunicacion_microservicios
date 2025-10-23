pipeline {
    agent any

    stages {
        stage('Check Docker') {
            steps {
                echo 'Validating Docker access...'
                sh 'docker version'
            }
        }

        stage('Build Images') {
            steps {
                echo 'Building microservice images...'
                sh 'make build TAG=${BUILD_NUMBER}'
            }
        }

        stage('Push to DockerHub') {
            steps {
                echo 'Pushing images to DockerHub...'
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-creds',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin'
                    sh 'make push TAG=${BUILD_NUMBER}'
                }
            }
        }
    }
}
