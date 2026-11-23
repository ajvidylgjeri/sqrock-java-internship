package crypto;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import crypto.Models.*;

/** MySQL implementation using prepared statements (schema in sql/schema.sql). */
public class JdbcRepository implements Repository {
    private final Connection c;
    public JdbcRepository(String url, String user, String pass) {
        try { c = DriverManager.getConnection(url, user, pass); } catch (SQLException e) { throw new IllegalStateException("DB connection failed: " + e.getMessage(), e); }
    }
    private interface Rs<T> { T map(ResultSet r) throws SQLException; }
    private <T> List<T> q(String sql, Rs<T> m, Object... a) {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < a.length; i++) p.setObject(i + 1, a[i]);
            try (ResultSet r = p.executeQuery()) { List<T> out = new ArrayList<>(); while (r.next()) out.add(m.map(r)); return out; }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }
    private void u(String sql, Object... a) {
        try (PreparedStatement p = c.prepareStatement(sql)) { for (int i = 0; i < a.length; i++) p.setObject(i + 1, a[i]); p.executeUpdate(); }
        catch (SQLException e) { throw new IllegalStateException(e); }
    }
    private static User user(ResultSet r) throws SQLException {
        return new User(r.getString("username"), r.getString("full_name"), r.getString("email"), r.getString("salt"), r.getString("hash"), Role.valueOf(r.getString("role")), r.getDouble("cash"));
    }
    private static Coin coin(ResultSet r) throws SQLException {
        String h = r.getString("history");
        List<Double> hist = h == null || h.isBlank() ? new ArrayList<>() : Arrays.stream(h.split(",")).map(Double::valueOf).collect(Collectors.toList());
        return new Coin(r.getString("symbol"), r.getString("name"), r.getDouble("price"), r.getDouble("change24h"), r.getDouble("market_cap"),
                r.getDouble("volume24h"), r.getDouble("supply"), r.getString("description"), hist);
    }
    @Override public Optional<User> findUser(String n) { return q("SELECT * FROM users WHERE username=?", JdbcRepository::user, n.toLowerCase()).stream().findFirst(); }
    @Override public List<User> listUsers() { return q("SELECT * FROM users", JdbcRepository::user); }
    @Override public void saveUser(User x) {
        u("REPLACE INTO users(username,full_name,email,salt,hash,role,cash) VALUES(?,?,?,?,?,?,?)", x.username(), x.fullName(), x.email(), x.salt(), x.hash(), x.role().name(), x.cash());
    }
    @Override public List<Coin> listCoins() { return q("SELECT * FROM coins ORDER BY market_cap DESC", JdbcRepository::coin); }
    @Override public Optional<Coin> findCoin(String s) { return q("SELECT * FROM coins WHERE symbol=?", JdbcRepository::coin, s.toUpperCase()).stream().findFirst(); }
    @Override public void saveCoin(Coin x) {
        u("REPLACE INTO coins(symbol,name,price,change24h,market_cap,volume24h,supply,description,history) VALUES(?,?,?,?,?,?,?,?,?)", x.symbol(), x.name(), x.price(), x.change24h(),
                x.marketCap(), x.volume24h(), x.supply(), x.description(), x.history().stream().map(String::valueOf).collect(Collectors.joining(",")));
    }
    @Override public void deleteCoin(String s) { u("DELETE FROM coins WHERE symbol=?", s.toUpperCase()); }
    @Override public List<String> watchlist(String n) { return q("SELECT symbol FROM watchlist WHERE username=?", r -> r.getString(1), n); }
    @Override public void addWatch(String n, String s) { u("INSERT IGNORE INTO watchlist(username,symbol) VALUES(?,?)", n, s); }
    @Override public void removeWatch(String n, String s) { u("DELETE FROM watchlist WHERE username=? AND symbol=?", n, s); }
    @Override public List<Holding> holdings(String n) {
        return q("SELECT * FROM holdings WHERE username=?", r -> new Holding(r.getString("username"), r.getString("symbol"), r.getDouble("quantity"), r.getDouble("avg_cost")), n);
    }
    @Override public void saveHolding(Holding h) {
        if (h.quantity() <= 1e-12) u("DELETE FROM holdings WHERE username=? AND symbol=?", h.username(), h.symbol());
        else u("REPLACE INTO holdings(username,symbol,quantity,avg_cost) VALUES(?,?,?,?)", h.username(), h.symbol(), h.quantity(), h.avgCost());
    }
    @Override public List<Transaction> transactions(String n) {
        return q("SELECT * FROM transactions WHERE username=? ORDER BY id", r -> new Transaction(r.getInt("id"), r.getString("username"), r.getString("symbol"),
                Side.valueOf(r.getString("side")), r.getDouble("quantity"), r.getDouble("price"), r.getString("tx_time")), n);
    }
    @Override public void addTransaction(Transaction t) {
        u("INSERT INTO transactions(username,symbol,side,quantity,price,tx_time) VALUES(?,?,?,?,?,?)", t.username(), t.symbol(), t.side().name(), t.quantity(), t.price(), t.time());
    }
}
