
pipeline {
    agent any
    tools {
        jdk 'jdk17'
        nodejs 'node16'
    }
    environment {
        SCANNER_HOME = tool 'sonar-scanner'
    }
    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
            }
        }
        stage('Checkout from Git') {
            steps {
                git branch: 'main', url: 'https://github.com/imsalmanmalik/DevSecOps-Project-Netflix-Deployment.git'
            }
        }
        stage('Sonarqube Analysis') {
            steps {
                withSonarQubeEnv('sonar-server') {
                    sh '''
                        $SCANNER_HOME/bin/sonar-scanner \
                          -Dsonar.projectName=Netflix \
                          -Dsonar.projectKey=Netflix
                    '''
                }
            }
        }
        stage('Quality Gate') {
            steps {
                script {
                    waitForQualityGate abortPipeline: false, credentialsId: 'Sonar-token'
                }
            }
        }
        stage('Install Dependencies') {
            steps {
                sh "npm install"
            }
        }
        stage('OWASP FS SCAN') {
            steps {
                dependencyCheck additionalArguments: '--scan ./ --disableYarnAudit --disableNodeAudit', odcInstallation: 'DP-Check'
                dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
            }
        }
        stage('TRIVY FS SCAN') {
            steps {
                sh "trivy fs . > trivyfs.txt"
            }
        }
        stage('Docker Build & Push') {
            steps {
                script {
                    withDockerRegistry(credentialsId: 'docker', toolName: 'docker') {
                        sh "docker build --build-arg TMDB_V3_API_KEY=e5136e6f0c207c6117e3592fe9b21a87 -t netflix ." //replace with your TMDB API key
                        sh "docker tag netflix mariamshahzad/netflix:latest" //replace with your dockerhub registry
                        sh "docker push mariamshahzad/netflix:latest" //replace with your dockerhub registry
                    }
                }
            }
        }
        stage('TRIVY Image Scan') {
            steps {
                sh "trivy image mariamshahzad/netflix:latest > trivyimage.txt"
            }
        }
        stage('Deploy to container') {
            steps {
                script {
                    // Stop and remove any existing container named "netflix"
                    sh '''
                        docker ps -a -q --filter name=netflix | xargs -r docker stop
                        docker ps -a -q --filter name=netflix | xargs -r docker rm
                    '''
                    // Start a fresh container
                    sh 'docker run -d --name netflix -p 8081:80 mariamshahzad/netflix:latest'
                }
            }
        }
    }
}
