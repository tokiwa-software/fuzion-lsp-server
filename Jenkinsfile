pipeline {

    options {
        ansiColor('xterm')
    }

    triggers {
        cron('H H(19-23) * * 0-5')
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
