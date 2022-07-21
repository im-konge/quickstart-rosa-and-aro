#!/usr/bin/env groovy

def STRIMZI_TOOLS = "strimzi-tools"

pipeline {
    agent {
        kubernetes {
            yaml '''
            spec:
                containers:
                - image: "quay.io/rh_integration/strimzi-tools:latest"
                  name: "${STRIMZI_TOOLS}"
                  workingDir: "/home/jenkins"
                  command: "sleep 99d"
                  args: ""
                  resourceLimitMemory: "4Gi"
                  resourceRequestMemory: "2Gi"
                  resourceLimitCpu: "2"
                  alwaysPullImage: true
                  runAsUser: 1000
            '''
        }
    }
    environment {
        AWS_CREDENTIALS_FILE_PATH = "~/.aws/credentials"
    }
    options {
        timeout(time: 3, unit: 'HOURS')
        ansiColor('xterm')
    }
    stages {
        stage('Clean') {
            steps {
                cleanWs()
            }
        }
        stage('Get and check secrets') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        // Configure AWS credentials and region needed for ROSA installation
                        sh("aws configure set default.default.aws_access_key_id ${env.AWS_ACCESS_KEY_ID}")
                        sh("aws configure set default.default.aws_secret_access_key ${env.AWS_SECRET_ACCESS_KEY}")
                        sh("aws configure set default.region ${env.REGION}")

                        sh("rosa login --token ${env.OCM_TOKEN}")
                    }
                }
            }
        }
        stage('Delete cluster') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh("rosa init")
                        sh("rosa delete cluster -c ${env.CLUSTER_NAME} -y")
                        sh("rosa logs uninstall -c ${env.CLUSTER_NAME} --watch")
                    }
                }
            }
        }
    }
}
