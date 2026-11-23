# CryptoClone — Cryptocurrency Tracker (Java Swing)

Sqrock IT Solutions - Java Internship, Project Phase 2, Task 2.

Desktop application that replicates the core of a cryptocurrency platform: market listings, coin detail pages with price charts,
search / filter / sort, watchlists, a virtual-cash portfolio with profit/loss, a market overview dashboard and an admin panel.
Written in core Java (JDK only), Swing GUI, JDBC for MySQL. Prices are **simulated** (bounded random walk, "Live prices" switch) so the app runs offline.

## Features
| Requirement | Implementation |
|---|---|
| User authentication | Register / login / logout, PBKDF2-SHA256 salted passwords, profile update, change password, roles USER / ADMIN |
| Cryptocurrency listings | Market tab: price, 24h change, market cap, volume for 8 seeded coins |
| Coin details page | Double-click a coin: description, supply, 30-day line chart, Buy / Sell / add to watchlist |
| Search & filter | Search by name or symbol; filters All / Gainers / Losers / Under $1 / Over $1000; sort by market cap, price, 24h change, volume, name |
| Watchlist | Add / remove coins, live values |
| Portfolio tracking | Buy and sell with $10,000 virtual cash, average cost, value, P/L and P/L %, net worth, transaction history |
| Market overview | Total market cap, 24h volume, BTC dominance, your net worth, top movers, 24h change bar chart |
| Admin management | List a new coin, set price, delist a coin, view registered users (admin only, enforced in the service layer) |

## Java concepts used
OOP and encapsulation, records / enums / sealed-style data types, interface-based design (`Repository` with `FileRepository` and `JdbcRepository`),
collections and streams, custom exceptions for validation, Swing (`JTabbedPane`, `JTable`, custom painting with Graphics2D, `javax.swing.Timer`),
JDBC with prepared statements, `java.security` / PBKDF2 for passwords, file I/O (`java.nio`), synchronised trade operations.

## Database integration
- **Default**: tab-separated files in `data/` — no setup required.
- **MySQL**: run `sql/schema.sql`, then start with the JDBC driver on the classpath:
  `java -cp out:mysql-connector-j.jar -Ddb.url=jdbc:mysql://localhost:3306/cryptoclone -Ddb.user=root -Ddb.password=... crypto.App`
  (`JdbcRepository` implements the same interface; it was written against the schema but **has not been run against a live MySQL server** in the build environment.)

## Build, test, run
```bash
javac -d out src/crypto/*.java
java -cp out crypto.Tests          # 54 automated checks, no external libraries
java -cp out crypto.App            # GUI.  Demo admin: admin / Admin@123
xvfb-run java -cp out crypto.Shots screenshots   # regenerate screenshots on a headless machine
```
Test results: `output/test_results.txt`. Screenshots: `screenshots/`.

## Project structure
```
src/crypto/Models.java          records: User, Coin, Holding, Transaction
src/crypto/Repository.java      storage interface
src/crypto/FileRepository.java  flat-file storage     src/crypto/JdbcRepository.java  MySQL storage
src/crypto/MarketService.java   auth, market, watchlist, portfolio, admin logic
src/crypto/App.java             Swing GUI             src/crypto/Tests.java / Shots.java
sql/schema.sql                  MySQL schema
```

## Notes
Educational project: no real market data, no real money, no Spring Boot / REST layer (optional in the brief).
