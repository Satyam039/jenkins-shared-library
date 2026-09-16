def call(Map config = [:]) {

    def appName = config.get('appName', 'node-app')

    pipeline {

        agent any

        environment {
            APP_NAME = "${appName}"
        }

        options {
            timestamps()
            timeout(time: 30, unit: 'MINUTES')
            disableConcurrentBuilds()
        }

        stages {

            stage('Checkout') {
                steps {
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: '*/main']],
                        userRemoteConfigs: [[
                            url: 'https://github.com/Satyam039/website-downtime-alert.git',
                            credentialsId: 'github-creds'
                        ]]
                    ])
                }
            }

            stage('Environment') {
                steps {
                    sh '''
                        echo "Application: $APP_NAME"
                        echo "Node Version:"
                        node --version
                        echo "NPM Version:"
                        npm --version
                    '''
                }
            }

            stage('Install') {
                steps {
                    sh 'npm ci'
                }
            }

            stage('Lint') {
                steps {
                    sh 'npm run lint --if-present'
                }
            }

            stage('Test') {
                steps {
                    sh 'npm test --if-present'
                }
            }

            stage('Build') {
                steps {
                    sh 'npm run build --if-present'
                }
            }
        }

        post {

            success {
                echo "BUILD SUCCESS: ${APP_NAME}"
            }

            failure {
                echo "BUILD FAILED: ${APP_NAME}"
            }

            always {
                cleanWs()
            }
        }
    }
}
