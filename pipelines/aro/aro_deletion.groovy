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
                        sh("az login --service-principal -u ${env.SR_USERNAME} -p ${env.SR_PASSWORD} --tenant ${env.AZ_TENANT_ID}")
                    }
                }
            }
        }
        stage('Delete cluster') {
            steps {
                container(STRIMZI_TOOLS) {
                    script {
                        // because there is a bug when using `az deployment group delete` we need to go this way
                        sh("az aro delete --name ${env.CLUSTER_NAME} --resource-group ${env.RESOURCE_GROUP} -y")
                        sh("az network vnet delete --name ${env.AZ_VNET} --resource-group ${env.RESOURCE_GROUP}")
                        sh("az network watcher configure --resource-group NetworkWatcherRG --locations ${env.REGION} --enabled false")
                    }
                }
            }
        }
    }
}
