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
                        sh("aws configure set default.aws_access_key_id ${env.AWS_ACCESS_KEY_ID}")
                        sh("aws configure set default.aws_secret_access_key ${env.AWS_SECRET_ACCESS_KEY}")
                        sh("aws configure set default.region ${env.REGION}")

                        sh("rosa login --token ${env.OCM_TOKEN}")
                    }
                }
            }
        }
        stage('Install cluster') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh("rosa init")
                        sh("rosa create cluster -c ${env.CLUSTER_NAME}")
                        sh("rosa logs install -c ${env.CLUSTER_NAME} --watch")
                    }
                }
            }
        }
        stage('Get cluster description') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh("rosa describe cluster -c ${env.CLUSTER_NAME} > rosa_cluster_desc.txt")
                    }
                }
            }
        }
        stage('Create admin user') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh("rosa create admin -c ${env.CLUSTER_NAME} > rosa_login.txt")
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
        stage("Login to ROSA cluster") {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh(script: "oc login -u ${env.ADMIN_USER} -p ${env.ADMIN_PASS} ${env.API_URL}")
                    }
                }
            }
        }
        stage("Update pull-secret for ROSA cluster") {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh(script: "oc set data secret/pull-secret -n openshift-config .dockerconfigjson='${env.PULL_SECRET}'")
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