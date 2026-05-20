groovy

def call(Map config = [:]){
    pipeline{
        
        agent any
        
        environment {
            NODE_VERSION = '25'
        }

        stages{

            stage('Prepare'){
                steps{
                    script{
                        def version = config.version ?; '25'
                        env.NODE_VERSION = version
                    }
                }
            }

            stage('Checkout'){
                steps{
                    echo "checkout source from github..."
                    checkout scm
                }
            }

            stage('Build'){
                steps{
                    echo "Using Node.js version: ${env.NODE_VERSION}"
                }
            }

        }
    }
}