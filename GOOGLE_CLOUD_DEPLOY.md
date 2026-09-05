# 🚀 EcoVerse — Google Cloud Free Tier Deployment Guide

## Prerequisites

1. **Google Cloud Account** — [console.cloud.google.com](https://console.cloud.google.com)
2. **gcloud CLI installed** — [Download](https://cloud.google.com/sdk/docs/install)
3. **Billing account** — Required for free tier (won't charge if within limits)

---

## Step 1: Create Google Cloud Project

```bash
# Login to Google Cloud
gcloud auth login

# Create new project
gcloud projects create ecoverse-app --name="EcoVerse"

# Set as active project
gcloud config set project ecoverse-app

# Enable required APIs
gcloud services enable cloudbuild.googleapis.com run.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com artifactregistry.googleapis.com
```

---

## Step 2: Create Cloud SQL PostgreSQL (Free Tier)

```bash
# Create PostgreSQL instance (f1-micro = free tier eligible)
gcloud sql instances create ecoverse-db \
  --database-version=POSTGRES_15 \
  --tier=db-f1-micro \
  --region=asia-south1 \
  --storage-size=10 \
  --backup-start-time=03:00

# Set root password
gcloud sql users set-password postgres \
  --instance=ecoverse-db \
  --password="YourStrongPassword123!"

# Create database
gcloud sql databases create ecoverse --instance=ecoverse-db

# Get connection details
gcloud sql instances describe ecoverse-db --format="value(connectionName)"
```

**Note down:**
- Connection Name: `ecoverse-app:asia-south1:ecoverse-db`
- Database: `ecoverse`
- User: `postgres`
- Password: (what you set above)

---

## Step 3: Store Secrets in Secret Manager

```bash
# Database URL (JDBC format for Cloud SQL)
echo -n "jdbc:postgresql:///ecoverse?socketFactory=com.google.cloud.sql.postgres.SocketFactory&cloudSqlInstance=ecoverse-app:asia-south1:ecoverse-db" | \
  gcloud secrets create ecoverse-db-url --data-file=-

# DB Username
echo -n "postgres" | gcloud secrets create ecoverse-db-user --data-file=-

# DB Password
echo -n "YourStrongPassword123!" | gcloud secrets create ecoverse-db-pass --data-file=-

# JWT Secret (generate secure key)
openssl rand -base64 64 | tr -d '\n' | gcloud secrets create ecoverse-jwt-secret --data-file=-

# Gemini API Key (get yours from https://aistudio.google.com/apikey)
echo -n "YOUR_GEMINI_API_KEY" | \
  gcloud secrets create ecoverse-gemini-key --data-file=-

# Admin Email
echo -n "admin@ecoverse.app" | gcloud secrets create ecoverse-admin-email --data-file=-

# Admin Password
echo -n "EcoVerse@2026" | gcloud secrets create ecoverse-admin-password --data-file=-
```

---

## Step 4: Create Artifact Registry Repository

```bash
gcloud artifacts repositories create ecoverse \
  --repository-format=docker \
  --location=asia-south1 \
  --description="EcoVerse Docker images"
```

---

## Step 5: Grant Permissions

```bash
# Get project number
PROJECT_NUMBER=$(gcloud projects describe ecoverse-app --format="value(projectNumber)")

# Grant Cloud Build access to deploy
gcloud projects add-iam-policy-binding ecoverse-app \
  --member="serviceAccount:${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com" \
  --role="roles/run.admin"

gcloud projects add-iam-policy-binding ecoverse-app \
  --member="serviceAccount:${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com" \
  --role="roles/iam.serviceAccountUser"

# Grant Cloud Run access to secrets
gcloud projects add-iam-policy-binding ecoverse-app \
  --member="serviceAccount:${PROJECT_NUMBER}-compute@developer.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"

# Grant Cloud Run access to Cloud SQL
gcloud projects add-iam-policy-binding ecoverse-app \
  --member="serviceAccount:${PROJECT_NUMBER}-compute@developer.gserviceaccount.com" \
  --role="roles/cloudsql.client"
```

---

## Step 6: Deploy!

```bash
cd C:\Users\utkar\Desktop\EcoVerse-Complete-Latest

# Submit build and deploy
gcloud builds submit --config cloudbuild.yaml
```

Or for one-click deploy from GitHub (connect repo in Cloud Build):
```bash
gcloud builds triggers create github \
  --repo-owner=Sovietgeek \
  --repo-name=Personal-Carbon-Calculator \
  --branch-pattern=main \
  --build-config=cloudbuild.yaml
```

---

## Step 7: Verify Deployment

```bash
# Get service URL
gcloud run services describe ecoverse-backend \
  --region=asia-south1 \
  --format="value(status.url)"

# Test health
curl https://ecoverse-backend-xxxxx-uc.a.run.app/actuator/health

# Test login
curl -X POST https://ecoverse-backend-xxxxx-uc.a.run.app/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ecoverse.app","password":"EcoVerse@2026"}'
```

---

## Free Tier Limits (Always Free)

| Service | Monthly Free Limit |
|---------|-------------------|
| Cloud Run | 2M requests, 360K vCPU-s, 180K CPU-s |
| Cloud SQL (f1-micro) | 1 instance, 10GB storage |
| Artifact Registry | 0.5 GB storage |
| Secret Manager | 6 secrets, 10K operations |
| Cloud Build | 120 minutes/month |

**⚠️ Important:** Set budget alerts to avoid unexpected charges!

```bash
gcloud billing budgets create \
  --billing-account=YOUR_BILLING_ACCOUNT \
  --display-name="EcoVerse Budget" \
  --budget-amount=5 \
  --alert-threshold-rules=percent=50,percent=90
```

---

## Troubleshooting

### Cold Start Slow?
Set minimum instances to 1 (costs ~$5/month):
```bash
gcloud run services update ecoverse-backend \
  --region=asia-south1 \
  --min-instances=1
```

### Database Connection Issues?
Check Cloud SQL proxy is enabled and service account has `cloudsql.client` role.

### View Logs
```bash
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=ecoverse-backend" \
  --limit=50 --format=json
```

---

<p align="center">
  Built with 💚 | Deployed on Google Cloud Free Tier
</p>
