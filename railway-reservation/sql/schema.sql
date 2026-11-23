-- Railway Reservation System - MySQL schema and sample data
CREATE DATABASE IF NOT EXISTS railway;
USE railway;

CREATE TABLE IF NOT EXISTS users (
    username      VARCHAR(50) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(120) NOT NULL,
    role          ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER'
);

CREATE TABLE IF NOT EXISTS trains (
    number      INT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    source      VARCHAR(80)  NOT NULL,
    destination VARCHAR(80)  NOT NULL,
    departure   TIME NOT NULL,
    arrival     TIME NOT NULL,
    total_seats INT  NOT NULL CHECK (total_seats > 0)
);

CREATE TABLE IF NOT EXISTS bookings (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(50) NOT NULL,
    train_number   INT NOT NULL,
    journey_date   DATE NOT NULL,
    passenger_name VARCHAR(100) NOT NULL,
    age            INT NOT NULL,
    gender         VARCHAR(10) NOT NULL,
    seat_number    INT NOT NULL DEFAULT 0,          -- 0 while on the waiting list / after cancellation
    status         ENUM('CONFIRMED','WAITING_LIST','CANCELLED') NOT NULL,
    booked_at      DATETIME NOT NULL,
    cancelled_at   DATETIME NULL,
    FOREIGN KEY (username)     REFERENCES users(username) ON DELETE CASCADE,
    FOREIGN KEY (train_number) REFERENCES trains(number),
    INDEX idx_train_date (train_number, journey_date, status)
);

INSERT IGNORE INTO trains VALUES
 (12001, 'Capital Express',  'Tirana',     'Durres',     '07:00', '08:10', 40),
 (12002, 'Coastal Line',     'Durres',     'Vlore',      '09:30', '12:00', 30),
 (12003, 'Skanderbeg Night', 'Tirana',     'Shkoder',    '21:00', '23:45', 50),
 (12004, 'Southern Star',    'Tirana',     'Vlore',      '06:15', '09:40', 25);
-- The first administrator is created by the application on first start (admin / Admin@123).
