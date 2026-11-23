package crypto;

import java.security.*;
import java.security.spec.KeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import crypto.Models.*;

/** Business logic: authentication, market data, watchlist, portfolio and admin operations. */
public class MarketService {
    public static class CryptoException extends RuntimeException { public CryptoException(String m) { super(m); } }
    public static final double START_CASH = 10_000;
    public enum SortKey { MARKET_CAP, PRICE, CHANGE, VOLUME, NAME }
    public record Position(Coin coin, double quantity, double avgCost, double value, double pnl, double pnlPct) {}
    public record Overview(double totalMarketCap, double totalVolume, int coins, Coin topGainer, Coin topLoser, double btcDominance) {}

    private final Repository repo;
    private final Random rnd;
    public MarketService(Repository repo, long seed) { this.repo = repo; this.rnd = new Random(seed); }
    public Repository repo() { return repo; }

    // ---------- seed ----------
    /** Creates the admin account and a starter set of coins if the store is empty. */
    public void seedIfEmpty() {
        if (repo.listUsers().isEmpty()) {
            String salt = newSalt();
            repo.saveUser(new User("admin", "Administrator", "admin@cryptoclone.local", salt, hash("Admin@123", salt), Role.ADMIN, 0));
        }
        if (repo.listCoins().isEmpty()) {
            Object[][] d = {
                {"BTC", "Bitcoin", 67000.0, 19_700_000.0, 28e9, "The first decentralised digital currency, secured by proof of work."},
                {"ETH", "Ethereum", 3400.0, 120_000_000.0, 14e9, "Programmable blockchain that runs smart contracts and dApps."},
                {"BNB", "BNB", 590.0, 150_000_000.0, 1.8e9, "Utility token of the BNB Chain ecosystem."},
                {"SOL", "Solana", 165.0, 440_000_000.0, 2.6e9, "High-throughput blockchain with low transaction fees."},
                {"XRP", "XRP", 0.52, 55_000_000_000.0, 1.4e9, "Digital asset built for fast cross-border payments."},
                {"ADA", "Cardano", 0.45, 35_000_000_000.0, 0.4e9, "Proof-of-stake blockchain developed through peer-reviewed research."},
                {"DOGE", "Dogecoin", 0.16, 144_000_000_000.0, 1.1e9, "Meme-born cryptocurrency with a large community."},
                {"DOT", "Polkadot", 7.2, 1_400_000_000.0, 0.25e9, "Multi-chain network connecting independent blockchains."}};
            for (Object[] x : d) {
                double p = (double) x[2];
                List<Double> h = new ArrayList<>();
                double v = p * 0.9;
                for (int i = 0; i < 30; i++) { v *= 1 + (rnd.nextDouble() - 0.45) * 0.04; h.add(round(v)); }
                double last = h.get(h.size() - 1);
                double scale = p / last;
                h = h.stream().map(a -> round(a * scale)).collect(Collectors.toList());
                repo.saveCoin(new Coin((String) x[0], (String) x[1], p, round2((h.get(29) - h.get(28)) / h.get(28) * 100), p * (double) x[3], (double) x[4], (double) x[3], (String) x[5], h));
            }
        }
    }

    // ---------- auth ----------
    private static String newSalt() { byte[] b = new byte[16]; new SecureRandom().nextBytes(b); return HexFormat.of().formatHex(b); }
    static String hash(String pw, String salt) {
        try {
            KeySpec spec = new PBEKeySpec(pw.toCharArray(), HexFormat.of().parseHex(salt), 65_536, 256);
            return HexFormat.of().formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public User register(String username, String fullName, String email, String password) {
        username = username == null ? "" : username.trim().toLowerCase();
        if (!username.matches("[a-z0-9_]{3,20}")) throw new CryptoException("Username must be 3-20 letters, digits or underscores.");
        if (fullName == null || fullName.isBlank()) throw new CryptoException("Full name is required.");
        if (email == null || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new CryptoException("Enter a valid email address.");
        if (password == null || password.length() < 8) throw new CryptoException("Password must have at least 8 characters.");
        if (repo.findUser(username).isPresent()) throw new CryptoException("Username already taken.");
        String salt = newSalt();
        User u = new User(username, fullName.trim(), email.trim(), salt, hash(password, salt), Role.USER, START_CASH);
        repo.saveUser(u);
        return u;
    }
    public User login(String username, String password) {
        User u = repo.findUser(username == null ? "" : username.trim()).orElseThrow(() -> new CryptoException("Invalid username or password."));
        if (!MessageDigest.isEqual(hash(password == null ? "" : password, u.salt()).getBytes(), u.hash().getBytes())) throw new CryptoException("Invalid username or password.");
        return u;
    }
    public User updateProfile(String username, String fullName, String email) {
        if (fullName == null || fullName.isBlank()) throw new CryptoException("Full name is required.");
        if (email == null || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new CryptoException("Enter a valid email address.");
        User u = repo.findUser(username).orElseThrow().withProfile(fullName.trim(), email.trim());
        repo.saveUser(u); return u;
    }
    public void changePassword(String username, String oldPw, String newPw) {
        User u = login(username, oldPw);
        if (newPw == null || newPw.length() < 8) throw new CryptoException("Password must have at least 8 characters.");
        String s = newSalt(); repo.saveUser(u.withPassword(s, hash(newPw, s)));
    }

    // ---------- market ----------
    public List<Coin> search(String text, String filter, SortKey key, boolean desc) {
        String t = text == null ? "" : text.trim().toLowerCase();
        Comparator<Coin> cmp = switch (key) {
            case PRICE -> Comparator.comparingDouble(Coin::price);
            case CHANGE -> Comparator.comparingDouble(Coin::change24h);
            case VOLUME -> Comparator.comparingDouble(Coin::volume24h);
            case NAME -> Comparator.comparing(c -> c.name().toLowerCase());
            default -> Comparator.comparingDouble(Coin::marketCap);
        };
        if (desc) cmp = cmp.reversed();
        return repo.listCoins().stream()
                .filter(c -> t.isEmpty() || c.name().toLowerCase().contains(t) || c.symbol().toLowerCase().contains(t))
                .filter(c -> filter == null || filter.equals("All") || (filter.equals("Gainers") && c.change24h() > 0) || (filter.equals("Losers") && c.change24h() < 0)
                        || (filter.equals("Under $1") && c.price() < 1) || (filter.equals("Over $1000") && c.price() > 1000))
                .sorted(cmp).toList();
    }
    public Coin coin(String symbol) { return repo.findCoin(symbol).orElseThrow(() -> new CryptoException("Unknown coin: " + symbol)); }
    /** Simulated market tick: bounded random walk for every coin (no network required). */
    public void tick() {
        for (Coin c : repo.listCoins()) {
            double move = (rnd.nextGaussian() * 0.012);
            double np = Math.max(0.0001, c.price() * (1 + Math.max(-0.08, Math.min(0.08, move))));
            List<Double> h = new ArrayList<>(c.history()); h.add(round(np)); while (h.size() > 60) h.remove(0);
            double ref = h.size() > 24 ? h.get(h.size() - 25) : h.get(0);
            repo.saveCoin(c.withMarket(round(np), round2((np - ref) / ref * 100), h));
        }
    }
    public Overview overview() {
        List<Coin> cs = repo.listCoins();
        if (cs.isEmpty()) return new Overview(0, 0, 0, null, null, 0);
        double cap = cs.stream().mapToDouble(Coin::marketCap).sum();
        Coin g = cs.stream().max(Comparator.comparingDouble(Coin::change24h)).get(), l = cs.stream().min(Comparator.comparingDouble(Coin::change24h)).get();
        double btc = cs.stream().filter(c -> c.symbol().equals("BTC")).mapToDouble(Coin::marketCap).sum();
        return new Overview(cap, cs.stream().mapToDouble(Coin::volume24h).sum(), cs.size(), g, l, cap == 0 ? 0 : btc / cap * 100);
    }

    // ---------- watchlist ----------
    public void addWatch(String user, String symbol) { Coin c = coin(symbol); if (!repo.watchlist(user).contains(c.symbol())) repo.addWatch(user, c.symbol()); }
    public void removeWatch(String user, String symbol) { repo.removeWatch(user, symbol.toUpperCase()); }
    public List<Coin> watchlist(String user) { return repo.watchlist(user).stream().map(s -> repo.findCoin(s)).flatMap(Optional::stream).toList(); }

    // ---------- portfolio ----------
    private static String now() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
    public synchronized void buy(String user, String symbol, double qty) {
        if (!(qty > 0)) throw new CryptoException("Quantity must be greater than zero.");
        Coin c = coin(symbol); User u = repo.findUser(user).orElseThrow();
        double cost = qty * c.price();
        if (cost > u.cash() + 1e-9) throw new CryptoException(String.format("Insufficient cash: need $%,.2f, have $%,.2f.", cost, u.cash()));
        Holding old = repo.holdings(user).stream().filter(h -> h.symbol().equals(c.symbol())).findFirst().orElse(new Holding(user, c.symbol(), 0, 0));
        double nq = old.quantity() + qty, avg = (old.quantity() * old.avgCost() + cost) / nq;
        repo.saveUser(u.withCash(u.cash() - cost));
        repo.saveHolding(new Holding(user, c.symbol(), nq, avg));
        repo.addTransaction(new Transaction(0, user, c.symbol(), Side.BUY, qty, c.price(), now()));
    }
    public synchronized void sell(String user, String symbol, double qty) {
        if (!(qty > 0)) throw new CryptoException("Quantity must be greater than zero.");
        Coin c = coin(symbol); User u = repo.findUser(user).orElseThrow();
        Holding h = repo.holdings(user).stream().filter(x -> x.symbol().equals(c.symbol())).findFirst().orElseThrow(() -> new CryptoException("You do not own " + c.symbol() + "."));
        if (qty > h.quantity() + 1e-12) throw new CryptoException(String.format("You only own %.6f %s.", h.quantity(), c.symbol()));
        repo.saveUser(u.withCash(u.cash() + qty * c.price()));
        repo.saveHolding(new Holding(user, c.symbol(), h.quantity() - qty, h.avgCost()));
        repo.addTransaction(new Transaction(0, user, c.symbol(), Side.SELL, qty, c.price(), now()));
    }
    public List<Position> portfolio(String user) {
        List<Position> out = new ArrayList<>();
        for (Holding h : repo.holdings(user)) {
            Optional<Coin> oc = repo.findCoin(h.symbol()); if (oc.isEmpty()) continue;
            Coin c = oc.get(); double val = h.quantity() * c.price(), cost = h.quantity() * h.avgCost();
            out.add(new Position(c, h.quantity(), h.avgCost(), val, val - cost, cost == 0 ? 0 : (val - cost) / cost * 100));
        }
        return out;
    }
    public double portfolioValue(String user) { return portfolio(user).stream().mapToDouble(Position::value).sum(); }
    public double netWorth(String user) { return repo.findUser(user).orElseThrow().cash() + portfolioValue(user); }

    // ---------- admin ----------
    private void requireAdmin(User a) { if (a == null || a.role() != Role.ADMIN) throw new CryptoException("Administrator rights required."); }
    public void addCoin(User admin, String symbol, String name, double price, double supply, double volume, String desc) {
        requireAdmin(admin);
        symbol = symbol == null ? "" : symbol.trim().toUpperCase();
        if (!symbol.matches("[A-Z0-9]{2,10}")) throw new CryptoException("Symbol must be 2-10 letters or digits.");
        if (name == null || name.isBlank()) throw new CryptoException("Name is required.");
        if (!(price > 0) || !(supply > 0)) throw new CryptoException("Price and supply must be positive.");
        if (repo.findCoin(symbol).isPresent()) throw new CryptoException("Coin already listed.");
        repo.saveCoin(new Coin(symbol, name.trim(), price, 0, price * supply, Math.max(0, volume), supply, desc == null ? "" : desc.trim(), new ArrayList<>(List.of(price))));
    }
    public void setPrice(User admin, String symbol, double price) {
        requireAdmin(admin);
        if (!(price > 0)) throw new CryptoException("Price must be positive.");
        Coin c = coin(symbol); List<Double> h = new ArrayList<>(c.history()); h.add(price);
        repo.saveCoin(c.withMarket(price, round2((price - c.price()) / c.price() * 100), h));
    }
    public void delist(User admin, String symbol) { requireAdmin(admin); coin(symbol); repo.deleteCoin(symbol); }

    private static double round(double v) { return Math.round(v * 10000.0) / 10000.0; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
