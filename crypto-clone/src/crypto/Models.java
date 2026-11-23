package crypto;

import java.util.List;

/** Plain data types shared by the service, repositories and GUI. */
public final class Models {
    private Models() {}
    public enum Role { USER, ADMIN }
    public enum Side { BUY, SELL }

    public record User(String username, String fullName, String email, String salt, String hash, Role role, double cash) {
        public User withCash(double c) { return new User(username, fullName, email, salt, hash, role, c); }
        public User withProfile(String n, String e) { return new User(username, n, e, salt, hash, role, cash); }
        public User withPassword(String s, String h) { return new User(username, fullName, email, s, h, role, cash); }
    }
    /** price in USD; change24h in percent. */
    public record Coin(String symbol, String name, double price, double change24h, double marketCap, double volume24h,
                       double supply, String description, List<Double> history) {
        public Coin withPrice(double p, double ch, List<Double> h) { return new Coin(symbol, name, p, ch, marketCap, volume24h, supply, description, h); }
        public Coin withMarket(double p, double ch, List<Double> h) {
            return new Coin(symbol, name, p, ch, p * supply, volume24h, supply, description, h);
        }
    }
    public record Holding(String username, String symbol, double quantity, double avgCost) {}
    public record Transaction(int id, String username, String symbol, Side side, double quantity, double price, String time) {}
}
