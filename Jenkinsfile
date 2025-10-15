pipeline {
    agent {
        kubernetes {
            yaml '''
            apiVersion: v1
            kind: Pod
            metadata:
                name: jenkins-agent
            spec:
                containers:
                -   name: gradle
                    image: gradle:8.5-jdk21-alpine
                    command: ["cat"]
                    tty: true
                -   name: docker
                    image: docker:27.2.0-alpine3.20
                    command: ["cat"]
                    tty: true
                    volumeMounts:
                    -   name: docker-socket
                        mountPath: "/var/run/docker.sock"
                -   name: python
                    image: python:3.13.7-alpine3.22
                    command: ["cat"]
                    tty: true
                volumes:
                -   name: docker-socket
                    hostPath:
                        path: "/var/run/docker.sock"
            '''
        }
    }

    environment {
        JAVA_IMAGE_NAME = 'viroovr/specguard-api'
        PYTHON_IMAGE_NAME = 'viroovr/specguard-nlp'
        DOCKER_CREDENTIALS_ID = 'dockerhub-access'
        JAVA_DIR = 'be18-specguard-backend/backend'
        PYTHON_DIR = 'be18-specguard-backend/python-server'
    }

    stages {
        stage('Java Backend Build') {
            steps {
                container('gradle') {
                    dir("${JAVA_DIR}") {
                        sh 'chmod +x ./gradlew'
                        sh './gradlew clean bootJar'
                    }
                }
            }
        }

        stage('Python Server Build') {
            steps {
                container('python') {
                    dir("${PYTHON_DIR}") {
                        sh '''
                        pip install poetry
                        poetry install --no-root
                        echo "Python server build (dependency install) completed."
                        '''
                    }
                }
            }
        }

        /* ==========================
         * 3. Docker Build & Push
         * ========================== */
        stage('Build & Push Docker Images') {
            steps {
                container('docker') {
                    script {
                        def buildNumber = "${env.BUILD_NUMBER}"

                        sh 'docker logout || true'

                        withCredentials([usernamePassword(
                            credentialsId: DOCKER_CREDENTIALS_ID,
                            usernameVariable: 'DOCKER_USERNAME', 
                            passwordVariable: 'DOCKER_PASSWORD'
                        )]) {
                            sh 'echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin'
                        }

                        // === Java 이미지 빌드 ===
                        dir("${JAVA_DIR}") {
                            sh """
                            docker build --no-cache -t ${JAVA_IMAGE_NAME}:${buildNumber} .
                            docker push ${JAVA_IMAGE_NAME}:${buildNumber}
                            """
                        }

                        // === Python 이미지 빌드 ===
                        dir("${PYTHON_DIR}") {
                            sh """
                            docker build --no-cache -t ${PYTHON_IMAGE_NAME}:${buildNumber} .
                            docker push ${PYTHON_IMAGE_NAME}:${buildNumber}
                            """
                        }
                    }
                }
            }
        }

        stage('Trigger specguard-k8s-manifests') {
            steps {
                script {
                    def dockerImageVersion = "${env.BUILD_NUMBER}"

                    build job: 'specguard-k8s-manifests',
                        parameters: [
                            string(name: 'DOCKER_IMAGE_VERSION', value: dockerImageVersion)
                        ],
                        wait: true
                }
            }
        }
    }

    post {
        always {
            withCredentials([string(
                credentialsId: 'discord-webhook', 
                variable: 'DISCORD_WEBHOOK_URL'
            )]) {
                discordSend description: """
                💡 **SpecGuard CI/CD 알림**
                🔹 Job : ${env.JOB_NAME}
                🔹 Build : ${currentBuild.displayName}
                🔹 결과 : ${currentBuild.currentResult}
                🔹 실행 시간 : ${currentBuild.duration / 1000}s
                """,
                result: currentBuild.currentResult,
                title: "SpecGuard Pipeline : ${currentBuild.displayName}",
                webhookURL: "${DISCORD_WEBHOOK_URL}"
            }
        }
    }
}
