# Kubernetes 배포

## 도입 배경

부하테스트에서 WAS 1대 → 3대로 처리량 528% 증가를 확인했지만, docker-compose 기반 운영의 한계가 명확했음.

| 문제 | docker-compose | Kubernetes |
|------|---------------|------------|
| 스케일링 | YAML 수정 + nginx.conf 수정 (수동) | `replicas` 변경 또는 HPA 자동 |
| 장애 복구 | `restart: always`가 전부 | Liveness Probe로 자동 감지 + 재시작 |
| 배포 | `down` → `up` (다운타임 발생) | 롤링 업데이트 (무중단) |
| 롤백 | 이전 이미지로 다시 빌드 | `kubectl rollout undo` (즉시) |
| 환경 분리 | `.env` 파일로 관리 | ConfigMap/Secret + Namespace |

---

## 인프라 구조

```
docker-compose (이전)
  Client → Nginx(80) → app1, app2, app3 → PostgreSQL, Redis

Kubernetes (현재)
  Client → Ingress → Service(로드밸런싱) → Pod 1~6개(HPA) → PostgreSQL, Redis
```

### docker-compose와의 대응

| docker-compose | Kubernetes | 역할 |
|----------------|-----------|------|
| `services.app1~3` | Deployment (`replicas: 3`) | 앱 실행 |
| nginx | Ingress + Service | 로드밸런싱, 외부 접근 |
| `environment:` | ConfigMap / Secret | 환경변수 관리 |
| `restart: always` | Liveness / Readiness Probe | 장애 감지 및 복구 |
| 수동 컨테이너 추가 | HPA | 오토스케일링 |

---

## K8s 리소스 구성

```
k8s/
├── postgres.yml     # PostgreSQL Deployment + Service
├── redis.yml        # Redis Deployment + Service
├── configmap.yml    # 환경변수 (DB_HOST, REDIS_HOST 등)
├── secret.yml       # 민감 정보 (DB_PASSWORD)
├── app.yml          # 앱 Deployment + Service + Probe
├── ingress.yml      # 외부 HTTP 라우팅
└── hpa.yml          # 오토스케일링 설정
```

---

## 주요 설정

### Deployment (app.yml)

```yaml
replicas: 3                    # Pod 3개 유지 (docker-compose의 app1~3 대체)
image: ticketing-app:v1        # Docker 이미지 지정
imagePullPolicy: Never         # 로컬 이미지 사용 (minikube)

envFrom:
  - configMapRef: ticketing-config   # 환경변수 (ConfigMap)
  - secretRef: ticketing-secret      # 비밀번호 (Secret)

resources:
  requests/limits:
    cpu: 500m                  # CPU 0.5코어
    memory: 512Mi              # 메모리 512MB
```

### Probe

```yaml
livenessProbe:                 # 앱이 죽었는지 체크
  path: /actuator/health/liveness
  initialDelaySeconds: 90      # Spring Boot 부팅 시간 고려
  periodSeconds: 10            # 10초마다 체크
  failureThreshold: 5          # 5번 실패 시 Pod 재시작

readinessProbe:                # 트래픽 받을 준비 됐는지 체크
  path: /actuator/health/readiness
  initialDelaySeconds: 60
  periodSeconds: 5
  failureThreshold: 5          # 5번 실패 시 트래픽 제외
```

- Spring Boot Actuator가 K8s 환경 감지 시 자동 활성화
- Liveness 실패 → Pod 재시작
- Readiness 실패 → Service에서 트래픽 제외 (Pod은 유지)

### HPA (hpa.yml)

```yaml
minReplicas: 2                 # 최소 2개
maxReplicas: 6                 # 최대 6개
averageUtilization: 50         # CPU 50% 초과 시 스케일아웃
```

---

## 배포 방법

### 초기 배포

```bash
# minikube 환경에서 이미지 빌드
eval $(minikube docker-env)
docker build -f docker/Dockerfile -t ticketing-app:v1 .

# 전체 리소스 배포
kubectl apply -f k8s/
```

### 새 버전 배포 (롤링 업데이트)

```bash
# 새 이미지 빌드
docker build -f docker/Dockerfile -t ticketing-app:v2 .

# app.yml에서 이미지 태그 변경 후
kubectl apply -f k8s/app.yml
# → Pod이 하나씩 v1 → v2로 교체 (무중단)
```

### 롤백

```bash
kubectl rollout undo deployment/ticketing-app
# → 즉시 이전 버전으로 복구
```

### 배포 히스토리 확인

```bash
kubectl rollout history deployment/ticketing-app
```

---

## 실행 환경

| 구분 | 값 |
|------|-----|
| 플랫폼 | minikube (로컬) |
| K8s 버전 | v1.35.1 |
| 애드온 | ingress, metrics-server |
| 컨테이너 런타임 | containerd (Docker 드라이버) |

### minikube 환경의 한계

- 로컬 단일 노드라 실제 수평 확장 효과 없음 (리소스 공유)
- Ingress가 `minikube tunnel` 없이 외부 접근 불가
- 실제 수평 확장은 EKS 등 클라우드 환경에서 가능
