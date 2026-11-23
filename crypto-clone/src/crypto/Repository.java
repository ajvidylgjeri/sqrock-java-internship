package crypto;

import java.util.List;
import java.util.Optional;
import crypto.Models.*;

/** Storage abstraction: FileRepository (no setup) and JdbcRepository (MySQL). */
public interface Repository {
    Optional<User> findUser(String username);
    List<User> listUsers();
    void saveUser(User u);

    List<Coin> listCoins();
    Optional<Coin> findCoin(String symbol);
    void saveCoin(Coin c);
    void deleteCoin(String symbol);

    List<String> watchlist(String username);
    void addWatch(String username, String symbol);
    void removeWatch(String username, String symbol);

    List<Holding> holdings(String username);
    void saveHolding(Holding h);          // quantity <= 0 deletes the row
    List<Transaction> transactions(String username);
    void addTransaction(Transaction t);   // id <= 0 means "assign a new id"
}
