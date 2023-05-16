pipeline {
    agent {
      docker {
        image 'eclipse-temurin:17-alpine'
      }
    }

    stages {
        stage('Install dependencies') {
            steps {
                sh 'apk add make'
            }
        }
        stage('Tests') {
            steps {
                sh 'make run_tests'
            }
        }
    }
}
