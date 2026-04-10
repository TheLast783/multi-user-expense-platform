# Multi-User Expense Monitoring & Automated Invoicing Platform

A professional-grade, full-stack application for automated expense tracking, real-time banking SMS interception, and multi-tenant cloud synchronization. Designed for the high-performance management of financial data across thousands of users.

---

## 🔒 Note on Repository Structure & Security
To protect intellectual property and sensitive credentials, this public repository contains the secondary **Android Client** code and the **System Architecture**.

- **Core Backend**: The original FastAPI business logic, database schemas, and mailing engines have been moved to a **Private Repository** to ensure secure production deployment.
- **Secrets Management**: All API keys, Google Client IDs, and Server Endpoints have been purged from the version history and are now managed via local `BuildConfig` and environment variables.

---

## Overview
This platform automates the bridge between banking transactions and financial accountability. It intercepts banking SMS messages on Android, parses them into structured records, and synchronizes them with a multi-tenant FastAPI cloud backend. Key features include automated e-mail invoicing for debt management and real-time transaction deduplication.

### **Key Features**
- **Real-time Interceptor**: Kotlin-based `BroadcastReceiver` for millisecond-fast SMS data extraction.
- **Data Integrity**: Advanced "Instant-Save" and deduplication locks to ensure 100% transaction capture accuracy.
- **Multi-Tenant Cloud**: FastAPI/PostgreSQL architecture with strict user-level data isolation via Google OAuth2.
- **Auto-Invoicing**: Trigger-based HTML email dispatch for payment receipts and outstanding balance alerts.
- **Professional Android UI**: Modern Jetpack Compose interface with Room DB for offline persistence.

---

## Tech Stack
| Component | Technologies |
| :--- | :--- |
| **Android** | Kotlin, Jetpack Compose, Room Database, Retrofit, Coroutines |
| **Backend** | Python 3.12+, FastAPI, SQLAlchemy, PostgreSQL, HTTPX |
| **Auth** | Google OAuth 2.0 (Identity isolation) |
| **Deployment** | Render (Production Cloud) |

---

## Architecture

```mermaid
graph TD
    A[Android Phone] -->|Banking SMS| B(SmsReceiver)
    B -->|Parse| C{Instant Save}
    C -->|Local DB| D[(Room SQL)]
    C -->|Overlay| E[User Dialog]
    E -->|Update| D
    D -->|Sync API| F[Cloud Backend]
    F -->|Process| G[(PostgreSQL)]
    F -->|Async| H[Gmail API]
    H -->|Email| I[Contact Inbox]
```

---

## Technical Highlights
This project demonstrates expertise in:
- **Scalability**: Designed to handle background tasks for 2,000+ simultaneous users.
- **Reliability**: Zero-loss data capture even with dismissed notifications.
- **Security**: OAuth2 token-based row-level security in databases.

---

## License
This project is open-source and available under the MIT License.
