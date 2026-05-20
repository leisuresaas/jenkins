groovy

#!/usr/bin/env groovy

def call(Map config = [:]){
    pipeline{
        
        agent any
        
        environment {

        }

        stages{

            stage('Checkout'){
                echo "checkout source from github..."
                steps{
                    checkout scm
                }
            }

        }
    }
}