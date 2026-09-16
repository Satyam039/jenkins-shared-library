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

            stage('Environment') {
                steps {
                    sh '''
                        echo "Application: $APP_NAME"
                        node --version
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
        sh '''
            npm test --if-present -- --run
        '''
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
