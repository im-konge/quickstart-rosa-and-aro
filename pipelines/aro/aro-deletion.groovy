#!/usr/bin/env groovy

def STRIMZI_TOOLS = "strimzi-tools"

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
        string(name: 'AZ_VNET', defaultValue: "", description: "Name of created VNET for ARO cluster")
        string(name: 'RESOURCE_GROUP', defaultValue: "", description: "Resource group name")
        string(name: 'REGION', defaultValue: "eastus", description: "Region, where the watcher will be disabled (and where the cluster is installed)")
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
        stage('Login to Azure') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        sh("az login --service-principal -u ${params.SR_USERNAME} -p ${params.SR_PASSWORD} --tenant ${params.AZ_TENANT_ID}")
                    }
                }
            }
        }
        stage('Delete cluster') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        // because there is a bug when using `az deployment group delete` we need to go this way
                        sh("az aro delete --name ${params.CLUSTER_NAME} --resource-group ${params.RESOURCE_GROUP} -y")
                        sh("az network vnet delete --name ${params.AZ_VNET} --resource-group ${params.RESOURCE_GROUP}")
                        sh("az network watcher configure --resource-group NetworkWatcherRG --locations ${params.REGION} --enabled false")
                    }
                }
            }
        }
    }
}
