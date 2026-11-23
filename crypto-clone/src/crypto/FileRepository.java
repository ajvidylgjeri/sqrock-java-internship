package crypto;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import crypto.Models.*;

/** Tab-separated flat-file storage. Every mutation rewrites the affected file. */
public class FileRepository implements Repository {
    private final Path dir;
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Coin> coins = new LinkedHashMap<>();
    private final Set<String> watch = new LinkedHashSet<>();           // "user\tsymbol"
    private final Map<String, Holding> holdings = new LinkedHashMap<>(); // "user\tsymbol"
    private final List<Transaction> txs = new ArrayList<>();

    public FileRepository(Path dir) {
        this.dir = dir;
        try { Files.createDirectories(dir); } catch (IOException e) { throw new IllegalStateException(e); }
        load();
    }
    private static String enc(String s) { return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n"); }
    private static String dec(String s) { return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\"); }
    private List<String[]> read(String f) {
        Path p = dir.resolve(f);
        if (!Files.exists(p)) return List.of();
        try {
            return Files.readAllLines(p).stream().filter(l -> !l.isBlank()).map(l -> l.split("\t", -1)).collect(Collectors.toList());
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    private void write(String f, List<String> lines) {
        try { Files.write(dir.resolve(f), lines); } catch (IOException e) { throw new IllegalStateException(e); }
    }
    private void load() {
        for (String[] c : read("users.tsv")) users.put(c[0], new User(c[0], dec(c[1]), dec(c[2]), c[3], c[4], Role.valueOf(c[5]), Double.parseDouble(c[6])));
        for (String[] c : read("coins.tsv")) {
            List<Double> h = c[8].isBlank() ? new ArrayList<>() : Arrays.stream(c[8].split(",")).map(Double::valueOf).collect(Collectors.toList());
            coins.put(c[0], new Coin(c[0], dec(c[1]), Double.parseDouble(c[2]), Double.parseDouble(c[3]), Double.parseDouble(c[4]),
                    Double.parseDouble(c[5]), Double.parseDouble(c[6]), dec(c[7]), h));
        }
        for (String[] c : read("watchlist.tsv")) watch.add(c[0] + "\t" + c[1]);
        for (String[] c : read("holdings.tsv")) holdings.put(c[0] + "\t" + c[1], new Holding(c[0], c[1], Double.parseDouble(c[2]), Double.parseDouble(c[3])));
        for (String[] c : read("transactions.tsv")) txs.add(new Transaction(Integer.parseInt(c[0]), c[1], c[2], Side.valueOf(c[3]),
                Double.parseDouble(c[4]), Double.parseDouble(c[5]), c[6]));
    }
    public boolean isEmpty() { return users.isEmpty() && coins.isEmpty(); }

    @Override public synchronized Optional<User> findUser(String u) { return Optional.ofNullable(users.get(u.toLowerCase())); }
    @Override public synchronized List<User> listUsers() { return new ArrayList<>(users.values()); }
    @Override public synchronized void saveUser(User u) {
        users.put(u.username(), u);
        write("users.tsv", users.values().stream().map(x -> String.join("\t", x.username(), enc(x.fullName()), enc(x.email()), x.salt(), x.hash(), x.role().name(), Double.toString(x.cash()))).toList());
    }
    @Override public synchronized List<Coin> listCoins() { return new ArrayList<>(coins.values()); }
    @Override public synchronized Optional<Coin> findCoin(String s) { return Optional.ofNullable(coins.get(s.toUpperCase())); }
    private void flushCoins() {
        write("coins.tsv", coins.values().stream().map(c -> String.join("\t", c.symbol(), enc(c.name()), Double.toString(c.price()), Double.toString(c.change24h()),
                Double.toString(c.marketCap()), Double.toString(c.volume24h()), Double.toString(c.supply()), enc(c.description()),
                c.history().stream().map(String::valueOf).collect(Collectors.joining(",")))).toList());
    }
    @Override public synchronized void saveCoin(Coin c) { coins.put(c.symbol(), c); flushCoins(); }
    @Override public synchronized void deleteCoin(String s) {
        coins.remove(s.toUpperCase()); flushCoins();
        watch.removeIf(w -> w.endsWith("\t" + s.toUpperCase())); flushWatch();
    }
    private void flushWatch() { write("watchlist.tsv", new ArrayList<>(watch)); }
    @Override public synchronized List<String> watchlist(String u) {
        return watch.stream().filter(w -> w.startsWith(u + "\t")).map(w -> w.substring(u.length() + 1)).toList();
    }
    @Override public synchronized void addWatch(String u, String s) { watch.add(u + "\t" + s); flushWatch(); }
    @Override public synchronized void removeWatch(String u, String s) { watch.remove(u + "\t" + s); flushWatch(); }
    @Override public synchronized List<Holding> holdings(String u) { return holdings.values().stream().filter(h -> h.username().equals(u)).toList(); }
    @Override public synchronized void saveHolding(Holding h) {
        if (h.quantity() <= 1e-12) holdings.remove(h.username() + "\t" + h.symbol()); else holdings.put(h.username() + "\t" + h.symbol(), h);
        write("holdings.tsv", holdings.values().stream().map(x -> String.join("\t", x.username(), x.symbol(), Double.toString(x.quantity()), Double.toString(x.avgCost()))).toList());
    }
    @Override public synchronized List<Transaction> transactions(String u) { return txs.stream().filter(t -> t.username().equals(u)).toList(); }
    @Override public synchronized void addTransaction(Transaction t) {
        if (t.id() <= 0) t = new Transaction(txs.stream().mapToInt(Transaction::id).max().orElse(0) + 1, t.username(), t.symbol(), t.side(), t.quantity(), t.price(), t.time());
        txs.add(t);
        write("transactions.tsv", txs.stream().map(x -> String.join("\t", Integer.toString(x.id()), x.username(), x.symbol(), x.side().name(), Double.toString(x.quantity()), Double.toString(x.price()), x.time())).toList());
    }
}
