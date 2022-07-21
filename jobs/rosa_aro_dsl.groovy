def folderName = 'rosa-and-aro'

folder(folderName) {
    displayName("ROSA and ARO")
}

pipelineJob("${folderName}/rosa_provision") {
    parameters {
        stringParam('CLUSTER_NAME', "my-aro-cluster", "Name of the ROSA cluster")
        stringParam('REGION', "us-east-2", "AWS region where ROSA cluster should be installed")
        stringParam('OCM_TOKEN', "", "OCM token")
        stringParam('PULL_SECRET', "", "Pull secret for the ROSA cluster - enable pulling from RH registries f.e.")
        stringParam('AWS_ACCESS_KEY_ID', "", "AWS access key ID")
        stringParam('AWS_SECRET_ACCESS_KEY', "", "AWS secret access key")
    }

    logRotator {
        numToKeep 10
    }

    definition {
        cps {
            script(readFileFromWorkspace('pipelines/rosa/rosa_provision.groovy'))
            sandbox()
        }
    }
}

pipelineJob("${folderName}/rosa_deletion") {
    parameters {
        stringParam('CLUSTER_NAME', "my-aro-cluster", "Name of the ROSA cluster")
        stringParam('REGION', "us-east-2", "AWS region where is ROSA cluster installed")
        stringParam('OCM_TOKEN', "", "OCM token")
        stringParam('AWS_ACCESS_KEY_ID', "", "AWS access key ID")
        stringParam('AWS_SECRET_ACCESS_KEY', "", "AWS secret access key")
    }

    logRotator {
        numToKeep 10
    }

    definition {
        cps {
            script(readFileFromWorkspace('pipelines/rosa/rosa_deletion.groovy'))
            sandbox()
        }
    }
}

pipelineJob("${folderName}/aro_provision") {
    parameters {
        stringParam('SR_USERNAME', "", "Service principal's username")
        stringParam('SR_PASSWORD', "", "Service principal's password")
        stringParam('AZ_TENANT_ID', "", "Azure tenant ID")
        stringParam('CLUSTER_NAME', "my-aro-cluster", "Name of the ARO cluster")
        stringParam('RESOURCE_GROUP', "", "Resource group name")
        stringParam('DOMAIN', "myapp", "Domain for ARO cluster installation")
        stringParam('REGION', "eastus", "'Location' of active directory and where the ARO cluster should be installed")
        stringParam('PULL_SECRET', "", "Pull secret for the ARO cluster - enable pulling from RH registries f.e.")
        stringParam('AZ_VNET', "", "Name of VNET for ARO cluster")
        stringParam('RP_OBJECT_ID', "", "Object ID of 'Azure Red Hat OpenShift RP' service principal - az ad sp list --display-name \"Azure Red Hat OpenShift RP\" --query \"[0].id\" -o tsv")
        stringParam('AZ_OBJECT_ID', "", "The Object ID of an Azure Active Directory client application")
    }

    logRotator {
        numToKeep 10
    }

    definition {
        cps {
            script(readFileFromWorkspace('pipelines/aro/aro_provision.groovy'))
            sandbox()
        }
    }
}

pipelineJob("${folderName}/aro_deletion") {
    parameters {
        stringParam('SR_USERNAME', "", "Service principal's username")
        stringParam('SR_PASSWORD', "", "Service principal's password")
        stringParam('AZ_TENANT_ID', "", "Azure tenant ID")
        stringParam('CLUSTER_NAME', "my-aro-cluster", "Name of the ARO cluster")
        stringParam('AZ_VNET', "", "Name of created VNET for ARO cluster")
        stringParam('RESOURCE_GROUP', "", "Resource group name")
        stringParam('REGION', "eastus", "Region, where the watcher will be disabled (and where the cluster is installed)")
    }

    logRotator {
        numToKeep 10
    }

    definition {
        cps {
            script(readFileFromWorkspace('pipelines/aro/aro_deletion.groovy'))
            sandbox()
        }
    }
}