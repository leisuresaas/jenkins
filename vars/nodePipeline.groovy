groovy

def call(Map config = [:]){

    def name = config.name ?: error("Parameter 'name' is required")

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
                        rm -f /home/archive/${name}.tar
                        tar -cf /home/archive/${name}.tar .
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
                        sudo rm -rf /home/app/${name}/*
                        tar -xf /home/archive/${name}.tar -C /home/app/${name}
                        docker restart ${name}-node
                    '''
                }
            }

        }
    }
}