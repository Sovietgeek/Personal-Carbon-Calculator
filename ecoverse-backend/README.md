# 🌿 EcoVerse — Carbon Intelligence Platform Backend

Spring Boot 3.2.5 + Java 17 backend for the EcoVerse Carbon Intelligence Platform.

## 🚀 Quick Start

### Prerequisites
- **Java 17** (JDK)
- **Maven 3.8+** (or use the included Maven wrapper)

### Run the Application

```bash
cd ecoverse-backend

# Option 1: Using Maven
mvn spring-boot:run

# Option 2: Build and run JAR
mvn clean package -DskipTests
java -jar target/ecoverse-backend-0.0.1-SNAPSHOT.jar
```

The server starts on **http://localhost:8080**

### H2 Database Console
Access at: **http://localhost:8080/h2-console**
- JDBC URL: `jdbc:h2:file:./data/ecoverse`
- Username: `sa`
- Password: _(empty)_

### Swagger API Docs
Access at: **http://localhost:8080/swagger-ui.html**

---

## 📁 Project Structure

```
ecoverse-backend/
├── pom.xml
├── src/main/
│   ├── java/com/ecoverse/
│   │   ├── EcoVerseApplication.java          # Main entry point
│   │   ├── config/
│   │   │   ├── CorsConfig.java               # CORS configuration
│   │   │   ├── OpenApiConfig.java             # Swagger/OpenAPI setup
│   │   │   └── SecurityConfig.java            # Spring Security + JWT
│   │   ├── security/
│   │   │   ├── JwtTokenProvider.java          # JWT generation & validation
│   │   │   ├── JwtAuthenticationFilter.java   # JWT request filter
│   │   │   └── CustomUserDetailsService.java  # User details for auth
│   │   ├── model/                             # JPA Entities (11)
│   │   │   ├── User.java
│   │   │   ├── CarbonEntry.java
│   │   │   ├── HealthLog.java
│   │   │   ├── Product.java
│   │   │   ├── CartItem.java
│   │   │   ├── Order.java
│   │   │   ├── OrderItem.java
│   │   │   ├── Note.java
│   │   │   ├── Achievement.java
│   │   │   ├── UserAchievement.java
│   │   │   └── EmissionFactor.java
│   │   ├── dto/                               # Data Transfer Objects (27)
│   │   │   ├── ApiResponse.java               # Generic API wrapper
│   │   │   ├── auth/                          # Login, Register, Auth response
│   │   │   ├── carbon/                        # Carbon entries, summary, risk
│   │   │   ├── health/                        # Health logs, BMI, score
│   │   │   ├── dashboard/                     # Dashboard aggregation
│   │   │   ├── shop/                          # Products, cart, orders
│   │   │   ├── weather/                       # Weather response
│   │   │   ├── news/                          # News articles
│   │   │   ├── note/                          # Notes CRUD
│   │   │   ├── achievement/                   # Achievements/badges
│   │   │   └── profile/                       # Profile management
│   │   ├── repository/                        # JPA Repositories (11)
│   │   ├── service/                           # Business Logic (9)
│   │   │   ├── AuthService.java
│   │   │   ├── CarbonService.java
│   │   │   ├── HealthService.java
│   │   │   ├── WeatherService.java
│   │   │   ├── NewsService.java
│   │   │   ├── DashboardService.java
│   │   │   ├── ShopService.java
│   │   │   ├── AchievementService.java
│   │   │   └── NoteService.java
│   │   ├── controller/                        # REST Controllers (10)
│   │   │   ├── AuthController.java
│   │   │   ├── CarbonController.java
│   │   │   ├── HealthController.java
│   │   │   ├── WeatherController.java
│   │   │   ├── NewsController.java
│   │   │   ├── DashboardController.java
│   │   │   ├── ShopController.java
│   │   │   ├── AchievementController.java
│   │   │   ├── NoteController.java
│   │   │   └── ProfileController.java
│   │   └── exception/                         # Error handling (3)
│   │       ├── ResourceNotFoundException.java
│   │       ├── BadRequestException.java
│   │       └── GlobalExceptionHandler.java
│   └── resources/
│       ├── application.yml                     # App configuration
│       └── data.sql                            # Seed data (emission factors + products)
```

---

## 🔌 API Endpoints

### Authentication (`/api/auth`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/auth/register` | Register new user | ❌ |
| POST | `/api/auth/login` | Login & get JWT token | ❌ |
| GET | `/api/auth/me` | Get current user profile | ✅ |

### Carbon Tracker (`/api/carbon`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/carbon/calculate` | Calculate emission from factors | ❌ |
| POST | `/api/carbon/entries` | Add carbon entry | ✅ |
| GET | `/api/carbon/entries?period=today` | Get entries (today/week/month/year) | ✅ |
| DELETE | `/api/carbon/entries/{id}` | Delete a specific entry | ✅ |
| DELETE | `/api/carbon/entries/today/clear` | Clear all today's entries | ✅ |
| GET | `/api/carbon/summary` | Get carbon summary | ✅ |
| GET | `/api/carbon/risk` | Get risk assessment | ✅ |
| GET | `/api/carbon/breakdown` | Get category breakdown | ✅ |
| GET | `/api/carbon/suggestions` | Get personalized suggestions | ✅ |

### Health Tracker (`/api/health`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/health/log` | Log health data | ✅ |
| GET | `/api/health/logs?type=all&period=week` | Get health logs | ✅ |
| POST | `/api/health/bmi` | Calculate BMI | ✅ |
| GET | `/api/health/score` | Get health score | ✅ |
| GET | `/api/health/streak` | Get streak days | ✅ |

### Weather (`/api/weather`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/weather?lat=28.6139&lon=77.2090` | Get live weather | ✅ |

### News (`/api/news`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/news?source=all` | Get eco news (BBC/Guardian/The Hindu) | ✅ |

### Dashboard (`/api/dashboard`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/dashboard` | Get full dashboard data | ✅ |

### Eco Shop (`/api/shop`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/shop/products?category=all` | List products | ✅ |
| GET | `/api/shop/products/{id}` | Get product details | ✅ |
| POST | `/api/shop/products` | Sell a product | ✅ |
| POST | `/api/shop/cart?productId=1&quantity=1` | Add to cart | ✅ |
| DELETE | `/api/shop/cart/{id}` | Remove from cart | ✅ |
| GET | `/api/shop/cart` | View cart | ✅ |
| DELETE | `/api/shop/cart` | Clear cart | ✅ |
| POST | `/api/shop/orders` | Place order | ✅ |
| GET | `/api/shop/orders` | View order history | ✅ |

### Achievements (`/api/achievements`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/achievements` | Get all achievements + unlock status | ✅ |
| POST | `/api/achievements/check` | Check & unlock new badges | ✅ |

### Tips & Notes (`/api/notes`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/notes` | Get user's notes | ✅ |
| POST | `/api/notes` | Create a note | ✅ |
| DELETE | `/api/notes/{id}` | Delete a note | ✅ |
| GET | `/api/notes/tip` | Get daily eco tip | ✅ |
| GET | `/api/notes/tips/history?days=7` | Get tip history | ✅ |

### Profile (`/api/profile`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/profile` | Get profile | ✅ |
| PUT | `/api/profile` | Update profile & goals | ✅ |
| DELETE | `/api/profile` | Delete account | ✅ |
| GET | `/api/profile/export` | Export all user data | ✅ |

---

## 🔐 Authentication Flow

1. **Register**: `POST /api/auth/register` with `{name, email, password, country}`
2. **Login**: `POST /api/auth/login` with `{email, password}` → Returns JWT token
3. **Authenticated Requests**: Add header `Authorization: Bearer <token>`

---

## 🧪 Example API Calls

### Register
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Eco User",
    "email": "eco@example.com",
    "password": "securepass123",
    "country": "IN"
  }'
```

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "eco@example.com",
    "password": "securepass123"
  }'
```

### Add Carbon Entry
```bash
curl -X POST http://localhost:8080/api/carbon/entries \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "category": "transport",
    "type": "car-petrol",
    "distance": 15,
    "passengers": 1
  }'
```

### Get Dashboard
```bash
curl http://localhost:8080/api/dashboard \
  -H "Authorization: Bearer <your-token>"
```

---

## ⚙️ Configuration

Key settings in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8080 | Server port |
| `spring.datasource.url` | jdbc:h2:file:./data/ecoverse | H2 file database |
| `jwt.secret` | (long string) | JWT signing key |
| `jwt.expiration` | 86400000 | Token expiry (24 hours in ms) |
| `app.cors.allowed-origins` | localhost:3000, localhost:5500 | CORS origins |

### Switch to MySQL
1. Change datasource URL in `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ecoverse
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
```

---

## 📊 Emission Factors

All emission factors are scientifically backed and match the frontend:

| Category | Types | Unit |
|----------|-------|------|
| Transport | car-petrol, car-diesel, car-ev, bus, train, bicycle, walking, etc. | kg CO₂/km |
| Energy | electricity, natural-gas, lpg, solar, diesel-generator | kg CO₂/kWh or kg |
| Food | vegan, vegetarian, beef, poultry, fish, etc. | kg CO₂/meal |
| Shopping | clothing, electronics, furniture, etc. | kg CO₂/₹ |
| Waste | landfill, recycled, composted, e-waste | kg CO₂/kg |
| Digital | streaming, video-call, crypto, AI query | kg CO₂/unit |

---

## 🏆 Achievement System

8 built-in achievements that auto-unlock based on user activity:

| Code | Name | Condition |
|------|------|-----------|
| `first_log` | First Step | Log first carbon entry |
| `week_streak` | Week Warrior | 7-day streak |
| `carbon_saver` | Carbon Saver | Save 10kg CO₂ |
| `health_enthusiast` | Health Enthusiast | Log 10 health entries |
| `early_bird` | Early Bird | Log sleep before 10pm |
| `marathon_runner` | Marathon Runner | Log 10,000 steps |
| `eco_shopper` | Eco Shopper | Buy 5 eco products |
| `zero_day` | Zero Emission Day | 0 kg CO₂ in a day |

---

## 🛠 Tech Stack

- **Framework**: Spring Boot 3.2.5
- **Language**: Java 17
- **Database**: H2 (default) / MySQL (production)
- **ORM**: Spring Data JPA + Hibernate
- **Security**: Spring Security + JWT (jjwt 0.12.3)
- **API Docs**: Springdoc OpenAPI (Swagger)
- **HTTP Client**: WebClient (WebFlux)
- **Build Tool**: Maven
- **Utilities**: Lombok
