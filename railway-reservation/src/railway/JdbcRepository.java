package railway;

import java.sql.*;
import java.time.*;
import java.util.*;
import railway.Models.*;

/**
 * MySQL implementation (see sql/schema.sql). Requires the MySQL Connector/J jar on the classpath.
 * Enable with:  java -cp out:mysql-connector-j.jar -Ddb.url=jdbc:mysql://localhost:3306/railway -Ddb.user=root -Ddb.password=... railway.Main
 */
public class JdbcRepository implements Repository {
    private final String url, user, password;

    public JdbcRepository(String url, String user, String password) {
        this.url = url; this.user = user; this.password = password;
        try (Connection c = conn()) { /* fail fast if the database is unreachable */ }
        catch (SQLException e) { throw new IllegalStateException("Cannot connect to database: " + e.getMessage(), e); }
    }
    private Connection conn() throws SQLException { return DriverManager.getConnection(url, user, password); }

    private interface Mapper<T> { T map(ResultSet rs) throws SQLException; }
    private <T> List<T> query(String sql, Mapper<T> m, Object... args) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) { List<T> out = new ArrayList<>(); while (rs.next()) out.add(m.map(rs)); return out; }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }
    private int update(String sql, Object... args) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { return k.next() ? k.getInt(1) : 0; }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    private static User user(ResultSet r) throws SQLException {
        return new User(r.getString("username"), r.getString("password_hash"), r.getString("full_name"), r.getString("email"), Role.valueOf(r.getString("role")));
    }
    private static Train train(ResultSet r) throws SQLException {
        return new Train(r.getInt("number"), r.getString("name"), r.getString("source"), r.getString("destination"),
                r.getTime("departure").toLocalTime(), r.getTime("arrival").toLocalTime(), r.getInt("total_seats"));
    }
    private static Booking booking(ResultSet r) throws SQLException {
        Timestamp c = r.getTimestamp("cancelled_at");
        return new Booking(r.getInt("id"), r.getString("username"), r.getInt("train_number"), r.getDate("journey_date").toLocalDate(),
                r.getString("passenger_name"), r.getInt("age"), r.getString("gender"), r.getInt("seat_number"),
                Status.valueOf(r.getString("status")), r.getTimestamp("booked_at").toLocalDateTime(), c == null ? null : c.toLocalDateTime());
    }

    public Optional<User> findUser(String u) { return query("SELECT * FROM users WHERE username=?", JdbcRepository::user, u).stream().findFirst(); }
    public List<User> listUsers() { return query("SELECT * FROM users ORDER BY username", JdbcRepository::user); }
    public void saveUser(User u) {
        update("INSERT INTO users(username,password_hash,full_name,email,role) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash),full_name=VALUES(full_name),email=VALUES(email),role=VALUES(role)",
                u.username(), u.passwordHash(), u.fullName(), u.email(), u.role().name());
    }
    public void deleteUser(String u) { update("DELETE FROM users WHERE username=?", u); }
    public Optional<Train> findTrain(int n) { return query("SELECT * FROM trains WHERE number=?", JdbcRepository::train, n).stream().findFirst(); }
    public List<Train> listTrains() { return query("SELECT * FROM trains ORDER BY number", JdbcRepository::train); }
    public void saveTrain(Train t) {
        update("INSERT INTO trains(number,name,source,destination,departure,arrival,total_seats) VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),source=VALUES(source),destination=VALUES(destination),departure=VALUES(departure),arrival=VALUES(arrival),total_seats=VALUES(total_seats)",
                t.number(), t.name(), t.source(), t.destination(), Time.valueOf(t.departure()), Time.valueOf(t.arrival()), t.totalSeats());
    }
    public Optional<Booking> findBooking(int id) { return query("SELECT * FROM bookings WHERE id=?", JdbcRepository::booking, id).stream().findFirst(); }
    public List<Booking> listBookings() { return query("SELECT * FROM bookings ORDER BY id", JdbcRepository::booking); }
    public Booking saveBooking(Booking b) {
        Timestamp cancelled = b.cancelledAt() == null ? null : Timestamp.valueOf(b.cancelledAt());
        if (b.id() <= 0) {
            int id = update("INSERT INTO bookings(username,train_number,journey_date,passenger_name,age,gender,seat_number,status,booked_at,cancelled_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    b.username(), b.trainNumber(), java.sql.Date.valueOf(b.journeyDate()), b.passengerName(), b.age(), b.gender(), b.seatNumber(), b.status().name(), Timestamp.valueOf(b.bookedAt()), cancelled);
            return new Booking(id, b.username(), b.trainNumber(), b.journeyDate(), b.passengerName(), b.age(), b.gender(), b.seatNumber(), b.status(), b.bookedAt(), b.cancelledAt());
        }
        update("UPDATE bookings SET seat_number=?, status=?, cancelled_at=? WHERE id=?", b.seatNumber(), b.status().name(), cancelled, b.id());
        return b;
    }
}
