#!/usr/bin/env groovy

def STRIMZI_TOOLS = "strimzi-tools"

pipeline {
    agent {
        node {
            label(STRIMZI_TOOLS)
        }
    }
    parameters {
        string(name: 'CLUSTER_NAME', defaultValue: "my-aro-cluster", description: "Name of the ROSA cluster")
        string(name: 'REGION', defaultValue: "us-east-2", description: "AWS region where is ROSA cluster installed")
        string(name: 'OCM_TOKEN', defaultValue: "", description: "OCM token")
        string(name: 'AWS_ACCESS_KEY_ID', defaultValue: "", description: "AWS access key ID")
        string(name: 'AWS_SECRET_ACCESS_KEY', defaultValue: "", description: "AWS secret access key")
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
                        sh("aws configure set default.default.aws_access_key_id ${params.AWS_ACCESS_KEY_ID}")
                        sh("aws configure set default.default.aws_secret_access_key ${params.AWS_SECRET_ACCESS_KEY}")
                        sh("aws configure set default.region ${params.REGION}")

                        sh("rosa login --token ${params.OCM_TOKEN}")
                    }
                }
            }
        }
        stage('Delete cluster') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh("rosa init")
                        sh("rosa delete cluster -c ${params.CLUSTER_NAME} -y")
                        sh("rosa logs uninstall -c ${params.CLUSTER_NAME} --watch")
                    }
                }
            }
        }
    }
}
