pipeline {
    agent {
        label 'api-test-agent && workstation && amd64'
    }

    options {
        skipDefaultCheckout(true)
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(
            logRotator(
                numToKeepStr: '20',
                artifactNumToKeepStr: '10'
            )
        )
    }

    parameters {
        choice(
            name: 'ENVIRONMENT',
            choices: ['qa', 'int', 'prod'],
            description: 'Environment against which the API tests will run'
        )
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm

                script {
                    env.CHECKED_OUT_COMMIT = sh(
                        script: 'git rev-parse HEAD',
                        returnStdout: true
                    ).trim()
                }

                echo "Checked out exact commit: ${env.CHECKED_OUT_COMMIT}"

                sh '''
                    git log -1 \
                      --pretty='Commit: %H%nAuthor: %an%nDate: %ad%nSubject: %s'
                '''
            }
        }

        stage('Configure Runtime') {
            steps {
                echo "Selected environment: ${params.ENVIRONMENT}"

                script {
                    if (params.ENVIRONMENT == 'qa') {
                        String serviceHost = 'host.docker.internal'

                        env.SERVICES_API_GATEWAY_BASE_URL =
                            "http://${serviceHost}:8000"
                        env.SERVICES_USER_SERVICE_BASE_URL =
                            "http://${serviceHost}:8001"
                        env.SERVICES_AUTH_SERVICE_BASE_URL =
                            "http://${serviceHost}:8002"
                        env.SERVICES_PRODUCT_SERVICE_BASE_URL =
                            "http://${serviceHost}:8003"
                        env.SERVICES_ORDER_SERVICE_BASE_URL =
                            "http://${serviceHost}:8004"
                        env.SERVICES_PAYMENT_SERVICE_BASE_URL =
                            "http://${serviceHost}:8005"
                        env.SERVICES_TEST_SUPPORT_SERVICE_BASE_URL =
                            "http://${serviceHost}:8006"
                    }
                }
            }
        }

        stage('Service Health Check') {
            when {
                expression {
                    params.ENVIRONMENT == 'qa'
                }
            }

            steps {
                sh 'bash ci/scripts/health-check.sh'
            }
        }

        stage('Test') {
            steps {
                sh '''
                    ./gradlew \
                      --no-daemon \
                      --stacktrace \
                      -Denvironment="${ENVIRONMENT}" \
                      clean test
                '''

                sh '''
                    test -d build/test-results/test

                    find build/test-results/test \
                      -name 'TEST-*.xml' \
                      -print \
                      -quit |
                    grep -q .
                '''
            }
        }
    }

    post {
        always {
            junit(
                testResults: 'build/test-results/test/*.xml',
                allowEmptyResults: true
            )

            archiveArtifacts(
                artifacts: 'build/reports/tests/test/**,build/test-results/test/**',
                allowEmptyArchive: true
            )
        }

        cleanup {
            cleanWs(
                deleteDirs: true,
                disableDeferredWipeout: true
            )
        }
    }
}