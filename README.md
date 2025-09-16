## 🚀 Spring Boot CI/CD Pipeline with Jenkins & Docker
Jenkins를 활용하여 CI/CD 파이프라인을 직접 구성하고 검증한 실습입니다.
- GitHub 저장소 변경 사항을 자동으로 감지  
- Gradle 빌드 → JAR 생성 → 아카이빙 자동화  
- Jenkins의 핵심 기능(CI/CD) 실습 및 검증

## 🧰 기술 스택

| Ubuntu | Jenkins | Java 17 | Gradle | GitHub | Ngrok(선택) |
| --- | --- | --- | --- | --- | --- |
| <img src="https://cdn.simpleicons.org/ubuntu" width="36"> | <img src="https://cdn.simpleicons.org/jenkins" width="36"> | <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/java/java-original.svg" width="36"> | <img src="https://cdn.simpleicons.org/gradle/02303A" width="36"> | <img src="https://avatars.githubusercontent.com/u/22289824?s=200&v=4" width="36"> | <img src="https://raw.githubusercontent.com/gilbarbara/logos/main/logos/ngrok.svg" width="60"> |


## 📚 CI/CD 파이프라인 구축 가이드
### 1. Jenkins 컨테이너 설치 (바인드 마운트)
호스트 머신에 Jenkins 데이터를 영속적으로 저장하기 위해 호스트 디렉터리를 생성하고, 이를 Jenkins 컨테이너의 `/var/jenkins_home`에 바인드 마운트합니다.


- 호스트에 Jenkins 홈 디렉터리 생성 및 권한 설정
- Jenkins 컨테이너의 기본 사용자 UID/GID는 1000입니다
```
sudo mkdir -p /srv/jenkins
sudo chown -R 1000:1000 /srv/jenkins
```

- Jenkins 컨테이너 실행 (호스트 포트 8080 연결)
```
docker run -d \
  --name myjenkins \
  -p 8080:8080 \
  -v /srv/jenkins:/var/jenkins_home \
  jenkins/jenkins:lts-jdk17
```

### 2. Jenkins 초기 설정
- 브라우저에서 http://<호스트 IP>:8080에 접속하여 초기 설정을 진행합니다. 아래 명령어로 초기 비밀번호를 확인하세요.

- 호스트에서 초기 비밀번호 확인
```
cat /srv/jenkins/secrets/initialAdminPassword
```
- 또는 컨테이너 내부에서 확인
```
docker exec myjenkins cat /var/jenkins_home/secrets/initialAdminPassword
비밀번호 입력 후, Install suggested plugins를 선택하고 관리자 계정을 생성합니다.
```

### 3. Jenkins 파이프라인 잡(Job) 생성
> Jenkins 대시보드에서 New Item을 클릭합니다.

> 아이템 이름(예: step03_teamArt)을 입력하고 Pipeline을 선택한 후 OK를 누릅니다.

> Pipeline 탭으로 이동하여 Definition 항목을 Pipeline script로 변경하고, 아래의 Jenkinsfile 내용을 붙여넣습니다.

> Build Triggers 섹션에서 GitHub hook trigger for GITScm polling 옵션을 체크하고 저장합니다.

## 🦴Jenkinsfile (Pipeline Script)
- 이 파이프라인은 Checkout, Build, Artifact 저장 단계를 수행하며 Gradle과 Maven 프로젝트를 자동으로 감지합니다.

```
pipeline {
    agent any

    // GitHub Webhook이 푸시 이벤트를 감지하면 파이프라인을 트리거합니다.
    triggers { githubPush() }

    options {
        timestamps()
        ansiColor('xterm')
    }

    environment {
        GITHUB_REPO   = 'https://github.com/kohtaewoo/fisatest.git' // GitHub 저장소 주소
        BRANCH_NAME   = 'main'                                     // 타겟 브랜치
        PROJECT_PATH  = 'step03_JPAGradle'                         // 빌드할 서브프로젝트 경로
        WORKSPACE_DIR = "${env.WORKSPACE}"                         // Jenkins 워크스페이스 경로
    }

    stages {
        stage('Checkout') {
            steps {
                echo "Checking out ${BRANCH_NAME} branch from ${GITHUB_REPO}"
                git branch: "${BRANCH_NAME}", url: "${GITHUB_REPO}"
                sh "echo '--- Contents of ${PROJECT_PATH} ---'; ls -al ${PROJECT_PATH}"
            }
        }

        stage('Build') {
            steps {
                script {
                    // gradlew 파일 존재 여부로 Gradle 프로젝트인지 확인
                    if (fileExists("${PROJECT_PATH}/gradlew")) {
                        echo "Gradle project detected. Starting build..."
                        dir("${PROJECT_PATH}") {
                            sh 'chmod +x gradlew'
                            sh './gradlew clean build -x test' // 테스트는 제외하고 빌드
                        }
                    // pom.xml 파일로 Maven 프로젝트인지 확인
                    } else if (fileExists("${PROJECT_PATH}/pom.xml")) {
                        echo "Maven project detected. Starting build..."
                        dir("${PROJECT_PATH}") {
                            sh 'mvn -B -DskipTests clean package'
                        }
                    } else {
                        error "Build failed: No gradlew or pom.xml found in ${PROJECT_PATH}"
                    }
                }
            }
            post {
                success {
                    // 빌드 성공 시 산출물을 아카이브하여 Jenkins 빌드 페이지에서 다운로드할 수 있도록 함
                    archiveArtifacts artifacts: "${PROJECT_PATH}/build/libs/*.jar, ${PROJECT_PATH}/target/*.jar",
                                   allowEmptyArchive: true, fingerprint: true
                }
            }
        }

        stage('Save Artifact') {
            steps {
                script {
                    echo "Copying artifact to workspace root for easy access from host."
                    // Gradle 산출물을 워크스페이스 루트로 복사
                    sh "cp ${PROJECT_PATH}/build/libs/*.jar ${WORKSPACE_DIR}/ || true"
                    // Maven 산출물을 워크스페이스 루트로 복사
                    sh "cp ${PROJECT_PATH}/target/*.jar ${WORKSPACE_DIR}/ || true"
                }
            }
        }
    }

    post {
        success {
            echo "✅ Build successful! Artifact is available at host path: /srv/jenkins/workspace/${env.JOB_NAME}/"
        }
        failure {
            echo '❌ Build failed. Please check the console log for details.'
        }
    }
}
```

### 4. GitHub Webhook 연동
- 빌드할 GitHub 저장소의 Settings > Webhooks > Add webhook으로 이동합니다.

- Payload URL에 http://<Jenkins 서버 IP>:8080/github-webhook/을 입력합니다.

- Content type을 application/json으로 설정합니다.

- Which events would you like to trigger this webhook? 에서 Just the push event를 선택하고 저장합니다.
- 

### 5. Git Push로 파이프라인 트리거하기
- 로컬에서 코드를 수정한 후 git push를 실행하면 Webhook이 Jenkins를 호출하여 파이프라인이 자동으로 시작됩니다.


- Git 사용자 정보 설정 (최초 1회)
```
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

- 변경사항 추가, 커밋, 푸시
```
git add .
git commit -m "feat: Update application logic and trigger CI pipeline"
git push origin main
```

### 6. 호스트에서 빌드 산출물 확인
파이프라인이 성공적으로 완료되면, 바인드 마운트된 호스트 경로에서 .jar 파일을 확인할 수 있습니다.


- 워크스페이스 루트에 복사된 JAR 파일 확인
```
ls -al /srv/jenkins/workspace/step03_teamArt/*.jar
```
<img width="1326" height="66" alt="image" src="https://github.com/user-attachments/assets/aea895cc-3eae-4933-8d3c-202e4434c3a7" />


- 또는 원본 빌드 경로 확인 (Gradle 기준)
```
ls -al /srv/jenkins/workspace/step03_teamArt/step03_JPAGradle/build/libs/
```
<img width="980" height="127" alt="image" src="https://github.com/user-attachments/assets/24f5ffc7-c83b-4fc0-a8d7-5123707d51c0" />

### 7. 애플리케이션 컨테이너 실행
- 호스트에 저장된 .jar 파일을 OpenJDK 컨테이너에 마운트하여 애플리케이션을 실행합니다.
- 환경 변수로 산출물 경로 지정
```
export APP_JAR_PATH=/srv/jenkins/workspace/step03_teamArt
```

- Docker 컨테이너 실행 (호스트 포트 8900 연결)
>  --mount 옵션은 호스트의 JAR 파일을 컨테이너의 /app 디렉터리에 읽기 전용으로 마운트합니다.
```
docker run -d \
  --name step03-app \
  -p 8900:8900 \
  --mount type=bind,source=${APP_JAR_PATH},target=/app,readonly \
  openjdk:17-jdk-slim \
  java -jar /app/*.jar
```

- 애플리케이션 로그 확인
'''
docker logs -f step03-app
'''
- application.properties 파일에 server.port = 8900으로 설정되어 있으므로 호스트의 8900 포트를 컨테이너의 8900 포트와 연결합니다.

## 📝 트러블슈팅

**1) Webhook**

- GitHub Webhook → **Recent Deliveries**에서 Response **200** 확인
- **Payload URL**이 `/github-webhook/` 로 끝나는지 확인
- Jenkins 플러그인/설정 확인: **Git**, **GitHub**, (필요시) Manage Jenkins → Configure System → GitHub 서버 추가
- 방화벽/보안그룹에서 **8080** 오픈
- 로컬이면 **ngrok https 8080** 으로 공개 URL 사용

**2) “cannot execute binary file: Exec format error”**
    
    ```bash
    # 잘못된 예
    ./current.jar
    # 올바른 예
    java -jar current.jar
    
    ```
    
- Docker 실행 시에도 `sh -c 'java -jar /app/*.jar'`처럼 **셸을 거쳐 실행**.

**3) 권한/퍼미션 문제**

- 호스트 바인드 마운트 디렉터리는 Jenkins UID/GID(1000)에 소유권 부여
    
    ```bash
    sudo chown -R 1000:1000 /srv/jenkins
    
    ```
    
- 호스트에서 Docker 명령 권한이 없으면:
    
    ```bash
    sudo usermod -aG docker $USER
    newgrp docker
    
    ```

## 📂 디렉터리 예시

```
fisatest/
└─ step03_JPAGradle/
   ├─ build/
   │  └─ libs/
   │     └─ app-0.0.1-SNAPSHOT.jar
   ├─ src/...
   ├─ gradlew
   └─ settings.gradle

```
