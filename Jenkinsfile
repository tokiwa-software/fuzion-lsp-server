pipeline {

    options {
        ansiColor('xterm')
    }

    agent {
      docker {
        image 'eclipse-temurin:21-alpine'
        args '-u root'
      }
    }

    stages {
        stage('Install dependencies') {
            steps {
                sh 'apk add make bash'
            }
        }
        stage('Tests') {
            steps {
                sh 'make run_tests'
            }
        }
    }
}
