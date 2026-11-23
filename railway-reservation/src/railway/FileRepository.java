package railway;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import railway.Models.*;

/** Persists everything in tab-separated text files inside a data directory. */
public class FileRepository implements Repository {
    private final Path dir;
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<Integer, Train> trains = new LinkedHashMap<>();
    private final Map<Integer, Booking> bookings = new LinkedHashMap<>();
    private int nextId = 1;

    public FileRepository(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
            for (String[] f : read("users.tsv")) users.put(f[0], new User(f[0], f[1], f[2], f[3], Role.valueOf(f[4])));
            for (String[] f : read("trains.tsv"))
                trains.put(Integer.parseInt(f[0]), new Train(Integer.parseInt(f[0]), f[1], f[2], f[3], LocalTime.parse(f[4]), LocalTime.parse(f[5]), Integer.parseInt(f[6])));
            for (String[] f : read("bookings.tsv")) {
                Booking b = new Booking(Integer.parseInt(f[0]), f[1], Integer.parseInt(f[2]), LocalDate.parse(f[3]), f[4], Integer.parseInt(f[5]), f[6],
                        Integer.parseInt(f[7]), Status.valueOf(f[8]), LocalDateTime.parse(f[9]), f[10].isEmpty() ? null : LocalDateTime.parse(f[10]));
                bookings.put(b.id(), b);
                nextId = Math.max(nextId, b.id() + 1);
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private static String esc(String s) { return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n"); }
    private static String unesc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) { char n = s.charAt(++i); b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n); }
            else b.append(c);
        }
        return b.toString();
    }
    private List<String[]> read(String file) throws IOException {
        Path p = dir.resolve(file);
        List<String[]> rows = new ArrayList<>();
        if (!Files.exists(p)) return rows;
        for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\t", -1);
            for (int i = 0; i < parts.length; i++) parts[i] = unesc(parts[i]);
            rows.add(parts);
        }
        return rows;
    }
    private <T> void write(String file, Collection<T> items, Function<T, String[]> fields) {
        List<String> lines = new ArrayList<>();
        for (T t : items) {
            String[] f = fields.apply(t);
            for (int i = 0; i < f.length; i++) f[i] = esc(f[i]);
            lines.add(String.join("\t", f));
        }
        try { Files.write(dir.resolve(file), lines, StandardCharsets.UTF_8); } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private void flushUsers() { write("users.tsv", users.values(), u -> new String[]{u.username(), u.passwordHash(), u.fullName(), u.email(), u.role().name()}); }
    private void flushTrains() { write("trains.tsv", trains.values(), t -> new String[]{"" + t.number(), t.name(), t.source(), t.destination(), "" + t.departure(), "" + t.arrival(), "" + t.totalSeats()}); }
    private void flushBookings() {
        write("bookings.tsv", bookings.values(), b -> new String[]{"" + b.id(), b.username(), "" + b.trainNumber(), "" + b.journeyDate(), b.passengerName(),
                "" + b.age(), b.gender(), "" + b.seatNumber(), b.status().name(), "" + b.bookedAt(), b.cancelledAt() == null ? "" : "" + b.cancelledAt()});
    }

    public synchronized Optional<User> findUser(String u) { return Optional.ofNullable(users.get(u)); }
    public synchronized List<User> listUsers() { return new ArrayList<>(users.values()); }
    public synchronized void saveUser(User u) { users.put(u.username(), u); flushUsers(); }
    public synchronized void deleteUser(String u) { users.remove(u); flushUsers(); }
    public synchronized Optional<Train> findTrain(int n) { return Optional.ofNullable(trains.get(n)); }
    public synchronized List<Train> listTrains() { return new ArrayList<>(trains.values()); }
    public synchronized void saveTrain(Train t) { trains.put(t.number(), t); flushTrains(); }
    public synchronized Optional<Booking> findBooking(int id) { return Optional.ofNullable(bookings.get(id)); }
    public synchronized List<Booking> listBookings() { return new ArrayList<>(bookings.values()); }
    public synchronized Booking saveBooking(Booking b) {
        if (b.id() <= 0) b = new Booking(nextId++, b.username(), b.trainNumber(), b.journeyDate(), b.passengerName(), b.age(), b.gender(), b.seatNumber(), b.status(), b.bookedAt(), b.cancelledAt());
        bookings.put(b.id(), b);
        flushBookings();
        return b;
    }
}
