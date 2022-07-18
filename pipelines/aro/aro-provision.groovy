#!/usr/bin/env groovy

def STRIMZI_TOOLS = "strimzi-tools"
def QUICKSTART_DIR = "quickstart-rosa-and-aro"

pipeline {
    agent {
        node {
            label(STRIMZI_TOOLS)
        }
    }
    parameters {
        string(name: 'SR_USERNAME', defaultValue: "", description: "Service principal's username")
        string(name: 'SR_PASSWORD', defaultValue: "", description: "Service principal's password")
        string(name: 'AZ_TENANT_ID', defaultValue: "", description: "Azure tenant ID")
        string(name: 'CLUSTER_NAME', defaultValue: "my-aro-cluster", description: "Name of the ARO cluster")
        string(name: 'RESOURCE_GROUP', defaultValue: "", description: "Resource group name")
        string(name: 'DOMAIN', defaultValue: "myapp", description: "Domain for ARO cluster installation")
        string(name: 'REGION', defaultValue: "eastus", description: "'Location' of active directory and where the ARO cluster should be installed")
        string(name: 'PULL_SECRET', defaultValue: "", description: "Pull secret for the ARO cluster - enable pulling from RH registries f.e.")
        string(name: 'AZ_VNET', defaultValue: "", description: "Name of VNET for ARO cluster")
        string(name: 'RP_OBJECT_ID', defaultValue: "", description: "Object ID of 'Azure Red Hat OpenShift RP' service principal")
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
                        sh("az login --service-principal -u ${params.SR_USERNAME} -p ${params.SR_PASSWORD} --tenant ${params.AZ_TENANT_ID}")
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
                                " --name ${params.CLUSTER_NAME}" +
                                " --template-file ${env.ARO_TEMPLATE_PATH}" +
                                " --parameters ${env.ARO_PARAMETERS_PATH}" +
                                " --resource-group ${params.RESOURCE_GROUP}" +
                                " --parameters aadClientSecret=${params.SR_PASSWORD} pullSecret=${params.PULL_SECRET} " +
                                " clusterName=${params.CLUSTER_NAME} domain=${params.DOMAIN} aadClientId=${params.SR_USERNAME} " +
                                " location=${params.REGION} clusterVnetName=${params.AZ_VNET} rpObjectId=${params.RP_OBJECT_ID}")
                        }
                    }
                }
            }
        }
        stage('Get cluster description') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh("az aro show --name ${params.CLUSTER_NAME} --resource-group ${params.RESOURCE_GROUP} > aro_info.txt")
                    }
                }
            }
        }
        stage('Get admin credentials and URLs') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        env.ADMIN_USER = "kubeadmin"
                        env.ADMIN_PASS = sh(script: "az aro list-credentials --name ${params.CLUSTER_NAME} " +
                            "--resource-group ${params.RESOURCE_GROUP} | jq -r '.kubeadminPassword'", returnStdout: true).trim()

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
