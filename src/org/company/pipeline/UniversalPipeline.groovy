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

        script.node {

            script.timestamps {

                script.timeout(time: 30, unit: 'MINUTES') {

                    try {

                        script.stage('Environment') {
                            script.echo "Application: ${appName}"

                            script.sh '''
                                echo "Node:"
                                node --version || true

                                echo "NPM:"
                                npm --version || true

                                echo "Java:"
                                java -version || true

                                echo "Python:"
                                python3 --version || true
                            '''
                        }

                        script.stage('Detect Project') {

                            if (language == 'auto') {

                                if (script.fileExists('package.json')) {
                                    script.env.PROJECT_TYPE = 'nodejs'

                                } else if (
                                    script.fileExists('pom.xml') ||
                                    script.fileExists('build.gradle')
                                ) {
                                    script.env.PROJECT_TYPE = 'java'

                                } else if (
                                    script.fileExists('requirements.txt') ||
                                    script.fileExists('pyproject.toml')
                                ) {
                                    script.env.PROJECT_TYPE = 'python'

                                } else {
                                    script.env.PROJECT_TYPE = 'unknown'
                                }

                            } else {
                                script.env.PROJECT_TYPE = language
                            }

                            script.echo "Detected Project: ${script.env.PROJECT_TYPE}"
                        }

                        script.stage('Install') {

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
                                    if [ -f pom.xml ]; then
                                        mvn dependency:resolve
                                    elif [ -f build.gradle ]; then
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
                            }
                        }

                        script.stage('Test') {

                            if (script.env.PROJECT_TYPE == 'nodejs') {

                                script.sh 'npm test --if-present -- --run'

                            } else if (script.env.PROJECT_TYPE == 'java') {

                                script.sh '''
                                    if [ -f pom.xml ]; then
                                        mvn test
                                    elif [ -f build.gradle ]; then
                                        ./gradlew test
                                    fi
                                '''

                            } else if (script.env.PROJECT_TYPE == 'python') {

                                script.sh '''
                                    . .venv/bin/activate
                                    python -m pytest
                                '''
                            }
                        }

                        script.stage('Build') {

                            if (script.env.PROJECT_TYPE == 'nodejs') {

                                script.sh 'npm run build --if-present'

                            } else if (script.env.PROJECT_TYPE == 'java') {

                                script.sh '''
                                    if [ -f pom.xml ]; then
                                        mvn package -DskipTests
                                    elif [ -f build.gradle ]; then
                                        ./gradlew build -x test
                                    fi
                                '''

                            } else if (script.env.PROJECT_TYPE == 'python') {

                                script.echo 'Python build completed.'
                            }
                        }

                        script.echo "BUILD SUCCESS: ${appName}"

                    } catch (Exception e) {

                        script.echo "BUILD FAILED: ${appName}"
                        throw e

                    } finally {

                        script.cleanWs()
                    }
                }
            }
        }
    }
}
