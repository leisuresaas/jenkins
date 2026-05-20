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
                        pnpm approve-builds --all
                        pnpm install
                        pnpm build
                    '''
                }
            }

            stage("Archive"){
                steps{
                    sh '''
                        rm -rf ./build
                        mkdir ./build
                        cp -r .next/standalone/* ./build
                        cp -r .next/standalone/.next ./build
                        cp -r .next/static ./build/.next
                        cp -r ./public ./build
                        cd ./build
                        rm -f /home/archive/test.tar
                        tar -cf /home/archive/test.tar .
                        cd ..
                    '''
                }
            }

            stage("DepolyToTestServer"){

                when{
                    branch 'main'
                }

                steps{
                    sh '''
                        sudo rm -rf /home/app/test/*
                        tar -xvf /home/archive/test.tar -C /home/app/test
                        docker restart test-node
                    '''
                }
            }

        }
    }
}