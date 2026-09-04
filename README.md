# 🌍 EcoVerse — Carbon Intelligence Platform

A full-stack sustainability platform that tracks your carbon footprint, monitors health, shops eco-friendly products, and provides AI-powered environmental insights.

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)
![Java](https://img.shields.io/badge/Java-17-orange)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Render](https://img.shields.io/badge/Deploy-Render-46E3B7)

---

## ✨ Features

### 🌱 Carbon Tracker
- Log daily carbon emissions by category (transport, food, energy, etc.)
- AI-powered emission calculations with real emission factors
- Carbon budget tracking with visual progress
- Predictive analytics and risk assessment

### 💚 Health Monitor
- Track steps, sleep, water intake, calories
- Health score calculation with BMI
- Goal tracking and streak system
- Instant health metrics dashboard

### 🛒 Eco Shop
- 300+ eco-friendly products across 10 categories
- Cart management and secure checkout (Razorpay)
- Product ratings and reviews
- Seller portal for product management

### 🤖 AI Assistant
- Powered by Google Gemini
- Eco-friendly tips and recommendations
- Streaming chat with real-time responses
- Contextual suggestions based on your data

### 📰 News & Weather
- Location-based environmental news
- Real-time weather with Open-Meteo API
- Climate-specific news feeds

### 🛡️ Admin Control Center
- Full admin panel with role-based access
- User management (block/unblock, role changes)
- Review moderation system
- Audit log browser
- AI usage monitoring
- System health dashboard
- Analytics with interactive charts
- 3-layer security (UI + API + Data)

---

## 🏗️ Architecture

```
EcoVerse-Complete-Latest/
├── ecoverse-backend/           # Spring Boot REST API
│   ├── src/main/java/          # Java source code
│   ├── src/main/resources/     # Config, migrations, static frontend
│   └── Dockerfile              # Multi-stage Docker build
├── docker-compose.yml          # Local development setup
├── render.yaml                 # Render.com deployment blueprint
├── .env.example                # Environment variable template
└── README.md
```

**Tech Stack:**
- **Backend:** Spring Boot 3.2.5, Spring Security, JWT Auth, Flyway
- **Database:** PostgreSQL 16 (prod), H2 (dev)
- **Frontend:** Vanilla JS, Chart.js, CSS Custom Properties
- **AI:** Google Gemini (Spring AI)
- **Payments:** Razorpay
- **Deploy:** Docker + Render.com

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Docker & Docker Compose
- Git

### 1. Clone & Configure

```bash
git clone https://github.com/Sovietgeek/Personal-Carbon-Calculator.git
cd Personal-Carbon-Calculator

# Copy env template and fill in values
cp .env.example .env
# Edit .env — set POSTGRES_PASSWORD, JWT_SECRET, GEMINI_API_KEY, ADMIN_EMAIL
```

### 2. Run with Docker (Recommended)

```bash
docker-compose up -d
```

App will be available at **http://localhost:8081**

### 3. First-Time Admin Setup

1. Register with the email you set in `ADMIN_EMAIL` (e.g., `admin@ecoverse.app`)
2. On startup, the app auto-promotes that user to ADMIN
3. Login again — you'll see the Admin Control Center

---

## 🔐 Security

| Layer | Protection |
|-------|-----------|
| **UI** | Admin nav hidden from non-admins, client-side guard |
| **API** | `@PreAuthorize("hasRole('ADMIN')")` on all admin endpoints |
| **Data** | `toSafeUserResponse()` strips passwords, tokens, secrets |
| **Auth** | JWT + httpOnly refresh cookie, session invalidation on block |
| **Admin** | Cannot promote via API — only `ADMIN_EMAIL` env var |

---

## 📋 Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `POSTGRES_PASSWORD` | ✅ | PostgreSQL password |
| `JWT_SECRET` | ✅ | Min 256-bit secret (`openssl rand -base64 64`) |
| `ADMIN_EMAIL` | ✅ | Email to promote to ADMIN on startup |
| `ADMIN_PASSWORD` | Optional | If set, creates admin user automatically |
| `GEMINI_API_KEY` | Optional | Google Gemini API key for AI chat |
| `RAZORPAY_KEY_ID` | Optional | Razorpay test/live key |
| `RAZORPAY_KEY_SECRET` | Optional | Razorpay secret |
| `GOOGLE_CLIENT_ID` | Optional | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | Optional | Google OAuth2 secret |

Full list in `.env.example`

---

## 🌐 Deploy to Render.com (Free)

1. Push code to GitHub
2. Go to [dashboard.render.com](https://dashboard.render.com) → **New** → **Blueprint**
3. Select this repo — `render.yaml` is auto-detected
4. Set these env vars in Render dashboard:
   - `GEMINI_API_KEY` — your Gemini API key
   - `ADMIN_EMAIL` — `admin@ecoverse.app`
   - `ADMIN_PASSWORD` — a strong password (admin auto-created)
5. Click **Apply** — done! 🎉

Free tier: 512MB RAM, app sleeps after 15min idle, PostgreSQL expires after 90 days.

---

## 📁 Project Structure (Backend)

```
ecoverse-backend/src/main/java/com/ecoverse/
├── config/          # Security, CORS, AI, async config
├── controller/      # REST controllers (Auth, Carbon, Health, Shop, Admin, AI...)
├── dto/             # Request/Response DTOs
├── exception/       # Global exception handling
├── model/           # JPA entities (User, CarbonEntry, Product, Order...)
├── repository/      # Spring Data JPA repositories
├── scheduler/       # Background schedulers
├── security/        # JWT filter, rate limiting, CSRF, input sanitization
├── service/         # Business logic services
└── util/            # Cookie, input sanitizer, password validator
```

---

## 🧪 Default Credentials (Docker/Dev)

| Email | Password | Role |
|-------|----------|------|
| `admin@ecoverse.app` | (set via `ADMIN_PASSWORD`) | ADMIN |

Register new users at the login screen — no manual DB setup needed.

---

## 📄 License

This project is for educational and personal use.

---

<p align="center">
  Built with 💚 for a greener planet
</p>
