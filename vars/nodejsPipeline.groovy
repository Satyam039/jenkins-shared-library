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
                    sh '''
                        rm -rf ./*

                        curl -L \
                          https://github.com/Satyam039/website-downtime-alert/archive/refs/heads/main.tar.gz \
                          -o repo.tar.gz

                        tar -xzf repo.tar.gz --strip-components=1

                        rm -f repo.tar.gz

                        echo "Repository downloaded successfully"
                        ls -la
                    '''
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
