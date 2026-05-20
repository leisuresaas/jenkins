groovy

#!/usr/bin/env groovy

def call(Map config = [:]){
    pipeline{
        
        agent any
        
        environment {
            NODE_VERSION = config.version ?: '24'
        }

        stages{

            stage('Checkout'){
                steps{
                    echo "checkout source from github..."
                    checkout scm
                }
            }

        }
    }
}