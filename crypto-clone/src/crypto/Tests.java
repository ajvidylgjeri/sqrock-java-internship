package crypto;

import java.nio.file.*;
import java.util.*;
import crypto.Models.*;
import crypto.MarketService.*;

/** Dependency-free test runner: java -cp out crypto.Tests */
public class Tests {
    static int passed = 0, failed = 0;
    static void check(String name, boolean ok) { if (ok) passed++; else { failed++; System.out.println("FAIL: " + name); } }
    static void throwsMsg(String name, Runnable r, String part) {
        try { r.run(); check(name, false); } catch (CryptoException e) { check(name + " [" + e.getMessage() + "]", e.getMessage().contains(part)); }
    }
    static MarketService fresh() throws Exception {
        Path d = Files.createTempDirectory("cryptotest");
        MarketService s = new MarketService(new FileRepository(d), 7); s.seedIfEmpty(); return s;
    }
    public static void main(String[] a) throws Exception {
        MarketService s = fresh();
        check("seeded 8 coins", s.repo().listCoins().size() == 8);
        check("admin exists", s.repo().findUser("admin").isPresent());
        // auth
        User u = s.register("Alice_1", "Alice Doe", "alice@example.com", "Secret123");
        check("username lower-cased", u.username().equals("alice_1"));
        check("starting cash", u.cash() == MarketService.START_CASH);
        check("password not stored in clear", !u.hash().contains("Secret123") && u.hash().length() == 64);
        throwsMsg("duplicate username", () -> s.register("alice_1", "X", "x@y.com", "Secret123"), "taken");
        throwsMsg("short password", () -> s.register("bob", "Bob", "b@y.com", "short"), "8 characters");
        throwsMsg("bad email", () -> s.register("bob", "Bob", "nope", "Secret123"), "valid email");
        throwsMsg("bad username", () -> s.register("a!", "Bob", "b@y.com", "Secret123"), "Username");
        check("login ok", s.login("ALICE_1", "Secret123").username().equals("alice_1"));
        throwsMsg("login wrong pw", () -> s.login("alice_1", "wrong"), "Invalid");
        throwsMsg("login unknown", () -> s.login("ghost", "Secret123"), "Invalid");
        s.updateProfile("alice_1", "Alice D.", "a2@example.com");
        check("profile updated", s.repo().findUser("alice_1").get().fullName().equals("Alice D."));
        s.changePassword("alice_1", "Secret123", "NewSecret99");
        check("password changed", s.login("alice_1", "NewSecret99") != null);
        throwsMsg("old password rejected", () -> s.login("alice_1", "Secret123"), "Invalid");
        // search / filter / sort
        check("search by name", s.search("bit", "All", MarketService.SortKey.MARKET_CAP, true).size() == 1);
        check("search by symbol", s.search("eth", "All", MarketService.SortKey.MARKET_CAP, true).get(0).symbol().equals("ETH"));
        check("sorted by market cap desc", s.search("", "All", MarketService.SortKey.MARKET_CAP, true).get(0).symbol().equals("BTC"));
        check("sorted by price asc", s.search("", "All", MarketService.SortKey.PRICE, false).get(0).price() < 0.2);
        check("filter under $1", s.search("", "Under $1", MarketService.SortKey.PRICE, false).stream().allMatch(c -> c.price() < 1));
        check("filter over $1000", s.search("", "Over $1000", MarketService.SortKey.PRICE, false).size() == 2);
        check("no results", s.search("zzz", "All", MarketService.SortKey.NAME, false).isEmpty());
        // watchlist
        s.addWatch("alice_1", "btc"); s.addWatch("alice_1", "SOL"); s.addWatch("alice_1", "BTC");
        check("watchlist no duplicates", s.watchlist("alice_1").size() == 2);
        s.removeWatch("alice_1", "BTC");
        check("watch removed", s.watchlist("alice_1").size() == 1 && s.watchlist("alice_1").get(0).symbol().equals("SOL"));
        throwsMsg("watch unknown coin", () -> s.addWatch("alice_1", "NOPE"), "Unknown");
        // portfolio
        s.buy("alice_1", "ETH", 1);
        double eth = s.coin("ETH").price();
        check("cash reduced", Math.abs(s.repo().findUser("alice_1").get().cash() - (10_000 - eth)) < 1e-6);
        s.buy("alice_1", "ETH", 1);
        check("quantity accumulates", s.portfolio("alice_1").get(0).quantity() == 2);
        throwsMsg("insufficient cash", () -> s.buy("alice_1", "BTC", 5), "Insufficient");
        throwsMsg("zero quantity", () -> s.buy("alice_1", "ETH", 0), "greater than zero");
        throwsMsg("sell more than owned", () -> s.sell("alice_1", "ETH", 3), "only own");
        throwsMsg("sell not owned", () -> s.sell("alice_1", "ADA", 1), "do not own");
        s.setPrice(s.repo().findUser("admin").get(), "ETH", eth * 1.1);
        Position p = s.portfolio("alice_1").get(0);
        check("profit computed", Math.abs(p.pnl() - 2 * eth * 0.1) < 1e-6 && Math.abs(p.pnlPct() - 10) < 1e-6);
        s.sell("alice_1", "ETH", 2);
        check("position closed", s.portfolio("alice_1").isEmpty());
        check("net worth = cash after closing", Math.abs(s.netWorth("alice_1") - s.repo().findUser("alice_1").get().cash()) < 1e-9);
        check("cash increased by profit", s.repo().findUser("alice_1").get().cash() > 10_000);
        check("transactions logged", s.repo().transactions("alice_1").size() == 3);
        check("transaction ids unique", s.repo().transactions("alice_1").stream().map(Transaction::id).distinct().count() == 3);
        // market
        Coin before = s.coin("BTC"); s.tick(); Coin after = s.coin("BTC");
        check("tick changes price", before.price() != after.price());
        check("tick extends history", after.history().size() == before.history().size() + 1);
        for (int i = 0; i < 100; i++) s.tick();
        check("history capped at 60", s.coin("BTC").history().size() <= 60);
        check("prices stay positive", s.repo().listCoins().stream().allMatch(c -> c.price() > 0));
        Overview o = s.overview();
        check("overview totals", o.coins() == 8 && o.totalMarketCap() > 0 && o.btcDominance() > 0 && o.btcDominance() < 100);
        check("top gainer >= top loser", o.topGainer().change24h() >= o.topLoser().change24h());
        // admin
        User admin = s.repo().findUser("admin").get(), alice = s.repo().findUser("alice_1").get();
        s.addCoin(admin, "xyz", "XYZ Coin", 2.5, 1_000_000, 5000, "Test coin");
        check("coin added", s.coin("XYZ").name().equals("XYZ Coin"));
        throwsMsg("duplicate coin", () -> s.addCoin(admin, "XYZ", "Again", 1, 1, 1, ""), "already");
        throwsMsg("user cannot add coin", () -> s.addCoin(alice, "ABC", "Abc", 1, 1, 1, ""), "Administrator");
        throwsMsg("user cannot delist", () -> s.delist(alice, "XYZ"), "Administrator");
        throwsMsg("bad price", () -> s.setPrice(admin, "XYZ", -1), "positive");
        s.addWatch("alice_1", "XYZ"); s.delist(admin, "XYZ");
        check("delisted", s.repo().findCoin("XYZ").isEmpty());
        check("delisting clears watchlists", s.watchlist("alice_1").stream().noneMatch(c -> c.symbol().equals("XYZ")));
        // persistence
        Path d = Files.createTempDirectory("cryptopersist");
        MarketService s1 = new MarketService(new FileRepository(d), 1); s1.seedIfEmpty();
        s1.register("carol", "Carol", "c@x.io", "Password1"); s1.buy("carol", "SOL", 3); s1.addWatch("carol", "DOT");
        MarketService s2 = new MarketService(new FileRepository(d), 2);
        check("persist user", s2.login("carol", "Password1").cash() < 10_000);
        check("persist holding", s2.portfolio("carol").get(0).quantity() == 3);
        check("persist watchlist", s2.watchlist("carol").size() == 1);
        check("persist history", s2.coin("BTC").history().size() == 30);
        System.out.printf("%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }
}
