# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Moabogi** (모아보기) is a movie/drama viewing record and personal taste analysis system for a university team project. It allows users to track what they've watched, when they watched it, ratings, reviews, and viewing statistics by genre.

- **Team**: 팀 E from 정보시스템구축및관리 2026 course
- **Current version**: v0.1.1 (2026-10-16 baseline with improvements)
- **Current status**: Core features implemented; board/comments/file attachments deferred

## Technology Stack

| Component | Version |
|-----------|---------|
| **Language** | Java 8 syntax (compiled with JDK 15) |
| **Framework** | Spring Legacy MVC 5.3.39 |
| **App Server** | Apache Tomcat 9.0.123 |
| **Database** | MySQL Community 8.4.11 LTS (InnoDB, utf8mb4) |
| **Data Access** | MyBatis 3.5.19 + MyBatis-Spring 2.1.2 |
| **Security** | Spring Security 5.8.16 with BCrypt (cost 12) |
| **View Layer** | JSP 2.3, JSTL 1.2, custom CSS/JS |
| **Build** | Maven 3.9.9 with WAR packaging |
| **IDE** | Spring Tool Suite 3.9.18 (or Eclipse 4.21) |
| **Logging** | Logback 1.2.13 |

All source/target compilation settings are Java 1.8. The codebase uses the course-provided templates and runs the course-specific JDK 15.

## Build & Development Commands

Run all commands from the **repository root** unless otherwise noted.

### Basic Build & Server Control

```bat
# Build the project to review-system/target/ROOT.war
build.cmd

# Start local MySQL (creates/starts if not running)
start-db.cmd

# Start Tomcat server on http://localhost:8080
# Automatically runs build.cmd first and checks DB status
start-server.cmd

# Stop Tomcat server
stop-server.cmd

# Stop MySQL
stop-db.cmd

# Start Spring Tool Suite with course-specific workspace
start-sts.cmd
```

### Development Shell

```bat
# Open a command prompt with JDK 15 and Maven 3.9.9 in PATH
dev-shell.cmd
```

From `dev-shell.cmd`, use standard Maven commands:
```bash
cd review-system
mvn clean package            # Full build
mvn test                      # Run unit tests only (no DB)
mvn verify                    # Run all tests including integration tests
mvn -e                        # Verbose error output
```

### Database Management

```powershell
# Database selection:
# - review_system: Main app database
# - review_system_test: Integration test database (cleared on each test run)
# - review_system_perf: Performance test database

# For DBeaver GUI:
start-dbeaver.cmd
# Then: Database → New Database Connection → MySQL
# Host: localhost, Port: 3306, Database: review_system
# Username: review_app, Password: (from external config)

# Promote a user to admin (must run from repo root):
.\.setup\promote_admin.ps1 -Username <registered_id>
```

### Database Credentials & Configuration

DB credentials are stored **outside the repository** in:
- `%USERPROFILE%\dev\review-system\config\application.properties` (app)
- `%USERPROFILE%\dev\review-system\config\test.properties` (integration tests)

The app reads config in this priority order:
1. JVM option: `-Dreview.config=/absolute/path`
2. Environment variable: `REVIEW_CONFIG=/absolute/path`
3. User profile default: `%USERPROFILE%\dev\review-system\config\application.properties`
4. Environment variables: `DB_URL`, `DB_USER`, `DB_PASSWORD` override file values

Account permissions:
- `review_app` user: SELECT, INSERT, UPDATE, DELETE on `review_system` only
- `root`: Schema setup and administration only (not used by the app)

## Architecture

### Overall Flow

```
Controller → Service(@Transactional) → MyBatis Mapper → MySQL
```

No Spring Boot, no separate Node server. Pure Spring Legacy MVC with XML configuration.

### Package Structure

```
src/main/java/org/mnu/
├── config/           # Spring configuration and DB settings
│   ├── RootConfig.java           # Root application context
│   ├── SecurityConfig.java       # Authentication/authorization
│   └── LocalSettings.java        # Load external properties
├── controller/       # HTTP controllers
│   ├── AuthController            # Login/signup
│   ├── HomeController            # Dash/health
│   ├── WorkController            # Work (movie/drama) CRUD
│   ├── RecordController          # Viewing record CRUD
│   ├── StatsController           # Statistics/analytics
│   ├── AppAdvice                 # @ControllerAdvice for errors
│   └── Current, SessionTokens    # Utility helpers
├── service/         # Business logic, @Transactional
│   ├── AccountService            # User accounts
│   ├── WorkService               # Work/genre management
│   ├── RecordService             # Viewing records + state transitions
│   └── StatsService              # Analytics and aggregations
├── mapper/          # MyBatis mapper interfaces
│   ├── AccountMapper
│   ├── WorkMapper
│   ├── RecordMapper
│   └── StatsMapper
└── domain/          # Input/output DTO objects
    ├── SignupForm, WorkForm, RecordForm, QueryFilter
    ├── AccountPrincipal         # Spring Security principal
    └── InputError               # Validation error holder
```

### Database Schema

**7 core tables** (see `db/schema.sql` for full definitions):

- `app_user` — User accounts (ID, BCrypt hash, name, email, role, timestamps)
- `work` — Movies/TV shows (title, media type, genres, source)
- `genre` — Genres (name only)
- `work_genre` — Junction table (composite PK)
- `viewing_record` — Individual viewing sessions (user, work, date, rating, review text, version)
- `user_work_state` — Current viewing state per work (viewed, watching, want-to-watch, completed)
- `record_submission` — Idempotency tracker for form submissions (UUID, hash, result record ID)

Key indexing: `(user_id, viewed_on, created_at, id)` for record lists, `(user_id, work_id, ...)` for work history.

### Core Contracts & Rules

**Do not modify without reviewing docs/design.md:**

- **Three save operations**: COMPLETE (record + mark finished), PAST (record only), state-only (no record)
- **Record immutability**: Only owner can modify; date, rating, review text can change; `version` field prevents stale updates
- **Ownership**: Determined from authentication principal, never from user input
- **NULL vs 0**: Rating NULL = unrated (distinct from 0); handle carefully in JSP expressions
- **Statistics**: Period-based aggregations use latest valid rating per work within date range
- **UUID idempotency**: Request UUID issued in form, validated on server; same UUID + same content reuses existing record; same UUID + different content → 409 conflict
- **Deletion independence**: Deleting a record does not affect user_work_state; clearing state does not delete records

### JSP Views

```
src/main/webapp/WEB-INF/views/
├── login.jsp              # Authentication form
├── signup.jsp             # New user registration
├── works/
│   ├── list.jsp           # Search/filter works (includes personal state summary)
│   └── detail.jsp         # Single work with full history, add/edit forms
├── records/
│   ├── list.jsp           # Filtered viewing record list (paginated, sortable)
│   └── detail.jsp         # Single record detail
├── stats/
│   ├── index.jsp          # Period-based statistics (genre breakdown, counts)
│   └── detail.jsp         # Deep dive into a statistic
├── common/
│   ├── filter.jspf        # Reusable filter sidebar (genre, date range, status)
│   ├── applied-filters.jspf — Shows active filters; remove individual ones
│   ├── record-list.jspf   # Viewing record table (caption, th with scope)
│   ├── pagination.jspf    # Paging control (15 items/page)
│   ├── history-summary.jspf — Shows work history overview
│   └── rating-change.jspf — Shows rating direction change
└── error/
    ├── 404.jsp
    ├── 409.jsp            # UUID conflict (duplicate submission)
    └── error.jsp          # Generic error page
```

**Key UI patterns** (from docs/design.md):
- `.button` (primary action) vs `.secondary` (navigation) vs `.danger-button` (delete)
- `.panel`, `.pad`, `.field`, `.form-actions` for form layout
- Long titles wrap in full; never truncate with ellipsis
- Error messages use `role=alert`; status/success use `role=status`
- Mobile layout: same data, card-based arrangement at 320/390px widths
- Form submission uses `data-progress` to suppress duplicate clicks; does not replace server UUID validation

### Static Resources

```
src/main/webapp/resources/
├── css/app.css            # All styling (responsive, dark-theme aware)
└── js/app.js              # Common utilities (form submission, DOM helpers)
```

All CSS in one file; all client-side JS in one file. No external JS frameworks.

## Input & Output Contracts

### Common Input Objects

- **SignupForm** — username, password, name, email
- **WorkForm** — title, media_type (MOVIE/TV), year, description, genres (List<Long> genre IDs), source (MANUAL/TMDB)
- **RecordForm** — viewedOn (date), rating (0.5~5.0, 0.5 steps, or null), text (review), action (COMPLETE/PAST/state-only)
- **QueryFilter** — genres (List), startDate, endDate, sortBy, currentStatus (work state), workId (for stats)

**Validation**: Happens in controller methods. JSP preserves invalid input and highlights error fields. Never discard user input on error.

### Common Output Patterns

- Record lists return Map with keys: `workId`, `viewedOn`, `viewingOrder`, `rating`, `text`, `latestRating`, `previousRating`, `ratingChange`
- Work history summaries return: `currentState`, `recordCount`, `lastViewedOn`, `latestRating`, `latestRatingOn`
- Statistics include: `genreName`, `recordCount`, `workCount`, `repeatedCount`, `averageRating`, `totalRecords` within period
- All DateTime fields use `java.sql.Date` (date only) or `java.time.LocalDateTime` (timestamps)

## Testing

### Unit & Integration Tests

Tests are in `src/test/java/org/mnu/` with parallel `src/test/resources/` for test configuration.

- **Unit tests**: `ReviewSystemIT.java` (26 integration test cases with real MySQL)
  - Tests: saving/updating/deleting records, state transitions, ownership validation, NULL handling
  - Runs against `review_system_test` database only
- **Service tests**: `StatsServiceTest.java` (2 unit tests for aggregation logic)

To run tests manually:

```bash
# From review-system/ directory with dev-shell.cmd active:
mvn test                    # Unit tests only (no DB)
mvn verify                  # Full integration tests (requires MySQL test DB)

# Or from repo root with specific test config:
.\build.cmd -B -ntp verify "-Dreview.config=%USERPROFILE%\dev\review-system\config\test.properties"
```

**Important**: Integration tests automatically clear and re-seed `review_system_test`. Never run them against the main `review_system` database.

### Browser/E2E Tests

Automated tests in `.setup/` directory (Node/Playwright required, not part of Maven):

- `.setup/repov_browser_checks.cjs` — 80 functional test cases (login, search, record CRUD, stats)
- `.setup/handover_checks.cjs` — Filter/pagination/state preservation tests
- `.setup/repov_layout.cjs` — Responsive design checks (1440, 390, 320px)

Results saved to `review-system/docs/evidence/` with JSON and PNG attachments.

## Team Responsibilities & Handover

As of 2026-11-03 (Redmine basis, see project wiki for updates):

- **구서준 (Seo-joon Koo)**: Ratings, reviews, statistics (UC #79-80), common UI, PM. Issues #20, #23, #24, #193-195.
- **고현진 (Hyun-jin Ko)**: Viewing records CRUD, re-watch history, current state. Issues #82, #85, #89, #90.
- **조우성 (Woo-sung Jo)**: Users, authentication, works, queries, cross-review. Issues #13, #14, #17, #22.

**Before modifying code**, read:
1. `docs/design.md` for contracts and DB rules
2. `docs/team-handover.md` for role boundaries and test re-run procedures
3. `docs/requirements.md` for SFR/INR/QUR mapping to implementation

If changing filters (QueryFilter), validate/Mapper bindings, pagination, or JSP display, coordinate with PM on the common filter pattern.

## Development Workflow

### Before Committing

1. **Run tests**: `mvn verify` (or `.\build.cmd -B -ntp verify ...` from root)
2. **Check database**: Ensure `review_system_test` is clean; tests auto-seed
3. **Verify contracts**: Confirm changes don't break save operations, ownership, or NULL handling
4. **Test the UI**: Start server, walk through affected flows in browser

### New Features

- **Do not add**: Saved drafts, social features, recommendation algorithms, SPA rewrite, HTTPS, secure cookies
- **Coordinate before starting**: 
  - New QueryFilter conditions (check with PM)
  - Schema changes (check with data owner)
  - New controller endpoints (impacts common nav)

### Code Style

- Follow existing patterns (no refactoring beyond the change scope)
- Java: Spring annotations over XML config where possible
- MyBatis: Explicit `<resultMap>` with alias names (e.g., `viewedOn`, `viewingOrder`)
- JSP: Use JSTL `<c:*>` tags; preserve user input on error with `c:out value="..."`
- CSS: Mobile-first responsive; test at 320, 390, 1440px

## External Systems & Setup

### Database Installation (New PC)

From repo root, PowerShell (requires Python 3):

```powershell
# Full automated setup:
python .setup/install_tools.py
python .setup/configure_tools.py
python .setup/install_database_tools.py
python .setup/configure_database.py
python .setup/configure_runtime.py
.\build.cmd -B -ntp
.\.setup\configure_workspace.ps1
python .setup/verify_environment.py
```

This installs JDK 15, Maven, Tomcat, and MySQL into `%USERPROFILE%\dev\review-system\`.

### Demo Data Seeding

```powershell
# Seed demo_viewer account with sample viewing records:
python .setup/seed_data.py demo

# Expected October 2026 results:
# - 4 records, 2 works, 2 re-watched, 3 re-watch additions
# - Drama avg rating 4.25 (n=2), SF avg rating 4.50 (n=1)
# - Demo credentials stored in external config/demo-account.properties
```

### Redmine Integration

- **Project**: http://114.70.92.70:3000/projects/e-team-2026
- **Issues/Wiki**: Track requirements, sprints, and design decisions
- **File attachments**: v0.1.1 baseline and evidence uploads

## Key Gotchas

- **Port conflicts**: 8080 (app), 8005 (Tomcat admin), 3306 (MySQL). STS and command-line servers cannot share 8080.
- **DB passwords**: Never in code, never in Git. Always external `config/application.properties`.
- **JDK version mismatch**: `dev-shell.cmd` uses JDK 15; regular terminal may use Java 17. Use `dev-shell.cmd` for Maven builds.
- **User principal source**: Never accept user ID from request params. Always use `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`.
- **NULL rating**: Rating NULL ≠ 0. Use `== null` checks, not `== 0`.
- **Test DB isolation**: `review_system_test` is cleared before each test run. Never run tests against `review_system`.
- **Form submission**: UUID generated client-side in session, validated server-side. 409 conflict if UUID reused with different data.
- **Long text wrapping**: Do not truncate with ellipsis; wrap full text in JSP.

## Documentation

- `README.md` (repo root) — Project overview, running locally, versions
- `개발환경_안내.md` — Development environment setup (in Korean)
- `review-system/README.md` — Running the app, DB config, demo data
- `review-system/docs/design.md` — Database contracts, statistics formula, UI patterns
- `review-system/docs/team-handover.md` — Responsibilities, test re-run steps, inter-team coordination
- `review-system/docs/requirements.md` — Use case to code mapping
- `review-system/docs/verification.md` — Test results, known limitations, TMDB/external features status

## Health Check Endpoint

Navigate to **http://localhost:8080/health** to see:
- Spring version (5.3.39)
- Java version (15.0.2)
- Application status
