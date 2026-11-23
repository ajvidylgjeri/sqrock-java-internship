package railway;

import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import railway.Models.*;
import railway.ReservationService.BookingException;

/** Console user interface. Run with no arguments for the interactive menu. */
public class Main {
    private static final Scanner in = new Scanner(System.in);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static ReservationService svc;

    static Repository createRepository() {
        String url = System.getProperty("db.url");
        if (url != null) {
            System.out.println("Using MySQL database: " + url);
            return new JdbcRepository(url, System.getProperty("db.user", "root"), System.getProperty("db.password", ""));
        }
        return new FileRepository(Path.of(System.getProperty("data.dir", "data")));
    }

    public static void main(String[] args) {
        svc = new ReservationService(createRepository());
        System.out.println("===== RAILWAY RESERVATION SYSTEM =====");
        while (true) {
            try {
                if (svc.currentUser().isEmpty()) { if (!guestMenu()) return; }
                else if (svc.currentUser().get().role() == Role.ADMIN) adminMenu();
                else userMenu();
            } catch (BookingException e) { System.out.println("  [!] " + e.getMessage()); }
            catch (DateTimeException | NumberFormatException e) { System.out.println("  [!] Invalid input format: " + e.getMessage()); }
            catch (NoSuchElementException e) { return; }   // end of input
        }
    }

    static String ask(String label) { System.out.print(label + ": "); return in.nextLine().trim(); }
    static int askInt(String label) { return Integer.parseInt(ask(label)); }

    static boolean guestMenu() {
        System.out.print("\n1. Register\n2. Login\n3. Search Trains\n4. Exit\nChoice: ");
        switch (in.nextLine().trim()) {
            case "1" -> { svc.register(ask("Username"), ask("Password"), ask("Full name"), ask("Email")); System.out.println("Account created. You can log in now."); }
            case "2" -> { User u = svc.login(ask("Username"), ask("Password")); System.out.println("Welcome, " + u.fullName() + "!"); }
            case "3" -> searchTrains();
            case "4" -> { System.out.println("Goodbye!"); return false; }
            default -> System.out.println("  [!] Invalid choice.");
        }
        return true;
    }

    static void userMenu() {
        System.out.print("\n--- " + svc.currentUser().get().fullName() + " ---\n1. Search Trains\n2. View All Trains / Schedules\n3. Check Seat Availability\n4. Book Ticket\n"
                + "5. Cancel Ticket\n6. Booking History\n7. Check Ticket Status\n8. Cancellation History\n9. Search My Bookings\n10. Update Profile\n11. Logout\nChoice: ");
        switch (in.nextLine().trim()) {
            case "1" -> searchTrains();
            case "2" -> svc.allTrains().forEach(Main::printTrain);
            case "3" -> { int n = askInt("Train number"); LocalDate d = LocalDate.parse(ask("Date (yyyy-mm-dd)"));
                          Set<Integer> s = svc.availableSeats(n, d); System.out.println(s.size() + " seat(s) available: " + s); }
            case "4" -> { int n = askInt("Train number"); LocalDate d = LocalDate.parse(ask("Date (yyyy-mm-dd)"));
                          Booking b = svc.book(n, d, ask("Passenger name"), askInt("Age"), ask("Gender (M/F/O)")); printTicket(b); }
            case "5" -> { Booking b = svc.cancel(askInt("Booking id")); System.out.println("Booking #" + b.id() + " cancelled. Seat released."); }
            case "6" -> svc.myBookings().forEach(Main::printBookingLine);
            case "7" -> printTicket(svc.bookingStatus(askInt("Booking id")));
            case "8" -> svc.myCancellations().forEach(Main::printBookingLine);
            case "9" -> svc.searchBookings(ask("Search text (name / train / id / status)")).forEach(Main::printBookingLine);
            case "10" -> { svc.updateProfile(ask("Full name"), ask("Email")); System.out.println("Profile updated."); }
            case "11" -> { svc.logout(); System.out.println("Logged out."); }
            default -> System.out.println("  [!] Invalid choice.");
        }
    }

    static void adminMenu() {
        System.out.print("\n--- ADMIN ---\n1. Add Train\n2. Update Schedule\n3. Update Seat Capacity\n4. View All Bookings\n5. View Users\n6. Remove User\n7. Search Trains\n8. Logout\nChoice: ");
        switch (in.nextLine().trim()) {
            case "1" -> { svc.addTrain(new Train(askInt("Train number"), ask("Name"), ask("Source"), ask("Destination"),
                    LocalTime.parse(ask("Departure (HH:mm)")), LocalTime.parse(ask("Arrival (HH:mm)")), askInt("Seats"))); System.out.println("Train added."); }
            case "2" -> { svc.updateSchedule(askInt("Train number"), LocalTime.parse(ask("Departure (HH:mm)")), LocalTime.parse(ask("Arrival (HH:mm)"))); System.out.println("Schedule updated."); }
            case "3" -> { svc.updateCapacity(askInt("Train number"), askInt("New capacity")); System.out.println("Capacity updated."); }
            case "4" -> svc.allBookings().forEach(Main::printBookingLine);
            case "5" -> svc.allUsers().forEach(u -> System.out.println(u.username() + " | " + u.fullName() + " | " + u.email() + " | " + u.role()));
            case "6" -> { svc.deleteUser(ask("Username")); System.out.println("User removed."); }
            case "7" -> searchTrains();
            case "8" -> { svc.logout(); System.out.println("Logged out."); }
            default -> System.out.println("  [!] Invalid choice.");
        }
    }

    static void searchTrains() {
        List<Train> r = svc.searchTrains(ask("From (blank = any)"), ask("To (blank = any)"));
        if (r.isEmpty()) System.out.println("No trains found.");
        r.forEach(Main::printTrain);
    }
    static void printTrain(Train t) {
        System.out.printf("#%d %-18s %s -> %s  dep %s  arr %s  seats %d%n", t.number(), t.name(), t.source(), t.destination(), t.departure(), t.arrival(), t.totalSeats());
    }
    static void printBookingLine(Booking b) {
        System.out.printf("Booking #%d | train %d | %s | %s (%d, %s) | seat %s | %s%n", b.id(), b.trainNumber(), b.journeyDate(), b.passengerName(),
                b.age(), b.gender(), b.seatNumber() == 0 ? "-" : b.seatNumber(), b.status());
    }
    static void printTicket(Booking b) {
        Train t = svc.train(b.trainNumber());
        System.out.println("\n===== BOOKING " + (b.status() == Status.CONFIRMED ? "CONFIRMATION" : "STATUS") + " =====");
        System.out.println("Booking ID : " + b.id() + "\nPassenger  : " + b.passengerName() + " (" + b.age() + ", " + b.gender() + ")");
        System.out.println("Train      : " + t.number() + " " + t.name() + " (" + t.source() + " -> " + t.destination() + ")");
        System.out.println("Journey    : " + b.journeyDate() + "  dep " + t.departure() + "  arr " + t.arrival());
        System.out.println("Seat       : " + (b.seatNumber() == 0 ? "not allocated" : b.seatNumber()));
        System.out.println("Booked on  : " + b.bookedAt().format(TS) + "\nStatus     : " + b.status());
    }
}
