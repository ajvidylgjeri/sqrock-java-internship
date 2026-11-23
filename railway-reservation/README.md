# Railway Reservation System (Java)

Sqrock IT Solutions - Java Internship, Project Phase 2, Task 4.

## Overview
Console application where passengers register, search trains, check seats, book and cancel tickets and review
their history, while administrators manage trains, schedules, capacity, users and bookings.

## Features
- Registration / login / logout, profile update, PBKDF2-hashed passwords, roles USER / ADMIN
- Train management: view trains and schedules, search by source and destination
- Booking with passenger details (name, age, gender), booking confirmation, **automatic seat allocation** (lowest free seat)
- **Overbooking prevention**: when a train is full passengers go to a waiting list (max 5); beyond that booking is refused
- **Cancellation**: releases the seat and automatically promotes the first waiting-list passenger; cancellation history
- Booking history, ticket status (Confirmed / Waiting List / Cancelled) and search in own bookings
- Admin: add train, update schedule, update seat capacity (cannot drop below a booked seat), view all bookings, manage users

## Java concepts used
OOP and encapsulation, records and enums, interfaces (Repository abstraction with two implementations), collections and streams,
exceptions for validation, java.time, file I/O (java.nio), JDBC with prepared statements, synchronisation of booking operations.

## Database integration
- **Default**: tab-separated files in `data/` (no setup).
- **MySQL**: run `sql/schema.sql`, then start with the JDBC driver on the classpath:
  `java -cp out:mysql-connector-j.jar -Ddb.url=jdbc:mysql://localhost:3306/railway -Ddb.user=root -Ddb.password=... railway.Main`
  (the JDBC repository was written to the same interface but has not been run against a live MySQL server in the build environment).

## Build, test and run
```bash
javac -d out src/railway/*.java
java -cp out railway.Tests      # 29 automated checks, no external libraries
java -cp out railway.Main       # interactive menu; default admin: admin / Admin@123
```
Sample session: `output/sample_session.txt`.
