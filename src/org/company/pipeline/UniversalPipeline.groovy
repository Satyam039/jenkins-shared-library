package org.company.pipeline

class UniversalPipeline implements Serializable {

    def script
    Map config

    UniversalPipeline(def script, Map config = [:]) {
        this.script = script
        this.config = config
    }

    def run() {

        def appName = config.get('appName', 'application')
        def language = config.get('language', 'auto')

        script.pipeline {

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
                            echo "Node:"
                            node --version || true
                            echo "Java:"
                            java -version || true
                            echo "Python:"
                            python3 --version || true
                        '''
                    }
                }

                stage('Detect Project') {
                    steps {
                        script {
                            if (language == 'auto') {

                                if (fileExists('package.json')) {
                                    env.PROJECT_TYPE = 'nodejs'

                                } else if (fileExists('pom.xml') || fileExists('build.gradle')) {
                                    env.PROJECT_TYPE = 'java'

                                } else if (fileExists('requirements.txt') || fileExists('pyproject.toml')) {
                                    env.PROJECT_TYPE = 'python'

                                } else {
                                    env.PROJECT_TYPE = 'unknown'
                                }

                            } else {
                                env.PROJECT_TYPE = language
                            }

                            echo "Detected Project: ${env.PROJECT_TYPE}"
                        }
                    }
                }

                stage('Install') {
                    steps {
                        script {

                            if (env.PROJECT_TYPE == 'nodejs') {

                                sh '''
                                    if [ -f package-lock.json ]; then
                                        npm ci
                                    else
                                        npm install
                                    fi
                                '''

                            } else if (env.PROJECT_TYPE == 'java') {

                                sh '''
                                    if [ -f pom.xml ]; then
                                        mvn dependency:resolve
                                    elif [ -f build.gradle ]; then
                                        ./gradlew dependencies
                                    fi
                                '''

                            } else if (env.PROJECT_TYPE == 'python') {

                                sh '''
                                    python3 -m venv .venv
                                    . .venv/bin/activate

                                    if [ -f requirements.txt ]; then
                                        pip install -r requirements.txt
                                    fi
                                '''

                            } else {
                                echo "No installation step configured."
                            }
                        }
                    }
                }

                stage('Test') {
                    steps {
                        script {

                            if (env.PROJECT_TYPE == 'nodejs') {

                                sh 'npm test --if-present -- --run'

                            } else if (env.PROJECT_TYPE == 'java') {

                                sh '''
                                    if [ -f pom.xml ]; then
                                        mvn test
                                    elif [ -f build.gradle ]; then
                                        ./gradlew test
                                    fi
                                '''

                            } else if (env.PROJECT_TYPE == 'python') {

                                sh '''
                                    . .venv/bin/activate
                                    python -m pytest
                                '''

                            } else {
                                echo "No test step configured."
                            }
                        }
                    }
                }

                stage('Build') {
                    steps {
                        script {

                            if (env.PROJECT_TYPE == 'nodejs') {

                                sh 'npm run build --if-present'

                            } else if (env.PROJECT_TYPE == 'java') {

                                sh '''
                                    if [ -f pom.xml ]; then
                                        mvn package -DskipTests
                                    elif [ -f build.gradle ]; then
                                        ./gradlew build -x test
                                    fi
                                '''

                            } else if (env.PROJECT_TYPE == 'python') {

                                echo "Python build completed."

                            } else {
                                echo "No build step configured."
                            }
                        }
                    }
                }
            }

            post {

                success {
                    echo "BUILD SUCCESS: ${appName}"
                }

                failure {
                    echo "BUILD FAILED: ${appName}"
                }

                always {
                    cleanWs()
                }
            }
        }
    }
}
