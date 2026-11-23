package railway;

import java.util.List;
import java.util.Optional;
import railway.Models.*;

/** Storage abstraction: implemented by FileRepository (zero setup) and JdbcRepository (MySQL). */
public interface Repository {
    Optional<User> findUser(String username);
    List<User> listUsers();
    void saveUser(User u);
    void deleteUser(String username);

    Optional<Train> findTrain(int number);
    List<Train> listTrains();
    void saveTrain(Train t);

    Optional<Booking> findBooking(int id);
    List<Booking> listBookings();
    Booking saveBooking(Booking b);   // id <= 0 means "assign a new id"
}
