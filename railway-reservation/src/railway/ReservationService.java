package railway;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import railway.Models.*;

/** All business rules: authentication, train search, booking with seat allocation, cancellation and waiting list. */
public class ReservationService {
    public static class BookingException extends RuntimeException {
        private static final long serialVersionUID = 1L; public BookingException(String m) { super(m); } }

    private final Repository repo;
    private User current;

    public ReservationService(Repository repo) {
        this.repo = repo;
        if (repo.findUser("admin").isEmpty()) repo.saveUser(new User("admin", hash("Admin@123"), "Administrator", "admin@railway.local", Role.ADMIN));
        if (repo.listTrains().isEmpty()) seedTrains();
    }

    private void seedTrains() {
        repo.saveTrain(new Train(12001, "Capital Express", "Tirana", "Durres", LocalTime.of(7, 0), LocalTime.of(8, 10), 40));
        repo.saveTrain(new Train(12002, "Coastal Line", "Durres", "Vlore", LocalTime.of(9, 30), LocalTime.of(12, 0), 30));
        repo.saveTrain(new Train(12003, "Skanderbeg Night", "Tirana", "Shkoder", LocalTime.of(21, 0), LocalTime.of(23, 45), 50));
        repo.saveTrain(new Train(12004, "Southern Star", "Tirana", "Vlore", LocalTime.of(6, 15), LocalTime.of(9, 40), 25));
    }

    // ---------- authentication ----------
    static String hash(String password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            return Base64.getEncoder().encodeToString(salt) + ":" + pbkdf2(password, salt);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String pbkdf2(String pw, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, 65_536, 256);
        return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
    }
    static boolean verify(String password, String stored) {
        try {
            String[] p = stored.split(":");
            return MessageDigestEqual(pbkdf2(password, Base64.getDecoder().decode(p[0])), p[1]);
        } catch (Exception e) { return false; }
    }
    private static boolean MessageDigestEqual(String a, String b) { return java.security.MessageDigest.isEqual(a.getBytes(), b.getBytes()); }

    public User register(String username, String password, String fullName, String email) {
        username = username == null ? "" : username.trim();
        if (!username.matches("[A-Za-z0-9_]{3,30}")) throw new BookingException("Username must be 3-30 letters, digits or underscores.");
        if (password == null || password.length() < 6) throw new BookingException("Password must be at least 6 characters.");
        if (fullName == null || fullName.isBlank()) throw new BookingException("Full name is required.");
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new BookingException("Invalid email address.");
        if (repo.findUser(username).isPresent()) throw new BookingException("Username already taken.");
        User u = new User(username, hash(password), fullName.trim(), email.trim(), Role.USER);
        repo.saveUser(u);
        return u;
    }

    public User login(String username, String password) {
        Optional<User> u = repo.findUser(username == null ? "" : username.trim());
        if (u.isEmpty() || !verify(password == null ? "" : password, u.get().passwordHash())) throw new BookingException("Invalid username or password.");
        current = u.get();
        return current;
    }
    public void logout() { current = null; }
    public Optional<User> currentUser() { return Optional.ofNullable(current); }
    private User requireLogin() { if (current == null) throw new BookingException("Please log in first."); return current; }
    private User requireAdmin() { User u = requireLogin(); if (u.role() != Role.ADMIN) throw new BookingException("Administrator access required."); return u; }

    public void updateProfile(String fullName, String email) {
        User u = requireLogin();
        if (fullName == null || fullName.isBlank() || email == null || !email.contains("@")) throw new BookingException("Invalid profile data.");
        current = u.withProfile(fullName.trim(), email.trim());
        repo.saveUser(current);
    }

    // ---------- trains ----------
    public List<Train> allTrains() { return repo.listTrains(); }
    public Train train(int number) { return repo.findTrain(number).orElseThrow(() -> new BookingException("Train " + number + " not found.")); }

    public List<Train> searchTrains(String source, String destination) {
        return repo.listTrains().stream()
                .filter(t -> source == null || source.isBlank() || t.source().equalsIgnoreCase(source.trim()))
                .filter(t -> destination == null || destination.isBlank() || t.destination().equalsIgnoreCase(destination.trim()))
                .sorted(Comparator.comparing(Train::departure)).collect(Collectors.toList());
    }

    private List<Booking> active(int trainNumber, LocalDate date, Status s) {
        return repo.listBookings().stream().filter(b -> b.trainNumber() == trainNumber && b.journeyDate().equals(date) && b.status() == s).collect(Collectors.toList());
    }

    public Set<Integer> availableSeats(int trainNumber, LocalDate date) {
        Train t = train(trainNumber);
        Set<Integer> free = new TreeSet<>();
        for (int i = 1; i <= t.totalSeats(); i++) free.add(i);
        active(trainNumber, date, Status.CONFIRMED).forEach(b -> free.remove(b.seatNumber()));
        return free;
    }

    // ---------- booking ----------
    public static final int MAX_WAITING = 5;

    public synchronized Booking book(int trainNumber, LocalDate date, String passenger, int age, String gender) {
        User u = requireLogin();
        train(trainNumber);
        if (date == null || date.isBefore(LocalDate.now())) throw new BookingException("Journey date cannot be in the past.");
        if (passenger == null || passenger.isBlank()) throw new BookingException("Passenger name is required.");
        if (age < 1 || age > 120) throw new BookingException("Age must be between 1 and 120.");
        String g = gender == null ? "" : gender.trim().toUpperCase();
        if (!List.of("M", "F", "O").contains(g)) throw new BookingException("Gender must be M, F or O.");
        boolean duplicate = repo.listBookings().stream().anyMatch(b -> b.status() != Status.CANCELLED && b.trainNumber() == trainNumber
                && b.journeyDate().equals(date) && b.passengerName().equalsIgnoreCase(passenger.trim()) && b.username().equals(u.username()));
        if (duplicate) throw new BookingException("This passenger already has a booking on that train and date.");

        Set<Integer> free = availableSeats(trainNumber, date);
        Status st; int seat = 0;
        if (!free.isEmpty()) { st = Status.CONFIRMED; seat = free.iterator().next(); }       // automatic seat allocation
        else if (active(trainNumber, date, Status.WAITING_LIST).size() < MAX_WAITING) st = Status.WAITING_LIST;
        else throw new BookingException("Train is full and the waiting list is closed.");     // prevents overbooking
        return repo.saveBooking(new Booking(0, u.username(), trainNumber, date, passenger.trim(), age, g, seat, st, LocalDateTime.now(), null));
    }

    /** Cancels a booking, frees its seat and promotes the first waiting-list passenger. */
    public synchronized Booking cancel(int bookingId) {
        User u = requireLogin();
        Booking b = repo.findBooking(bookingId).orElseThrow(() -> new BookingException("Booking " + bookingId + " not found."));
        if (!b.username().equals(u.username()) && u.role() != Role.ADMIN) throw new BookingException("You can only cancel your own bookings.");
        if (b.status() == Status.CANCELLED) throw new BookingException("Booking is already cancelled.");
        int freed = b.seatNumber();
        boolean wasConfirmed = b.status() == Status.CONFIRMED;
        Booking cancelled = repo.saveBooking(b.with(0, Status.CANCELLED, LocalDateTime.now()));
        if (wasConfirmed) {
            active(b.trainNumber(), b.journeyDate(), Status.WAITING_LIST).stream().min(Comparator.comparing(Booking::bookedAt).thenComparing(Booking::id))
                    .ifPresent(w -> repo.saveBooking(w.with(freed, Status.CONFIRMED, null)));
        }
        return cancelled;
    }

    // ---------- history ----------
    public List<Booking> myBookings() {
        String name = requireLogin().username();
        return repo.listBookings().stream().filter(b -> b.username().equals(name)).collect(Collectors.toList());
    }
    public List<Booking> myCancellations() { return myBookings().stream().filter(b -> b.status() == Status.CANCELLED).collect(Collectors.toList()); }
    public Booking bookingStatus(int id) {
        User u = requireLogin();
        Booking b = repo.findBooking(id).orElseThrow(() -> new BookingException("Booking " + id + " not found."));
        if (!b.username().equals(u.username()) && u.role() != Role.ADMIN) throw new BookingException("Booking " + id + " not found.");
        return b;
    }
    public List<Booking> searchBookings(String text) {
        String q = text == null ? "" : text.trim().toLowerCase();
        return myBookings().stream().filter(b -> b.passengerName().toLowerCase().contains(q) || String.valueOf(b.trainNumber()).equals(q)
                || String.valueOf(b.id()).equals(q) || b.status().name().toLowerCase().contains(q.replace(' ', '_'))).collect(Collectors.toList());
    }

    // ---------- admin ----------
    public void addTrain(Train t) {
        requireAdmin();
        if (repo.findTrain(t.number()).isPresent()) throw new BookingException("Train number already exists.");
        if (t.totalSeats() < 1) throw new BookingException("Seat capacity must be positive.");
        repo.saveTrain(t);
    }
    public void updateSchedule(int number, LocalTime dep, LocalTime arr) { requireAdmin(); repo.saveTrain(train(number).withSchedule(dep, arr)); }
    public void updateCapacity(int number, int seats) {
        requireAdmin();
        int highestSeat = repo.listBookings().stream().filter(b -> b.trainNumber() == number && b.status() == Status.CONFIRMED).mapToInt(Booking::seatNumber).max().orElse(0);
        if (seats < highestSeat) throw new BookingException("Cannot reduce capacity below seat " + highestSeat + " which is already booked.");
        repo.saveTrain(train(number).withSeats(seats));
    }
    public List<Booking> allBookings() { requireAdmin(); return repo.listBookings(); }
    public List<User> allUsers() { requireAdmin(); return repo.listUsers(); }
    public void deleteUser(String username) {
        requireAdmin();
        if (username.equals("admin")) throw new BookingException("The main administrator cannot be removed.");
        repo.deleteUser(username);
    }
}
