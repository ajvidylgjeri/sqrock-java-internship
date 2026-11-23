package railway;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Plain data classes (records) used across the application. */
public final class Models {
    private Models() {}

    public enum Role { USER, ADMIN }
    public enum Status { CONFIRMED, WAITING_LIST, CANCELLED }

    public record User(String username, String passwordHash, String fullName, String email, Role role) {
        public User withProfile(String fullName, String email) { return new User(username, passwordHash, fullName, email, role); }
    }

    public record Train(int number, String name, String source, String destination,
                        LocalTime departure, LocalTime arrival, int totalSeats) {
        public Train withSchedule(LocalTime dep, LocalTime arr) { return new Train(number, name, source, destination, dep, arr, totalSeats); }
        public Train withSeats(int seats) { return new Train(number, name, source, destination, departure, arrival, seats); }
    }

    public record Booking(int id, String username, int trainNumber, LocalDate journeyDate, String passengerName,
                          int age, String gender, int seatNumber, Status status, LocalDateTime bookedAt,
                          LocalDateTime cancelledAt) {
        public Booking with(int seat, Status st, LocalDateTime cancelled) {
            return new Booking(id, username, trainNumber, journeyDate, passengerName, age, gender, seat, st, bookedAt, cancelled);
        }
    }
}
