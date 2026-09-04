# 🐳 EcoVerse — Docker Deployment Guide

Complete guide to run EcoVerse with Docker + PostgreSQL.

---

## 📋 Prerequisites

| Tool | Version | Install |
|---|---|---|
| **Docker Desktop** | Latest | [docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop) |
| **Git** | Latest | [git-scm.com](https://git-scm.com) |

> That's it! No Java, Maven, or PostgreSQL needed on your machine — Docker handles everything.

---

## 🚀 Quick Start (3 commands)

```bash
# 1. Clone the repo
git clone <your-repo-url>
cd EcoVerse-Complete-Latest

# 2. Copy environment config
cp .env.example .env

# 3. Start everything!
docker compose up -d
```

**Done!** 🎉 Your app is live at:

| Service | URL |
|---|---|
| **EcoVerse App** | http://localhost:8081 |
| **Swagger API Docs** | http://localhost:8081/swagger-ui.html |
| **Health Check** | http://localhost:8081/api/health |

---

## 🔧 Configuration

### Environment Variables (`.env` file)

| Variable | Default | Description |
|---|---|---|
| `POSTGRES_DB` | ecoverse | Database name |
| `POSTGRES_USER` | ecoverse | Database user |
| `POSTGRES_PASSWORD` | ecoverse123 | Database password ⚠️ Change in production! |
| `POSTGRES_PORT` | 5432 | PostgreSQL port |
| `BACKEND_PORT` | 8081 | Backend server port |
| `SPRING_PROFILES_ACTIVE` | prod | Spring profile (dev=H2, prod=PostgreSQL) |
| `HIBERNATE_DDL` | update | Schema strategy (update/validate/none) |
| `JWT_SECRET` | (long string) | JWT signing key ⚠️ Change in production! |
| `JWT_EXPIRATION` | 86400000 | Token expiry in ms (24 hours) |
| `JAVA_OPTS` | -Xmx512m -Xms256m | JVM memory settings |
| `CORS_ORIGINS` | localhost:3000,5500,8081 | Allowed CORS origins |

---

## 📊 Common Commands

```bash
# Start all services
docker compose up -d

# Start with rebuild (after code changes)
docker compose up -d --build

# View logs
docker compose logs -f backend

# View PostgreSQL logs
docker compose logs -f postgres

# Stop all services
docker compose down

# Stop and delete volumes (RESET DATABASE)
docker compose down -v

# Restart only backend
docker compose restart backend

# Check running containers
docker compose ps

# Shell into backend container
docker compose exec backend sh

# Shell into PostgreSQL
docker compose exec postgres psql -U ecoverse -d ecoverse
```

---

## 🗄️ Database Management

### pgAdmin (Visual UI)

```bash
# Start pgAdmin alongside other services
docker compose --profile debug up -d
```

Access at: **http://localhost:5050**
- Email: `admin@ecoverse.com`
- Password: `admin123`

**Add server connection:**
| Field | Value |
|---|---|
| Host | postgres |
| Port | 5432 |
| Username | ecoverse |
| Password | ecoverse123 |

### Manual SQL

```bash
# Connect to PostgreSQL
docker compose exec postgres psql -U ecoverse -d ecoverse

# List tables
\dt

# Query emission factors
SELECT * FROM emission_factors;

# Query products
SELECT id, name, price, category FROM products;

# Exit
\q
```

---

## 🔄 Development Workflow

### Method 1: Docker (Recommended)

```bash
# Make code changes → rebuild → test
docker compose up -d --build backend
```

### Method 2: Local Java + Docker DB

```bash
# Start only PostgreSQL via Docker
docker compose up -d postgres

# Run backend locally (needs Java 17 + Maven)
cd ecoverse-backend
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ecoverse \
SPRING_DATASOURCE_USERNAME=ecoverse \
SPRING_DATASOURCE_PASSWORD=ecoverse123 \
./mvnw spring-boot:run
```

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────┐
│  Docker Compose Network: ecoverse-network         │
│                                                    │
│  ┌─────────────────┐     ┌──────────────────────┐ │
│  │   PostgreSQL     │◄────│   Spring Boot        │ │
│  │   Port: 5432     │     │   Port: 8081         │ │
│  │   Volume: pgdata │     │   Profile: prod      │ │
│  └─────────────────┘     │   JVM: 512MB          │ │
│                           └──────────────────────┘ │
│                                                    │
│  ┌─────────────────┐                               │
│  │   pgAdmin        │  (Optional, --profile debug) │
│  │   Port: 5050     │                               │
│  └─────────────────┘                               │
└──────────────────────────────────────────────────┘
         │
         ▼
    User Browser
    http://localhost:8081
```

---

## 🔒 Production Checklist

Before deploying to real users:

- [ ] Change `POSTGRES_PASSWORD` to a strong random password
- [ ] Change `JWT_SECRET` to a new 256-bit random string (generate with `openssl rand -base64 64 | tr -d '\n'`)
- [ ] Set `SPRING_PROFILES_ACTIVE=prod`
- [ ] Remove pgAdmin profile from production
- [ ] Add HTTPS (use Nginx reverse proxy + Let's Encrypt — see [Reverse Proxy & HTTPS](#-reverse-proxy--https) below)
- [ ] Set up database backups (see `BACKUP_RESTORE.md`)
- [ ] Restrict CORS origins to your domain only (`CORS_ORIGINS=https://yourdomain.com`)
- [ ] Set `COOKIE_SECURE=true` (default) — ensures cookies only sent over HTTPS
- [ ] Set `COOKIE_DOMAIN=.yourdomain.com` for cross-subdomain cookie sharing
- [ ] Set `JAVA_OPTS` based on server RAM
- [ ] Set `RAZORPAY_MODE=test` until ready to go live (NEVER set to `live` without real keys)

---

## 🔀 Reverse Proxy & HTTPS

EcoVerse is designed to run **behind a TLS-terminating reverse proxy** (Nginx, Caddy, Traefik, AWS ALB). The Spring Boot container itself does **not** handle TLS.

### How It Works

```
User ──HTTPS──▶ Nginx/Caddy (port 443) ──HTTP──▶ Spring Boot (port 8081)
                 TLS terminates here              Trusts X-Forwarded-* headers
```

### Forwarded Headers

All non-default profiles (`dev`, `staging`, `prod`) set:

```yaml
server:
  forward-headers-strategy: FRAMEWORK
```

This tells Spring Boot to trust `X-Forwarded-Proto`, `X-Forwarded-For`, and `X-Forwarded-Port` headers from the reverse proxy. This is essential for:

- **HSTS**: `request.isSecure()` returns `true` only when `X-Forwarded-Proto: https` is present
- **Redirect URLs**: Spring generates `https://` URLs correctly
- **Rate limiting**: IP extraction from `X-Forwarded-For` works correctly

### Nginx Example

```nginx
server {
    listen 443 ssl http2;
    server_name yourdomain.com;

    ssl_certificate     /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;

    location / {
        proxy_pass http://localhost:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port $server_port;
    }
}

# Redirect HTTP to HTTPS
server {
    listen 80;
    server_name yourdomain.com;
    return 301 https://$host$request_uri;
}
```

### Caddy Example (Automatic HTTPS)

```Caddyfile
yourdomain.com {
    reverse_proxy localhost:8081
}
```

Caddy automatically adds `X-Forwarded-*` headers and manages Let's Encrypt certificates.

---

## 🍪 Cookie & CSRF Security

### Cookie Configuration

| Setting | Value | Notes |
|---|---|---|
| **HttpOnly** | `true` | Always set; prevents JavaScript access |
| **SameSite** | `Lax` | Always set; primary CSRF protection |
| **Secure** | `${COOKIE_SECURE:true}` | Configurable; sends cookie only over HTTPS |
| **Path** | `/api/auth` | Restricts cookie to auth endpoints only (not `/`) |
| **Domain** | `${COOKIE_DOMAIN:}` | Set to `.yourdomain.com` for cross-subdomain |

### CSRF Protection

EcoVerse uses **defense-in-depth** CSRF protection (no traditional CSRF tokens, which are inappropriate for stateless JWT APIs):

1. **SameSite=Lax** (primary): Browsers don't send cookies on cross-site POST/PUT/DELETE requests
2. **Origin/Referer validation** (secondary): `CsrfOriginValidationFilter` checks `Origin` and `Referer` headers on `/api/auth/refresh` and `/api/auth/logout` against the CORS allowed-origins list
3. **JWT Bearer tokens** (tertiary): API endpoints use `Authorization: Bearer <token>` — not cookies — so they're immune to CSRF by design

### CORS Configuration

- CORS origins are **explicitly listed** — never `*`
- `ProductionStartupValidator` **refuses to start** in `prod`/`staging` profiles if `CORS_ORIGINS` contains `*`
- `allowCredentials(true)` is safe because origins are always explicit

---

## 🔐 Security Headers

EcoVerse automatically sets these security headers:

| Header | Value | When |
|---|---|---|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | When `request.isSecure()` is true (behind TLS proxy with forwarded headers) |
| `X-Content-Type-Options` | `nosniff` | Always |
| `X-Frame-Options` | `DENY` | Always |
| `X-XSS-Protection` | `0` | Always (modern browsers don't need this; header disabled to avoid legacy quirks) |

---

## 📏 Resource Limits

EcoVerse enforces resource limits to prevent abuse:

| Resource | Limit | Configuration |
|---|---|---|
| HTTP form POST size | 100KB | `server.tomcat.max-http-form-post-size=100KB` |
| Request swallow size | 1MB | `server.tomcat.max-swallow-size=1MB` |
| File upload size | 5MB | `spring.servlet.multipart.max-file-size=5MB` |
| Multipart request size | 5MB | `spring.servlet.multipart.max-request-size=5MB` |
| Default page size | 20 | `spring.data.web.pageable.default-page-size=20` |
| Maximum page size | 100 | `spring.data.web.pageable.max-page-size=100` |

---

## 🐛 Troubleshooting

| Problem | Solution |
|---|---|
| Backend won't start | `docker compose logs backend` — check DB connection |
| PostgreSQL not ready | Wait 30s, backend auto-retries health check |
| Port 8081 already in use | Change `BACKEND_PORT` in `.env` |
| Port 5432 already in use | Change `POSTGRES_PORT` in `.env` |
| Can't connect to DB | Ensure both containers are running: `docker compose ps` |
| Seed data not loaded | Only runs on first start. Reset: `docker compose down -v && docker compose up -d` |
| Build fails | Ensure Docker has 4GB+ RAM (Docker Desktop → Settings → Resources) |
| Slow build first time | Normal — Maven downloads dependencies. Subsequent builds are cached. |

---

## 📁 File Structure

```
EcoVerse-Complete-Latest/
├── docker-compose.yml          # Main Docker config
├── .env                        # Environment variables (secrets)
├── .env.example                # Template for .env
├── .gitignore                  # Git ignore rules
├── DEPLOY.md                   # This file
│
└── ecoverse-backend/
    ├── Dockerfile               # Multi-stage Docker build
    ├── .dockerignore            # Docker build ignore
    ├── pom.xml                  # Maven config (PostgreSQL + H2 drivers)
    │
    └── src/main/
        ├── java/com/ecoverse/
        │   ├── config/          # Security, CORS, OpenAPI
        │   ├── controller/      # REST APIs
        │   ├── dto/             # Data Transfer Objects
        │   ├── exception/       # Error handling
        │   ├── model/           # JPA Entities
        │   ├── repository/      # Data access
        │   ├── security/        # JWT auth
        │   └── service/         # Business logic
        │
        └── resources/
            ├── application.yml      # Multi-profile config
            ├── init-db.sql          # PostgreSQL seed data
            ├── data.sql             # H2 seed data (legacy)
            └── static/              # Frontend (HTML/CSS/JS)
                ├── index.html
                ├── styles.css
                └── app.js
```
