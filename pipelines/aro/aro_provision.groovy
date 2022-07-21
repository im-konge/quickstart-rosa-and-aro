def STRIMZI_TOOLS = "strimzi-tools"
def QUICKSTART_DIR = "quickstart-rosa-and-aro"

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
        ARO_TEMPLATE_PATH = "examples/aro-configuration/template.json"
        ARO_PARAMETERS_PATH = "examples/aro-configuration/parameters.json"
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
        stage('Checkout quickstart-rosa-and-aro repository') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh("git clone https://github.com/im-konge/quickstart-rosa-and-aro.git --depth 1")
                    }
                }
            }
        }
        stage('Login to Azure') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh("az login --service-principal -u ${env.SR_USERNAME} -p ${env.SR_PASSWORD} --tenant ${env.AZ_TENANT_ID}")
                    }
                }
            }
        }
        stage('Install cluster') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        dir(QUICKSTART_DIR) {
                            sh("az deployment group create" +
                                " --name ${env.CLUSTER_NAME}" +
                                " --template-file ${env.ARO_TEMPLATE_PATH}" +
                                " --parameters ${env.ARO_PARAMETERS_PATH}" +
                                " --resource-group ${env.RESOURCE_GROUP}" +
                                " --parameters aadClientSecret=${env.SR_PASSWORD} pullSecret=${env.PULL_SECRET} " +
                                " clusterName=${env.CLUSTER_NAME} domain=${env.DOMAIN} aadClientId=${env.SR_USERNAME} " +
                                " location=${env.REGION} clusterVnetName=${env.AZ_VNET} rpObjectId=${env.RP_OBJECT_ID} aadObjectId=${env.AZ_OBJECT_ID}")
                        }
                    }
                }
            }
        }
        stage('Get cluster description') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh("az aro show --name ${env.CLUSTER_NAME} --resource-group ${env.RESOURCE_GROUP} > aro_info.txt")
                    }
                }
            }
        }
        stage('Get admin credentials and URLs') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        env.ADMIN_USER = "kubeadmin"
                        env.ADMIN_PASS = sh(script: "az aro list-credentials --name ${env.CLUSTER_NAME} " +
                            "--resource-group ${env.RESOURCE_GROUP} | jq -r '.kubeadminPassword'", returnStdout: true).trim()

                        env.API_URL = sh(script: "cat aro_info.txt | jq -r '.apiserverProfile.url'", returnStdout: true).trim()
                        env.CONSOLE_URL = sh(script: "cat aro_info.txt | jq -r '.consoleProfile.url'", returnStdout: true).trim()

                        println("[INFO] username: ${env.ADMIN_USER}")
                        println("[INFO] password: ${env.ADMIN_PASS}")
                        println("[INFO] API url: ${env.API_URL}")
                        println("[INFO] Console url: ${env.CONSOLE_URL}")
                    }
                }
            }
        }
    }
    post {
        always {
            container(STRIMZI_TOOLS) {
                script {
                    archiveArtifacts(artifacts: "aro_info.txt", onlyIfSuccessful: false)
                }
            }
        }
    }
}
