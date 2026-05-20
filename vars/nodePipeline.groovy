groovy

def call(Map config = [:]){

    if(!config.name){
        error("Parameter 'name' is required")
    }

    pipeline{
        
        agent any

        environment{
            APP_NAME = "${config.name}"
        }

        tools{
            nodejs 'node-25'
        }
        
        stages{

            stage('Prepare'){
                steps{
                    sh '''
                        # install pnpm
                        npm install -g pnpm

                        echo "====================================="
                        echo "Build ${APP_NAME}"
                        echo "Node version: $(node -v)"
                        echo "npm version: $(npm -v)"
                        echo "pnpm version: $(pnpm -v)"
                        echo "====================================="
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
                        echo "Installing dependencies..."
                        pnpm install --ignore-scripts || true

                        echo "Approving build scripts"
                        pnpm approve-builds --all

                        echo "Reinstall with approved scripts"
                        pnpm install

                        echo "Building..."
                        pnpm build

                        echo "Build completed successfully!"
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
                        rm -f /home/archive/${APP_NAME}.tar
                        tar -cf /home/archive/${APP_NAME}.tar .
                        cd ..
                    '''
                }
            }

            stage("DeployToTestServer"){

                when{
                    branch 'develop'
                }

                steps{
                    sh '''
                        sudo rm -rf /home/app/${APP_NAME}
                        mkdir /home/app/${APP_NAME}
                        tar -xf /home/archive/${APP_NAME}.tar -C /home/app/${APP_NAME}
                        docker restart ${APP_NAME}-node
                    '''
                }
            }

            stage("DeployToProductionServer"){

                when{
                    branch 'main'
                }

                steps{

                    input message: "Confirm deploy to production server?", ok: "confirm"

                    script{

                        sshPublisher(
                            publishers:[
                                sshPublisherDesc(
                                    configName: "Production-2",
                                    verbose: true,
                                    transfers: [
                                        sshTransfer(
                                            sourceFiles: "/home/archive/${APP_NAME}.tar",
                                            remoteDirectory: "/tmp",
                                            execCommand: """

                                                set -e

                                                # 
                                                TEMP_DIR = "/tmp/deploy_${APP_NAME}"
                                                mkdir -p ${TEMP_DIR}

                                                #
                                                tar -xf /tmp/${APP_NAME}.tar -C ${TEMP_DIR}

                                                #
                                                if [-f "/home/app/${APP_NAME}/.env"]; then
                                                    cp /home/app/${APP_NAME}/.env ${TEMP_DIR}/
                                                fi

                                                #
                                                rm -rf /home/backup/${APPNAME}/
                                                mv /home/app/${APP_NAME} /home/backup/${APP_NAME}
                                                mv ${TEMP_DIR} /home/app/${APP_NAME}
                                                echo "hello production"

                                            """
                                        )
                                    ],
                                    execTimeout: 120000,
                                    usePty: true
                                )
                            ]
                        )

                    }

                }

            }

        }
    }
}