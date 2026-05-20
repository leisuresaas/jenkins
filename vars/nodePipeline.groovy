groovy

def call(Map config = [:]){
    pipeline{
        
        agent any

        tools{
            nodejs 'node-25'
        }
        
        stages{

            stage('Prepare'){
                steps{
                    script{
                        def version = config.version ?: '25'
                    }
                    sh '''
                        # print environment
                        echo "Node version: $(node -v)"
                        echo "npm version: $(npm -v)"

                        # install pnpm
                        npm install -g pnpm

                        echo "pnpm version: $(pnpm -v)"
                    '''
                }
            }

            // stage('Checkout'){
            //     steps{
            //         echo "checkout source from github..."
            //         checkout scm
            //     }
            // }

            stage('Build'){
                steps{
                    sh '''
                        pnpm approve-builds --force || true
                        pnpm install
                        pnpm build
                    '''
                }
            }

        }
    }
}