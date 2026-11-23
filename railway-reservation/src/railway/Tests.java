package railway;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import railway.Models.*;
import railway.ReservationService.BookingException;

/** Dependency-free test runner:  java -cp out railway.Tests */
public class Tests {
    static int passed = 0, failed = 0;

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  PASS " + name); } else { failed++; System.out.println("  FAIL " + name); }
    }
    static void throwsMsg(String name, Runnable r, String contains) {
        try { r.run(); check(name + " (expected error)", false); }
        catch (BookingException e) { check(name, e.getMessage().toLowerCase().contains(contains.toLowerCase())); }
    }

    public static void main(String[] a) throws Exception {
        Path dir = Files.createTempDirectory("railway-test");
        ReservationService s = new ReservationService(new FileRepository(dir));
        LocalDate day = LocalDate.now().plusDays(3);

        s.register("alice", "secret1", "Alice A", "alice@mail.com");
        s.register("bob", "secret2", "Bob B", "bob@mail.com");
        throwsMsg("duplicate username rejected", () -> s.register("alice", "secret1", "x", "a@b.co"), "taken");
        throwsMsg("short password rejected", () -> s.register("carl", "123", "x", "a@b.co"), "6 characters");
        throwsMsg("wrong password rejected", () -> s.login("alice", "nope"), "invalid");
        throwsMsg("booking requires login", () -> s.book(12001, day, "A", 20, "F"), "log in");

        s.login("alice", "secret1");
        check("search by route", s.searchTrains("tirana", "vlore").size() == 1);
        check("search by source only", s.searchTrains("Tirana", "").size() == 3);

        Booking b1 = s.book(12001, day, "Alice A", 30, "F");
        check("first booking confirmed with seat 1", b1.status() == Status.CONFIRMED && b1.seatNumber() == 1);
        Booking b2 = s.book(12001, day, "Alice Sister", 28, "f");
        check("second booking gets seat 2 automatically", b2.seatNumber() == 2);
        check("availability reduced", s.availableSeats(12001, day).size() == 38);
        throwsMsg("same passenger twice rejected", () -> s.book(12001, day, "alice a", 30, "F"), "already");
        throwsMsg("past date rejected", () -> s.book(12001, LocalDate.now().minusDays(1), "X", 20, "M"), "past");
        throwsMsg("bad age rejected", () -> s.book(12001, day, "Y", 0, "M"), "age");
        throwsMsg("unknown train rejected", () -> s.book(99999, day, "Y", 20, "M"), "not found");

        // fill a small train completely to test overbooking prevention and the waiting list
        s.logout(); s.login("admin", "Admin@123");
        s.addTrain(new Train(500, "Mini", "A", "B", LocalTime.of(8, 0), LocalTime.of(9, 0), 2));
        throwsMsg("user cannot add trains", () -> { s.logout(); s.login("bob", "secret2"); s.addTrain(new Train(501, "X", "A", "B", LocalTime.NOON, LocalTime.MIDNIGHT, 1)); }, "administrator");
        s.logout(); s.login("bob", "secret2");
        Booking m1 = s.book(500, day, "P1", 20, "M"), m2 = s.book(500, day, "P2", 21, "F");
        Booking w1 = s.book(500, day, "P3", 22, "M"), w2 = s.book(500, day, "P4", 23, "M");
        check("train full -> waiting list", w1.status() == Status.WAITING_LIST && w2.status() == Status.WAITING_LIST && w1.seatNumber() == 0);
        check("no seats left", s.availableSeats(500, day).isEmpty());
        for (int i = 5; i <= 7; i++) { int n = i; s.book(500, day, "P" + n, 20 + n, "O"); }
        throwsMsg("waiting list limit prevents overbooking", () -> s.book(500, day, "P8", 30, "M"), "full");

        // cancellation releases the seat and promotes the first waiting passenger
        s.cancel(m1.id());
        check("cancelled booking marked", s.bookingStatus(m1.id()).status() == Status.CANCELLED);
        Booking promoted = s.bookingStatus(w1.id());
        check("first waiting passenger promoted to released seat", promoted.status() == Status.CONFIRMED && promoted.seatNumber() == m1.seatNumber());
        check("second waiting passenger still waiting", s.bookingStatus(w2.id()).status() == Status.WAITING_LIST);
        throwsMsg("double cancel rejected", () -> s.cancel(m1.id()), "already");
        check("cancellation history has 1 entry", s.myCancellations().size() == 1);
        check("search my bookings by name", s.searchBookings("p2").size() == 1);

        // privacy
        s.logout(); s.login("alice", "secret1");
        throwsMsg("cannot cancel someone else's booking", () -> s.cancel(m2.id()), "own");

        // admin capacity rule
        s.logout(); s.login("admin", "Admin@123");
        throwsMsg("capacity cannot drop below booked seat", () -> s.updateCapacity(12001, 1), "capacity");
        s.updateCapacity(12001, 60);
        check("capacity updated", s.train(12001).totalSeats() == 60);
        s.updateSchedule(12001, LocalTime.of(7, 30), LocalTime.of(8, 40));
        check("schedule updated", s.train(12001).departure().equals(LocalTime.of(7, 30)));
        check("admin sees all bookings", s.allBookings().size() >= 9);

        // persistence: a new service over the same files sees the same data
        ReservationService reloaded = new ReservationService(new FileRepository(dir));
        reloaded.login("alice", "secret1");
        check("data persisted across restart", reloaded.myBookings().size() == 2 && reloaded.availableSeats(12001, day).size() == 58);

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
