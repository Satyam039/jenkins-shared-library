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

       stage('Checkout') {
    steps {
        withCredentials([
            usernamePassword(
                credentialsId: 'github-creds',
                usernameVariable: 'GIT_USERNAME',
                passwordVariable: 'GIT_TOKEN'
            )
        ]) {
            sh '''
                rm -rf ./*

                export GIT_ASKPASS="$WORKSPACE/git-askpass.sh"

                cat > "$GIT_ASKPASS" <<'EOF'
#!/bin/sh
case "$1" in
    *Username*) echo "$GIT_USERNAME" ;;
    *Password*) echo "$GIT_TOKEN" ;;
esac
EOF

                chmod +x "$GIT_ASKPASS"

                git -c credential.helper= clone \
                  --branch main \
                  --single-branch \
                  https://github.com/Satyam039/website-downtime-alert.git .

                rm -f "$GIT_ASKPASS"
            '''
        }
    }
}

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
