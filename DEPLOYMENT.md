# SeatLock Deployment & Cloud Proof Guide 🚀

This guide provides exact, step-by-step instructions for:
1. **Primary Local Demo**: Minikube on developer laptop ($0, 100% reproducible).
2. **One-Time Cloud Proof**: Oracle Cloud Free Tier + K3s + Neon PostgreSQL ($0, for cloud verification and 2,000+ VU load testing).
3. **Frontend Standalone Deployment**: Vercel ($0, permanent portfolio showcase).

---

## 💰 Deployment & Cost Reference Table

| Environment | Config | Monthly Cost | Trade-Off Accepted | Role in Interview |
|---|---|---|---|---|
| **Minikube (Local)** | 4 CPU, 6 GB RAM, 3 Replicas, In-Cluster Postgres | **$0** | No real network, runs on host | **PRIMARY DEMO** (always reliable, zero network dependency) |
| **Oracle VM + K3s (No LB)** | 4 OCPU, 24 GB RAM (ARM Ampere A1), NodePort | **$0** | No TLS certificate, manual IP access | **CLOUD SCALE PROOF** (one-time deployment for 2,000+ VU run) |
| **Oracle VM + K3s + Cloud LB** | 4 OCPU, Cloud OCI Load Balancer + Let's Encrypt | **~$20** | Stable endpoint, TLS, single-node | Optional production upgrade |
| **Managed DB Upgrade** | OCI VM + Managed AWS RDS / Neon Pro | **~$45+** | Needed once traffic exceeds Neon free tier limits | Enterprise production scale |

---

## 1. Primary Demo: Local Minikube Cluster (1-Command)

```bash
# Linux / macOS
./demo.sh

# Windows PowerShell
./demo.ps1
```

### Manual Step-by-Step Alternative:
```bash
# 1. Start Minikube with exact laptop headroom constraints
minikube start --cpus=4 --memory=6g --driver=docker

# 2. Point Docker CLI at Minikube
eval $(minikube docker-env)

# 3. Build container images
docker build -t seatlock-backend:latest ./seatlock-backend
docker build -t seatlock-frontend:latest ./seatlock-frontend

# 4. Deploy all manifests (Postgres + ConfigMap + Secrets + 3 Backend Pods)
kubectl apply -k ./k8s

# 5. Check rollout status
kubectl rollout status deployment/seatlock-backend

# 6. Access UI
minikube service seatlock-backend --url
```

---

## 2. One-Time Cloud Proof: Oracle Cloud Free Tier + K3s + Neon

> **Purpose**: A one-time deployment proof to run a 2,000+ VU benchmark where the app isn't competing with the load generator for host CPU, captured with screenshots and logs, then torn down.

### Step 2.1: Provision Free Tier Resources
1. **Compute**: Create an **Ampere A1 Compute instance** on Oracle Cloud Infrastructure (OCI) Free Tier:
   - OS: Ubuntu 22.04 LTS
   - Shape: `VM.Standard.A1.Flex` (4 OCPUs, 24 GB RAM — 100% free)
   - Open Ports: In OCI Ingress Rules, allow TCP `80`, `443`, `6443`, `30080`.
2. **Database**: Provision a free PostgreSQL database on [Neon.tech](https://neon.tech):
   - Copy the connection string: `postgres://user:password@ep-xyz.us-east-2.aws.neon.tech/seatlock?sslmode=require`.

### Step 2.2: Install Lightweight Kubernetes (K3s)
SSH into the Oracle VM and run:
```bash
# Install K3s (disabling default Traefik to keep footprint minimal)
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik" sh -

# Allow non-root kubectl access
mkdir -p ~/.kube && sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config && sudo chown $(id -u):$(id -g) ~/.kube/config
```

### Step 2.3: Deploy SeatLock with Neon DB
```bash
# Clone the repository
git clone https://github.com/seatlock/seatlock.git && cd seatlock

# Update k8s/configmap.yaml with your Neon JDBC URL:
# SPRING_DATASOURCE_URL: jdbc:postgresql://ep-xyz.us-east-2.aws.neon.tech/seatlock?sslmode=require

# Update k8s/secret.yaml with your Neon username and password:
# SPRING_DATASOURCE_USERNAME: <neon-user>
# SPRING_DATASOURCE_PASSWORD: <neon-password>

# Apply manifests without in-cluster Postgres (using Neon)
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/backend-deployment.yaml
kubectl apply -f k8s/backend-service.yaml
```

### Step 2.4: Execute 2,000+ VU Cloud Benchmark
From your local machine (outside the cluster), point k6 at the Oracle VM public IP:
```bash
TARGET_URL="http://<ORACLE_VM_PUBLIC_IP>:30080" k6 run --vus 2000 --duration 1m k6/concurrency-test.js
```

### Step 2.5: Teardown & Clean Up
Once logs and benchmark metrics are recorded for your interview portfolio:
```bash
# Remove K3s resources
kubectl delete -f k8s/backend-deployment.yaml -f k8s/backend-service.yaml

# Uninstall K3s
/usr/local/bin/k3s-uninstall.sh

# Terminate OCI compute instance via Oracle Cloud Console & delete Neon project
```

---

## 3. Permanent Frontend Showcase: Vercel Deployment

Deploy the React frontend for free on Vercel as a permanent, clickable live link on your resume / GitHub profile:

1. Import the `seatlock-frontend` folder into [Vercel](https://vercel.com).
2. Set Environment Variables:
   - `REACT_APP_API_URL`: Your cloud backend URL or leave blank.
   - `REACT_APP_WS_URL`: Your WebSocket endpoint or leave blank.
3. Deploy!
4. **Offline / Mock Fallback Mode**: The frontend is built to detect when the backend is offline and display a clean status badge: `○ Offline — Backend Dormant (Spin up via ./demo.sh to test live sync)`.
