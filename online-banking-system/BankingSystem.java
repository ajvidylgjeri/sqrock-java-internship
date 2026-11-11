import java.io.*;
import java.util.*;

/**
 * Online Banking System — Sqrock IT Solutions Java Internship (Phase 1, Task 1)
 *
 * A console-based banking application demonstrating Core Java, OOP, collections
 * and file handling. Accounts and transactions are persisted to text files so
 * data survives between runs.
 *
 * Author: Ajvi Dylgjeri
 */
public class BankingSystem {

    // ---------- Domain model ----------
    static class Account {
        String number;      // unique account number
        String name;        // holder name
        String password;    // simple PIN/password
        String type;        // Savings / Current
        double balance;

        Account(String number, String name, String password, String type, double balance) {
            this.number = number; this.name = name; this.password = password;
            this.type = type; this.balance = balance;
        }
        String toLine() { return String.join("|", number, name, password, type, String.valueOf(balance)); }
        static Account fromLine(String line) {
            String[] p = line.split("\\|", -1);
            return new Account(p[0], p[1], p[2], p[3], Double.parseDouble(p[4]));
        }
    }

    // ---------- Storage ----------
    static final String ACC_FILE = "accounts.txt";
    static final String TXN_FILE = "transactions.txt";
    static Map<String, Account> accounts = new LinkedHashMap<>();
    static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        loadAccounts();
        System.out.println("==============================================");
        System.out.println("     WELCOME TO SQROCK ONLINE BANKING");
        System.out.println("==============================================");
        boolean running = true;
        while (running) {
            System.out.println("\nMAIN MENU");
            System.out.println("1. Create account");
            System.out.println("2. Login");
            System.out.println("3. Admin: list all accounts");
            System.out.println("0. Exit");
            switch (prompt("Choose: ")) {
                case "1" -> createAccount();
                case "2" -> login();
                case "3" -> listAccounts();
                case "0" -> { running = false; }
                default -> System.out.println("Invalid option.");
            }
        }
        System.out.println("Thank you for banking with us!");
    }

    // ---------- Account creation ----------
    static void createAccount() {
        String name = prompt("Full name: ");
        if (name.isBlank()) { System.out.println("Name cannot be empty."); return; }
        String type;
        while (true) {
            type = prompt("Account type (1=Savings, 2=Current): ");
            if (type.equals("1")) { type = "Savings"; break; }
            if (type.equals("2")) { type = "Current"; break; }
            System.out.println("Invalid type.");
        }
        String password = prompt("Set a password: ");
        double opening = readAmount("Opening deposit: ");
        String number = generateAccountNumber();
        Account a = new Account(number, name, password, type, opening);
        accounts.put(number, a);
        saveAccounts();
        logTransaction(number, "OPEN", opening, a.balance);
        System.out.println("\n✓ Account created successfully!");
        System.out.println("  Account number: " + number + "  (keep it safe)");
    }

    static String generateAccountNumber() {
        int n = 100001;
        for (String k : accounts.keySet()) {
            try { n = Math.max(n, Integer.parseInt(k) + 1); } catch (NumberFormatException ignored) {}
        }
        return String.valueOf(n);
    }

    // ---------- Login & session ----------
    static void login() {
        String number = prompt("Account number: ");
        Account a = accounts.get(number);
        if (a == null) { System.out.println("No such account."); return; }
        String pw = prompt("Password: ");
        if (!a.password.equals(pw)) { System.out.println("Incorrect password."); return; }
        System.out.println("\nWelcome back, " + a.name + "!");
        session(a);
    }

    static void session(Account a) {
        boolean in = true;
        while (in) {
            System.out.println("\n--- ACCOUNT MENU (" + a.name + ") ---");
            System.out.println("1. Check balance");
            System.out.println("2. Deposit");
            System.out.println("3. Withdraw");
            System.out.println("4. Transfer");
            System.out.println("5. Transaction history");
            System.out.println("6. Account details");
            System.out.println("0. Logout");
            switch (prompt("Choose: ")) {
                case "1" -> System.out.printf("Current balance: %.2f%n", a.balance);
                case "2" -> deposit(a);
                case "3" -> withdraw(a);
                case "4" -> transfer(a);
                case "5" -> history(a.number);
                case "6" -> details(a);
                case "0" -> in = false;
                default -> System.out.println("Invalid option.");
            }
        }
    }

    static void deposit(Account a) {
        double amt = readAmount("Amount to deposit: ");
        if (amt <= 0) { System.out.println("Amount must be positive."); return; }
        a.balance += amt; saveAccounts(); logTransaction(a.number, "DEPOSIT", amt, a.balance);
        System.out.printf("✓ Deposited %.2f. New balance: %.2f%n", amt, a.balance);
    }

    static void withdraw(Account a) {
        double amt = readAmount("Amount to withdraw: ");
        if (amt <= 0) { System.out.println("Amount must be positive."); return; }
        if (amt > a.balance) { System.out.println("Insufficient funds."); return; }
        a.balance -= amt; saveAccounts(); logTransaction(a.number, "WITHDRAW", amt, a.balance);
        System.out.printf("✓ Withdrew %.2f. New balance: %.2f%n", amt, a.balance);
    }

    static void transfer(Account a) {
        String to = prompt("Recipient account number: ");
        Account r = accounts.get(to);
        if (r == null) { System.out.println("Recipient not found."); return; }
        if (r.number.equals(a.number)) { System.out.println("Cannot transfer to self."); return; }
        double amt = readAmount("Amount to transfer: ");
        if (amt <= 0) { System.out.println("Amount must be positive."); return; }
        if (amt > a.balance) { System.out.println("Insufficient funds."); return; }
        a.balance -= amt; r.balance += amt; saveAccounts();
        logTransaction(a.number, "TRANSFER-OUT to " + r.number, amt, a.balance);
        logTransaction(r.number, "TRANSFER-IN from " + a.number, amt, r.balance);
        System.out.printf("✓ Transferred %.2f to %s (%s). New balance: %.2f%n", amt, r.number, r.name, a.balance);
    }

    static void details(Account a) {
        System.out.println("\nAccount number : " + a.number);
        System.out.println("Holder         : " + a.name);
        System.out.println("Type           : " + a.type);
        System.out.printf ("Balance        : %.2f%n", a.balance);
    }

    static void history(String number) {
        System.out.println("\nTransaction history for " + number + ":");
        boolean any = false;
        for (String line : readLines(TXN_FILE)) {
            String[] p = line.split("\\|", -1);
            if (p.length >= 5 && p[0].equals(number)) {
                System.out.printf("  %s | %-22s | amount %.2f | balance %.2f%n",
                        p[1], p[2], Double.parseDouble(p[3]), Double.parseDouble(p[4]));
                any = true;
            }
        }
        if (!any) System.out.println("  (no transactions yet)");
    }

    // ---------- Admin ----------
    static void listAccounts() {
        if (accounts.isEmpty()) { System.out.println("No accounts yet."); return; }
        System.out.printf("%n%-10s %-20s %-10s %12s%n", "NUMBER", "NAME", "TYPE", "BALANCE");
        System.out.println("-------------------------------------------------------");
        for (Account a : accounts.values())
            System.out.printf("%-10s %-20s %-10s %12.2f%n", a.number, a.name, a.type, a.balance);
    }

    // ---------- Persistence ----------
    static void loadAccounts() {
        for (String line : readLines(ACC_FILE))
            if (!line.isBlank()) { Account a = Account.fromLine(line); accounts.put(a.number, a); }
    }
    static void saveAccounts() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(ACC_FILE))) {
            for (Account a : accounts.values()) pw.println(a.toLine());
        } catch (IOException e) { System.out.println("Error saving accounts: " + e.getMessage()); }
    }
    static void logTransaction(String number, String kind, double amount, double balance) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(TXN_FILE, true))) {
            String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            pw.println(String.join("|", number, ts, kind, String.valueOf(amount), String.valueOf(balance)));
        } catch (IOException e) { System.out.println("Error logging transaction: " + e.getMessage()); }
    }
    static List<String> readLines(String file) {
        List<String> out = new ArrayList<>();
        File f = new File(file);
        if (!f.exists()) return out;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String l; while ((l = br.readLine()) != null) out.add(l);
        } catch (IOException e) { System.out.println("Error reading " + file + ": " + e.getMessage()); }
        return out;
    }

    // ---------- Input helpers ----------
    static String prompt(String msg) { System.out.print(msg); return sc.nextLine().trim(); }
    static double readAmount(String msg) {
        while (true) {
            try { return Double.parseDouble(prompt(msg)); }
            catch (NumberFormatException e) { System.out.println("Please enter a valid number."); }
        }
    }
}
