## 🚀 Spring Boot CI/CD Pipeline with Jenkins & Docker
'fisatest' 프로젝트는 GitHub 푸시 이벤트를 트리거로 Jenkins와 Docker를 활용하여 Spring Boot 애플리케이션의 빌드, 테스트, 배포를 자동화하는 CI/CD 파이프라인 구축을 목표로 합니다.

## 🌟 프로젝트 목표
- 자동화된 빌드 및 배포: GitHub에 소스를 푸시하면 Jenkins가 이를 감지하여 자동으로 Gradle 빌드를 실행합니다.

- 지속적인 통합 (CI): 코드 변경 사항이 원격 저장소에 병합될 때마다 파이프라인이 실행되어 코드의 안정성을 검증합니다.

- 컨테이너 기반 실행: 빌드가 완료된 .jar 산출물을 Docker 컨테이너 환경에서 즉시 실행하여 일관성 있는 환경을 보장합니다.

- 결과물 영속화: Jenkins 컨테이너의 작업 공간을 호스트 볼륨과 바인드 마운트하여 빌드 산출물과 로그를 영구적으로 관리하고 호스트에서 직접 활용합니다.

## ⚙️ 아키텍처 다이어그램

```
+-----------+     push     +-----------------+     webhook     +-----------------------------------------+
|           | -------------> |                 | --------------->  |                                         |
|  GitHub   |                |  Jenkins Server |                   |  Jenkins Pipeline                       |
|           | <------------- |  (Docker)       | <---------------- |  (Checkout -> Build -> Archive -> Copy) |
+-----------+   git clone    +-----------------+    trigger      +-----------------------------------------+
                                                                                    |
                                                                                    | build artifact (.jar)
                                                                                    |
                                                                    +-----------------------------------------+
                                                                    |                                         |
                                                                    |  Host Machine (/srv/jenkins/workspace)  |
                                                                    |                                         |
                                                                    +-----------------------------------------+
                                                                                    |
                                                                                    | mount volume
                                                                                    |
                                                        +-----------------------------------------------------+
                                                        |                                                     |
                                                        |  Application Container (OpenJDK) runs the .jar file |
                                                        |                                                     |
                                                        +-----------------------------------------------------+
```


## 🛠️ 기술 스택 및 구성 요소
| 구분 | 기술 스택 |	설명 |
| -- | ---- | -- |
| VCS	| `GitHub` | 소스 코드 버전 관리 및 Webhook 트리거 제공 |
| CI/CD |	`Jenkins` |	`jenkins/jenkins:lts-jdk17` 이미지를 사용한 CI 서버 |
| 컨테이너 |	`Docker` |	Jenkins 및 애플리케이션 실행 환경 컨테이너화 |
| 애플리케이션 | 	`Spring Boot` |	Gradle 기반의 Java 웹 애플리케이션 |
| 런타임 | 	`OpenJDK 17` |	`openjdk:17-jdk-slim` 이미지를 사용해 빌드된 JAR 실행 |
| 파이프라인| 	`Jenkinsfile` |	선언형 파이프라인 스크립트 (Groovy) |


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


# 워크스페이스 루트에 복사된 JAR 파일 확인
ls -al /srv/jenkins/workspace/step03_teamArt/*.jar

# 또는 원본 빌드 경로 확인 (Gradle 기준)
ls -al /srv/jenkins/workspace/step03_teamArt/step03_JPAGradle/build/libs/
7. 애플리케이션 컨테이너 실행
호스트에 저장된 .jar 파일을 OpenJDK 컨테이너에 마운트하여 애플리케이션을 실행합니다.

Bash

# 환경 변수로 산출물 경로 지정
export APP_JAR_PATH=/srv/jenkins/workspace/step03_teamArt

# Docker 컨테이너 실행 (호스트 포트 8900 연결)
# --mount 옵션은 호스트의 JAR 파일을 컨테이너의 /app 디렉터리에 읽기 전용으로 마운트합니다.
docker run -d \
  --name step03-app \
  -p 8900:8900 \
  --mount type=bind,source=${APP_JAR_PATH},target=/app,readonly \
  openjdk:17-jdk-slim \
  java -jar /app/*.jar

# 애플리케이션 로그 확인
docker logs -f step03-app
application.properties 파일에 server.port = 8900으로 설정되어 있으므로 호스트의 8900 포트를 컨테이너의 8900 포트와 연결합니다.

8. 애플리케이션 동작 확인
컨테이너가 정상적으로 실행되면 curl이나 웹 브라우저를 통해 API 엔드포인트를 호출하여 동작을 검증할 수 있습니다.

Bash

# GET 요청 테스트
curl http://localhost:8900/app/get

# POST 요청 테스트
curl -X POST http://localhost:8900/app/post
📝 트러블슈팅 메모
Jenkins 초기 비밀번호 오류: 비밀번호 복사 시 앞뒤 공백이 포함되지 않았는지 확인하세요. 문제가 지속되면 docker exec 명령어로 컨테이너 내부의 최신 비밀번호를 다시 확인하세요.

Webhook 미동작:

GitHub Webhook 설정의 Recent Deliveries 탭에서 응답 코드가 200인지 확인하세요. 404 오류는 Jenkins URL이 잘못되었거나 플러그인 문제일 수 있습니다.

Jenkins 서버의 방화벽이 8080 포트를 허용하는지 확인하세요.

Jenkins 잡 설정의 GitHub hook trigger for GITScm polling이 활성화되어 있는지 재확인하세요.

빌드 산출물 미발견:

Jenkins 빌드 콘솔 로그를 확인하여 빌드가 성공적으로 완료되었는지 확인하세요.

Jenkinsfile의 PROJECT_PATH가 올바른지, 빌드 스크립트(Gradle/Maven)가 산출물을 기본 경로(build/libs/ 또는 target/)에 생성하는지 확인하세요.
