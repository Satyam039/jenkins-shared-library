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
        def runTests = config.get('runTests', true)
        def runBuild = config.get('runBuild', true)

        script.pipeline {

            agent any

            environment {
                APP_NAME = "${appName}"
            }

            options {
                script.timestamps()
                script.timeout(time: 30, unit: 'MINUTES')
                script.disableConcurrentBuilds()
            }

            stages {

                stage('Environment') {
                    steps {
                        script.sh '''
                            echo "================================"
                            echo "Application: $APP_NAME"
                            echo "================================"

                            echo "Operating System:"
                            uname -a

                            echo "Git:"
                            git --version || true

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
                        script.script {
                            def detected = language

                            if (language == 'auto') {
                                if (script.fileExists('package.json')) {
                                    detected = 'nodejs'
                                } else if (
                                    script.fileExists('pom.xml') ||
                                    script.fileExists('build.gradle')
                                ) {
                                    detected = 'java'
                                } else if (
                                    script.fileExists('requirements.txt') ||
                                    script.fileExists('pyproject.toml')
                                ) {
                                    detected = 'python'
                                } else {
                                    detected = 'unknown'
                                }
                            }

                            script.env.PROJECT_TYPE = detected

                            script.echo "Detected project type: ${detected}"
                        }
                    }
                }

                stage('Install Dependencies') {
                    steps {
                        script.script {
                            if (script.env.PROJECT_TYPE == 'nodejs') {

                                script.sh '''
                                    if [ -f package-lock.json ]; then
                                        npm ci
                                    else
                                        npm install
                                    fi
                                '''

                            } else if (script.env.PROJECT_TYPE == 'java') {

                                script.sh '''
                                    if [ -f mvnw ]; then
                                        chmod +x mvnw
                                        ./mvnw dependency:resolve
                                    elif [ -f pom.xml ]; then
                                        mvn dependency:resolve
                                    elif [ -f gradlew ]; then
                                        chmod +x gradlew
                                        ./gradlew dependencies
                                    fi
                                '''

                            } else if (script.env.PROJECT_TYPE == 'python') {

                                script.sh '''
                                    python3 -m venv .venv
                                    . .venv/bin/activate

                                    if [ -f requirements.txt ]; then
                                        pip install -r requirements.txt
                                    fi
                                '''

                            } else {
                                script.echo "No dependency installation configured."
                            }
                        }
                    }
                }

                stage('Test') {
                    when {
                        expression {
                            return runTests
                        }
                    }

                    steps {
                        script.script {

                            if (script.env.PROJECT_TYPE == 'nodejs') {

                                script.sh 'npm test --if-present -- --run'

                            } else if (script.env.PROJECT_TYPE == 'java') {

                                script.sh '''
                                    if [ -f mvnw ]; then
                                        ./mvnw test
                                    elif [ -f pom.xml ]; then
                                        mvn test
                                    elif [ -f gradlew ]; then
                                        ./gradlew test
                                    fi
                                '''

                            } else if (script.env.PROJECT_TYPE == 'python') {

                                script.sh '''
                                    . .venv/bin/activate
                                    python -m pytest || python -m unittest discover
                                '''

                            } else {
                                script.echo "No test command configured."
                            }
                        }
                    }
                }

                stage('Build') {
                    when {
                        expression {
                            return runBuild
                        }
                    }

                    steps {
                        script.script {

                            if (script.env.PROJECT_TYPE == 'nodejs') {

                                script.sh 'npm run build --if-present'

                            } else if (script.env.PROJECT_TYPE == 'java') {

                                script.sh '''
                                    if [ -f mvnw ]; then
                                        ./mvnw package -DskipTests
                                    elif [ -f pom.xml ]; then
                                        mvn package -DskipTests
                                    elif [ -f gradlew ]; then
                                        ./gradlew build -x test
                                    fi
                                '''

                            } else if (script.env.PROJECT_TYPE == 'python') {

                                script.sh '''
                                    echo "Python build stage completed."
                                '''

                            } else {
                                script.echo "No build command configured."
                            }
                        }
                    }
                }
            }

            post {

                success {
                    script.echo "BUILD SUCCESS: ${appName}"
                }

                failure {
                    script.echo "BUILD FAILED: ${appName}"
                }

                always {
                    script.cleanWs()
                }
            }
        }
    }
}
