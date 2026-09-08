groovy

def call(Map config = [:]){

    if(!config.name){
        error("Parameter 'name' is required")
    }

    def mainPath = config.main ?: '.'
    def binaryName = config.binaryName ?: config.name
    def rootDir = config.rootDir ?: "/home/app"
    def container = config.container ?: config.name
    def goPrivate = config.goPrivate ?: 'github.com/leisuresaas/*,github.com/leisurecoder/*'
    def gitCredentialsId = config.gitCredentialsId ?: 'Github-PAT-Leisurecoder'

    pipeline{

        agent any

        environment{
            APP_NAME = "${config.name}"
            BINARY_NAME = "${binaryName}"
            MAIN_PATH = "${mainPath}"
            APP_DIR = "${rootDir}/${config.name}"
            CONTAINER_NAME = "${container}"
            BACKUP_DIR = "/home/backup/${config.name}"
            TEMP_DIR = "/tmp/deploy_${config.name}"
            ARCHIVE_FILE = "/home/archive/${config.name}/${config.binaryName}"
            GOPRIVATE = "${goPrivate}"
            GONOSUMDB = "${goPrivate}"
            GONOPROXY = "${goPrivate}"
        }

        tools{
            go 'go-1.25'
        }

        stages{

            stage('Load Environment'){
                steps{
                    script{

                        def id = "${env.BRANCH_NAME}-env"

                        try{

                            configFileProvider([
                                configFile(fileId: id, targetLocation: 'env.properties')
                            ]){
                                def props = readProperties file: 'env.properties'

                                props.each { key, value ->
                                    env."${key}" = value
                                    echo "Set Environment ${key} = ${value}"
                                }
                            }


                        }catch(Exception e){
                            echo "No environment configuration found for ${APP_NAME} (fileId: ${id})"
                        }

                    }
                }
            }

            stage('Prepare'){
                steps{
                    sh '''
                        echo "====================================="
                        echo "Build ${APP_NAME}"
                        echo "Go version: $(go version)"
                        echo "Main path: ${MAIN_PATH}"
                        echo "Binary name: ${BINARY_NAME}"
                        echo "App dir: ${APP_DIR}"
                        echo "Container: ${CONTAINER_NAME}"
                        echo "GOPRIVATE: ${GOPRIVATE}"
                        echo "GONOSUMDB: ${GONOSUMDB}"
                        echo "GONOPROXY: ${GONOPROXY}"
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
                    script {
                        def buildSteps = { ->
                            sh '''

                                git config --global url."https://${GIT_USER}:${GIT_TOKEN}@github.com/".insteadOf "https://github.com/"

                                echo "Downloading dependencies..."
                                go env GOPRIVATE GONOSUMDB GONOPROXY

                                # Fresh module cache per build (no reuse of agent/global modcache).
                                # GOPRIVATE/GONOPROXY/GONOSUMDB already fetch private modules from VCS.
                                export GOMODCACHE="${WORKSPACE}/.gomodcache"
                                sudo rm -rf "${GOMODCACHE}"

                                go mod tidy
                                go mod download

                                echo "Building..."
                                CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-s -w" -o ${BINARY_NAME} ${MAIN_PATH}

                                echo "Build completed successfully!"
                            '''
                        }

                        withCredentials([usernamePassword(
                            credentialsId: gitCredentialsId,
                            usernameVariable: 'GIT_USER',
                            passwordVariable: 'GIT_TOKEN'
                        )]) {
                            buildSteps()
                        }
                    }
                }
            }

            // stage("Archive"){
            //     steps{
            //         sh '''
            //             mkdir -p $(dirname "${ARCHIVE_FILE}")
            //             rm -f "${ARCHIVE_FILE}"
            //             cp "${BINARY_NAME}" "${ARCHIVE_FILE}"
            //         '''
            //     }
            // }

            stage("DeployToTestServer"){

                when{
                    branch 'develop'
                }

                steps{
                    sh '''
                        # config.yaml
                        if [ -f "${APP_DIR}/config.yaml" ]; then
                            sudo mv ${APP_DIR}/config.yaml /tmp/${APP_NAME}.config.yaml
                        fi

                        #
                        sudo rm -rf ${APP_DIR}
                        mkdir -p ${APP_DIR}

                        #
                        cp ${BINARY_NAME} ${APP_DIR}/${BINARY_NAME}

                        #
                        chmod +x ${APP_DIR}/${BINARY_NAME}

                        #
                        if [ -f "/tmp/${APP_NAME}.config.yaml" ]; then
                            sudo mv /tmp/${APP_NAME}.config.yaml ${APP_DIR}/config.yaml
                        fi

                        docker restart ${CONTAINER_NAME}
                    '''
                }
            }

            stage("DeployToProductionServer"){

                when{
                    branch 'main'
                }

                steps{

                    // sh '''
                    //     cp ${ARCHIVE_FILE} .
                    // '''

                    script{

                        sshPublisher(
                            publishers:[
                                sshPublisherDesc(
                                    configName: "Production-2",
                                    verbose: true,
                                    transfers: [
                                        sshTransfer(
                                            sourceFiles: "${BINARY_NAME}",
                                            remoteDirectory: "/tmp",
                                            execCommand: """

                                                set -e

                                                #
                                                mkdir -p ${TEMP_DIR}

                                                mv /tmp/${BINARY_NAME} ${TEMP_DIR}/${BINARY_NAME}
                                                chmod +x ${TEMP_DIR}/${BINARY_NAME}

                                                if [ -f "${APP_DIR}/config.yaml" ]; then
                                                    cp ${APP_DIR}/config.yaml ${TEMP_DIR}/${APP_NAME}.config.yaml
                                                fi

                                                #
                                                sudo rm -rf ${BACKUP_DIR}
                                                mv ${APP_DIR} ${BACKUP_DIR}
                                                mv ${TEMP_DIR} ${APP_DIR}

                                                #
                                                docker restart ${CONTAINER_NAME}

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
