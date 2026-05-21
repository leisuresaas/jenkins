groovy

def call(Map config = [:]){

    if(!config.name){
        error("Parameter 'name' is required")
    }

    pipeline{
        
        agent any

        environment{
            APP_NAME = "${config.name}"
            APP_DIR = "/home/app/${config.name}"
            BACKUP_DIR = "/home/backup/${config.name}"
            TEMP_DIR = "/tmp/deploy_${config.name}"
            ARCHIVE_FILE = "/home/archive/${config.name}.tar"
        }

        tools{
            nodejs 'node-25'
        }
        
        stages{

            stage('Load Environment'){
                steps{
                    script{

                        def id = "${APP_NAME}-env"

                        if(id && availableConfigs.contains(id)){
                            configFileProvider([
                                configFile(fileId: id, targetLocation: 'env.properties')
                            ])

                            def props = readProperties file: 'env.properties'

                            props.each { key, value ->
                                env."${key}" = value
                            }
                        }

                    }
                }
            }

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
                        rm -f ${ARCHIVE_FILE}
                        tar -cf ${ARCHIVE_FILE} .
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
                        #
                        if [ -f "${APP_DIR}/.env" ]; then
                            sudo mv ${APP_DIR}/.env /tmp/${APP_NAME}.env
                        fi

                        #
                        sudo rm -rf /home/app/${APP_NAME}
                        mkdir /home/app/${APP_NAME}

                        #
                        tar -xf /home/archive/${APP_NAME}.tar -C /home/app/${APP_NAME}
                        
                        #
                        if [ -f "/tmp/${APP_NAME}.env" ]; then
                            sudo mv /tmp/${APP_NAME}.env ${APP_DIR}/.env
                        fi

                        docker restart ${APP_NAME}
                    '''
                }
            }

            stage("DeployToProductionServer"){

                when{
                    branch 'main'
                }

                steps{

                    // input message: "Confirm deploy to production server?", ok: "confirm"
                    sh '''
                        cp ${ARCHIVE_FILE} .
                    '''

                    script{

                        sshPublisher(
                            publishers:[
                                sshPublisherDesc(
                                    configName: "Production-2",
                                    verbose: true,
                                    transfers: [
                                        sshTransfer(
                                            sourceFiles: "${APP_NAME}.tar",
                                            remoteDirectory: "/tmp",
                                            execCommand: """

                                                set -e

                                                # 
                                                mkdir -p ${TEMP_DIR}

                                                #
                                                tar -xf /tmp/${APP_NAME}.tar -C ${TEMP_DIR}

                                                #
                                                if [ -f "${APP_DIR}/.env" ]; then
                                                    cp ${APP_DIR}/.env ${TEMP_DIR}/
                                                fi

                                                #
                                                sudo rm -rf ${BACKUP_DIR}
                                                mv ${APP_DIR} ${BACKUP_DIR}
                                                mv ${TEMP_DIR} ${APP_DIR}

                                                # clean
                                                rm -f /tmp/${APP_NAME}.tar

                                                #
                                                docker restart ${APP_NAME}

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