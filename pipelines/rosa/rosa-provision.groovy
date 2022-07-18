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
        string(name: 'REGION', defaultValue: "us-east-2", description: "AWS region where ROSA cluster should be installed")
        string(name: 'OCM_TOKEN', defaultValue: "", description: "OCM token")
        string(name: 'PULL_SECRET', defaultValue: "", description: "Pull secret for the ROSA cluster - enable pulling from RH registries f.e.")
        string(name: 'AWS_ACCESS_KEY_ID', defaultValue: "", description: "AWS access key ID")
        string(name: 'AWS_SECRET_ACCESS_KEY', defaultValue: "", description: "AWS secret access key")
    }
    environment {
        AWS_CREDENTIALS_FILE_PATH = "~/.aws/credentials"
    }
    options {
        timeout(time: 3, unit: 'HOURS')
        ansiColor('xterm')
        timestamps()
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
        stage('Install cluster') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh("rosa init")
                        sh("rosa create cluster -c ${params.CLUSTER_NAME}")
                        sh("rosa logs install -c ${params.CLUSTER_NAME} --watch")
                    }
                }
            }
        }
        stage('Get cluster description') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh("rosa describe cluster -c ${params.CLUSTER_NAME} > rosa_cluster_desc.txt")
                    }
                }
            }
        }
        stage('Create admin user') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh("rosa create admin -c ${params.CLUSTER_NAME} > rosa_login.txt")
                    }
                }
            }
        }
        stage('Log into ROSA cluster') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        env.ADMIN_USER = sh(script: "cat rosa_login.txt | grep -o \"\\-\\-username [a-zA-Z0-9\\-]*\" | sed 's/--username //g'", returnStdout: true).trim()
                        env.ADMIN_PASS = sh(script: "cat rosa_login.txt | grep -o \"\\-\\-password [a-zA-Z0-9\\-]*\" | sed 's/--password //g'", returnStdout: true).trim()
                        env.API_URL = sh(script: "cat rosa_login.txt | grep -o \"https:.*6443\"", returnStdout: true).trim()

                        println("[INFO] username: ${env.ADMIN_USER}")
                        println("[INFO] password: ${env.ADMIN_PASS}")
                        println("[INFO] API url: ${env.API_URL}")
                    }
                }
            }
        }
        stage("Update pull-secret for ROSA cluster") {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh(script: "oc set data secret/pull-secret -n openshift-config .dockerconfigjson=${params.PULL_SECRET}")
                    }
                }
            }
        }
    }
    post {
        always {
            container(STRIMZI_TOOLS) {
                script {
                    archiveArtifacts(artifacts: "rosa_login.txt", onlyIfSuccessful: false)
                    archiveArtifacts(artifacts: "rosa_cluster_desc.txt", onlyIfSuccessful: false)
                }
            }
        }
    }
}
