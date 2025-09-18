# 🚀 Spring Boot CI/CD Pipeline with Jenkins & Docker

Jenkins를 활용하여 CI/CD 파이프라인을 직접 구성하고 검증한 실습입니다.

- GitHub 저장소 변경 사항 자동 감지 (Webhook)
- Gradle 빌드 → JAR 생성 → 아카이빙
- **Day 1:** 단일 VM에서 Jenkins(컨테이너) + 바인드 마운트로 즉시 실행
- **Day 2:** **VM1(빌드/배포)** → **VM2(WAS 실행)** 원격 배포
---

## 🧰 기술 스택

| Ubuntu | Jenkins | Java 17 | Gradle | GitHub | Ngrok |
| --- | --- | --- | --- | --- | --- |
| <img src="https://cdn.simpleicons.org/ubuntu" width="36"> | <img src="https://cdn.simpleicons.org/jenkins" width="36"> | <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/java/java-original.svg" width="36"> | <img src="https://cdn.simpleicons.org/gradle/02303A" width="36"> | <img src="https://avatars.githubusercontent.com/u/22289824?s=200&v=4" width="36"> | <img src="https://raw.githubusercontent.com/gilbarbara/logos/main/logos/ngrok.svg" width="60"> |

---

# Day 1 — 단일 VM (Jenkins 컨테이너 + 바인드 마운트 실행)

### 아키텍처 개요
<img width="1289" height="508" alt="image" src="https://github.com/user-attachments/assets/e71ebe73-e69c-4fea-9b2f-f3650a5fb7bf" />


### 1) Jenkins 컨테이너 설치 (바인드 마운트)

```bash
sudo mkdir -p /srv/jenkins
sudo chown -R 1000:1000 /srv/jenkins   # Jenkins UID/GID = 1000

docker run -d \
  --name myjenkins \
  -p 8080:8080 \
  -v /srv/jenkins:/var/jenkins_home \
  jenkins/jenkins:lts-jdk17
```

초기 비밀번호 확인:

```bash
cat /srv/jenkins/secrets/initialAdminPassword
# 또는
docker exec myjenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

### 2) Webhook 설정

- GitHub → **Settings → Webhooks → Add webhook**
    - Payload URL: `http://<VM1_IP>:8080/github-webhook/`
    - Content type: `application/json`
    - Events: `Just the push event`
- 로컬/사설망이면: `ngrok http 8080` 으로 공개 URL 사용

### 3) Jenkins 파이프라인 Job

- New Item → Pipeline → `step03_teamArt`
- Build Triggers: **GitHub hook trigger for GITScm polling** 체크
- Pipeline Script에 아래 삽입

### Jenkinsfile (Day 1: 단일 VM 빌드 & 컨테이너로 실행)

```groovy
pipeline {
  agent any
  triggers { githubPush() }
  options { timestamps(); ansiColor('xterm') }

  environment {
    GITHUB_REPO   = 'https://github.com/kohtaewoo/fisatest.git'
    BRANCH_NAME   = 'main'
    PROJECT_PATH  = 'step03_JPAGradle'
    WORKSPACE_DIR = "${env.WORKSPACE}"
    APP_PORT      = '8900'
    APP_NAME      = 'step03-app'
  }

  stages {
    stage('Checkout') {
      steps {
        git branch: "${BRANCH_NAME}", url: "${GITHUB_REPO}"
        sh "ls -al ${PROJECT_PATH}"
      }
    }

    stage('Build') {
      steps {
        dir("${PROJECT_PATH}") {
          script {
            if (fileExists('gradlew')) {
              sh 'chmod +x gradlew'
              sh './gradlew clean build -x test'
            } else if (fileExists('pom.xml')) {
              sh 'mvn -B -DskipTests clean package'
            } else {
              error "No gradlew or pom.xml found."
            }
          }
        }
      }
      post {
        success {
          archiveArtifacts artifacts: "${PROJECT_PATH}/build/libs/*.jar, ${PROJECT_PATH}/target/*.jar",
                           allowEmptyArchive: true, fingerprint: true
        }
      }
    }

    stage('Expose Artifact to Host (bind)') {
      steps {
        sh """
          cp ${PROJECT_PATH}/build/libs/*.jar ${WORKSPACE_DIR}/ || true
          cp ${PROJECT_PATH}/target/*.jar ${WORKSPACE_DIR}/ || true
          ls -al ${WORKSPACE_DIR} | grep .jar || true
        """
      }
    }

    stage('Run App in Docker') {
      steps {
        script {
          sh "docker rm -f ${APP_NAME} || true"
          sh """
            docker run -d --name ${APP_NAME} \
              -p ${APP_PORT}:${APP_PORT} \
              --mount type=bind,source=${WORKSPACE_DIR},target=/app,readonly \
              openjdk:17-jdk-slim \
              java -jar /app/*.jar --server.port=${APP_PORT}
          """
        }
      }
    }
  }

  post {
    success { echo "✅ Running on http://<VM1_IP>:${APP_PORT}" }
    failure { echo "❌ Build/Run failed. Check logs." }
  }
}

```

앱 로그:

```bash
docker logs -f step03-app
```

---

# Day 2 — 두 VM (VM1: Jenkins → VM2: WAS 원격 배포)

### 아키텍처 개요

<img width="1566" height="534" alt="image" src="https://github.com/user-attachments/assets/472d0404-750d-4aa2-a7ed-8117c9c574c2" />


> 상황 정리
> 
> - 초기에는 VM1에서 **Jenkins 컨테이너**로 운영 → VM2 WAS에 원격 배포
> - 그 이후에는 VM1에 **Jenkins 로컬 설치**로 전환(동일 파이프라인) → 여전히 VM2 WAS로 배포

### 0) 선행 준비

- **VM2**: OpenJDK 설치, 서비스 포트(예: 8900) 오픈
- **VM1 ↔ VM2**: SSH 키 기반 접속 설정(비밀번호 없이)
    
    ```bash
    # VM1에서
    ssh-keygen -t rsa -b 4096
    ssh-copy-id ubuntu@<VM2_IP>
    ssh ubuntu@<VM2_IP> "java -version"
    ```
    

### 1) VM2에 systemd 서비스 셋업(지속 실행 권장)

`/etc/systemd/system/myapp.service`

```
[Unit]
Description=Spring Boot App
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/opt/myapp
ExecStart=/usr/bin/java -jar /opt/myapp/app.jar --server.port=8900
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

VM2 초기화:

```bash
sudo mkdir -p /opt/myapp
sudo chown -R ubuntu:ubuntu /opt/myapp
sudo systemctl daemon-reload
sudo systemctl enable myapp
```

### 2) Jenkinsfile (Day 2: SSH로 VM2 배포)

> VM1의 Jenkins(컨테이너든 로컬이든 상관없이 동일)에서 scp로 JAR 전송 → VM2에서 서비스 재시작
> 
- Jenkins에 Credentials 추가: `SSH Username with private key` (ID: `vm2-ssh`)
- 필요 플러그인: **Credentials Binding**(기본), **SSH Agent**(혹은 직접 scp/ssh 사용)

```groovy
pipeline {
  agent any
  triggers { githubPush() }
  options { timestamps(); ansiColor('xterm') }

  environment {
    GITHUB_REPO   = 'https://github.com/kohtaewoo/fisatest.git'
    BRANCH_NAME   = 'main'
    PROJECT_PATH  = 'step03_JPAGradle'
    ARTIFACT_NAME = 'app-0.0.1-SNAPSHOT.jar'   // 실제 산출물명에 맞춰 조정
    VM2_USER      = 'ubuntu'
    VM2_HOST      = '<VM2_IP>'
    VM2_APP_DIR   = '/opt/myapp'
  }

  stages {
    stage('Checkout') {
      steps {
        git branch: "${BRANCH_NAME}", url: "${GITHUB_REPO}"
      }
    }

    stage('Build') {
      steps {
        dir("${PROJECT_PATH}") {
          sh 'chmod +x gradlew || true'
          sh './gradlew clean build -x test'
        }
      }
      post {
        success {
          archiveArtifacts artifacts: "${PROJECT_PATH}/build/libs/*.jar",
                           allowEmptyArchive: false, fingerprint: true
        }
      }
    }

    stage('Deploy to VM2 (scp + systemd restart)') {
      steps {
        script {
          // 최신 JAR 파일명 추출(와일드카드)
          def jar = sh(script: "ls -t ${PROJECT_PATH}/build/libs/*.jar | head -1", returnStdout: true).trim()
          echo "Deploying ${jar} to ${VM2_HOST}:${VM2_APP_DIR}/app.jar"

          // SSH 키가 Jenkins에 로드되어 있지 않다면, 직접 -i 경로 지정도 가능
          // 여기서는 known_hosts 무시 옵션 사용
          sh """
            scp -o StrictHostKeyChecking=no ${jar} ${VM2_USER}@${VM2_HOST}:${VM2_APP_DIR}/app.jar
            ssh -o StrictHostKeyChecking=no ${VM2_USER}@${VM2_HOST} 'sudo systemctl restart myapp && sudo systemctl status --no-pager myapp'
          """
        }
      }
    }
  }

  post {
    success { echo "✅ Deployed to http://${VM2_HOST}:8900" }
    failure { echo "❌ Deploy failed. Check console." }
  }
}
```

> Jenkins 로컬 설치로 전환 시: 위 파이프라인 동일하게 동작.
> 
> 
> 차이는 **Jenkins 실행 위치(컨테이너 → 로컬)** 뿐이며, **VM2로의 scp/ssh** 동작에는 영향 없음.
> 

---

## 🔍 확인/운영

### Webhook 동작 확인

- GitHub → Webhooks → **Recent Deliveries** → Response `200` 확인
- Jenkins 콘솔 로그에서 `GitHub Webhook` 트리거 메시지 확인

### 애플리케이션 확인

- Day 1: `http://<VM1_IP>:8900`
- Day 2: `http://<VM2_IP>:8900`

### 로그

- Day 1: `docker logs -f step03-app`
- Day 2: `journalctl -u myapp -f` (VM2)

---

## 🧯 트러블슈팅

**Webhook**

- Payload URL이 `/github-webhook/` 로 끝나는지
- 방화벽/보안그룹 8080 오픈
- 로컬 환경은 `ngrok http 8080` 사용(ngrok URL을 Payload URL로)

**권한/퍼미션**

- 바인드 마운트 디렉터리: Jenkins UID/GID(1000) 소유
    
    ```bash
    sudo chown -R 1000:1000 /srv/jenkins
    ```
    
- Docker 권한:
    
    ```bash
    sudo usermod -aG docker $USER
    newgrp docker
    ```
    

**SSH**

- VM1에서:
    
    ```bash
    ssh -i <개인키> ubuntu@<VM2_IP>
    scp -i <개인키> <jar> ubuntu@<VM2_IP>:/opt/myapp/app.jar
    ```
    
- 권한/소유자:
    
    ```bash
    ssh ubuntu@<VM2_IP> "sudo chown ubuntu:ubuntu /opt/myapp/app.jar"
    ```
    

**systemd 서비스가 바로 꺼질 때**

- 포트 중복/환경 변수/자바 버전 확인
- 로그 확인: `journalctl -u myapp -f`

---

## 🧪 커밋 & 트리거

```bash
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"

git add .
git commit -m "feat: CI/CD Day1+Day2 pipeline and remote deploy"
git push origin main
```
