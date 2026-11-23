-- MySQL schema for the Cryptocurrency Clone
CREATE DATABASE IF NOT EXISTS cryptoclone;
USE cryptoclone;

CREATE TABLE IF NOT EXISTS users (
  username  VARCHAR(40) PRIMARY KEY,
  full_name VARCHAR(100) NOT NULL,
  email     VARCHAR(120) NOT NULL,
  salt      VARCHAR(64)  NOT NULL,
  hash      VARCHAR(128) NOT NULL,
  role      ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
  cash      DOUBLE NOT NULL DEFAULT 10000
);
CREATE TABLE IF NOT EXISTS coins (
  symbol      VARCHAR(10) PRIMARY KEY,
  name        VARCHAR(60) NOT NULL,
  price       DOUBLE NOT NULL,
  change24h   DOUBLE NOT NULL DEFAULT 0,
  market_cap  DOUBLE NOT NULL DEFAULT 0,
  volume24h   DOUBLE NOT NULL DEFAULT 0,
  supply      DOUBLE NOT NULL DEFAULT 0,
  description TEXT,
  history     TEXT
);
CREATE TABLE IF NOT EXISTS watchlist (
  username VARCHAR(40) NOT NULL,
  symbol   VARCHAR(10) NOT NULL,
  PRIMARY KEY (username, symbol),
  FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE,
  FOREIGN KEY (symbol)   REFERENCES coins(symbol)   ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS holdings (
  username VARCHAR(40) NOT NULL,
  symbol   VARCHAR(10) NOT NULL,
  quantity DOUBLE NOT NULL,
  avg_cost DOUBLE NOT NULL,
  PRIMARY KEY (username, symbol),
  FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS transactions (
  id       INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(40) NOT NULL,
  symbol   VARCHAR(10) NOT NULL,
  side     ENUM('BUY','SELL') NOT NULL,
  quantity DOUBLE NOT NULL,
  price    DOUBLE NOT NULL,
  tx_time  VARCHAR(30) NOT NULL,
  FOREIGN KEY (username) REFERENCES users(username) ON DELETE CASCADE
);
-- Default administrator is created by the application on first start (admin / Admin@123).
