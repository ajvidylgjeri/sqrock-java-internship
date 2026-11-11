import java.io.*;
import java.util.*;

/**
 * Online Quiz Application — Sqrock IT Solutions Java Internship (Phase 1, Task 2)
 *
 * A console quiz engine demonstrating Core Java, OOP, collections and file handling.
 * Questions load from questions.txt (auto-seeded on first run). Supports category
 * selection, a per-question timer, scoring, a performance summary and a persistent
 * leaderboard (scores.txt).
 *
 * Author: Ajvi Dylgjeri
 */
public class QuizApp {

    static class Question {
        String category, text;
        String[] options;   // 4 options
        int correct;        // 0-based index
        Question(String category, String text, String[] options, int correct) {
            this.category = category; this.text = text; this.options = options; this.correct = correct;
        }
    }

    static final String Q_FILE = "questions.txt";
    static final String SCORE_FILE = "scores.txt";
    static final int TIME_LIMIT_SEC = 20;    // soft time budget per question
    static List<Question> bank = new ArrayList<>();
    static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        seedIfMissing();
        loadQuestions();
        System.out.println("=======================================");
        System.out.println("      SQROCK ONLINE QUIZ APPLICATION");
        System.out.println("=======================================");
        boolean running = true;
        while (running) {
            System.out.println("\n1. Take a quiz");
            System.out.println("2. View leaderboard");
            System.out.println("0. Exit");
            switch (prompt("Choose: ")) {
                case "1" -> takeQuiz();
                case "2" -> leaderboard();
                case "0" -> running = false;
                default -> System.out.println("Invalid option.");
            }
        }
        System.out.println("Goodbye!");
    }

    static void takeQuiz() {
        String name = prompt("Enter your name: ");
        if (name.isBlank()) name = "Guest";

        // Category choice
        List<String> cats = new ArrayList<>();
        for (Question q : bank) if (!cats.contains(q.category)) cats.add(q.category);
        System.out.println("\nCategories:");
        System.out.println("  0. All categories");
        for (int i = 0; i < cats.size(); i++) System.out.println("  " + (i + 1) + ". " + cats.get(i));
        String pick = prompt("Choose category: ");
        List<Question> quiz = new ArrayList<>(bank);
        try {
            int ci = Integer.parseInt(pick);
            if (ci >= 1 && ci <= cats.size()) {
                String c = cats.get(ci - 1);
                quiz.removeIf(q -> !q.category.equals(c));
            }
        } catch (NumberFormatException ignored) {}

        Collections.shuffle(quiz);
        int limit = Math.min(5, quiz.size());
        quiz = quiz.subList(0, limit);

        int score = 0;
        List<String> review = new ArrayList<>();
        System.out.println("\nStarting quiz — " + limit + " questions. (~" + TIME_LIMIT_SEC + "s each)\n");
        for (int i = 0; i < quiz.size(); i++) {
            Question q = quiz.get(i);
            System.out.println("Q" + (i + 1) + ". [" + q.category + "] " + q.text);
            char letter = 'A';
            for (String opt : q.options) System.out.println("   " + (letter++) + ") " + opt);

            long start = System.currentTimeMillis();
            String ans = prompt("Your answer (A-D): ").toUpperCase();
            long elapsed = (System.currentTimeMillis() - start) / 1000;

            int chosen = ans.isEmpty() ? -1 : ans.charAt(0) - 'A';
            boolean timedOut = elapsed > TIME_LIMIT_SEC;
            if (timedOut) {
                System.out.println("   ⏱ Time's up — counted as skipped.");
                review.add("Q" + (i + 1) + ": timed out. Correct: " + (char) ('A' + q.correct));
            } else if (chosen == q.correct) {
                System.out.println("   ✓ Correct!");
                score++;
            } else {
                System.out.println("   ✗ Wrong. Correct answer: " + (char) ('A' + q.correct) + ") " + q.options[q.correct]);
                review.add("Q" + (i + 1) + ": your '" + ans + "' vs correct '" + (char) ('A' + q.correct) + "'");
            }
            System.out.println();
        }

        double pct = quiz.isEmpty() ? 0 : (score * 100.0 / quiz.size());
        System.out.println("=============== RESULT ===============");
        System.out.printf("Score: %d/%d  (%.0f%%)%n", score, quiz.size(), pct);
        System.out.println("Grade: " + grade(pct));
        if (!review.isEmpty()) {
            System.out.println("\nReview:");
            review.forEach(r -> System.out.println("  - " + r));
        }
        saveScore(name, score, quiz.size(), pct);
    }

    static String grade(double pct) {
        if (pct >= 80) return "Excellent 🏆";
        if (pct >= 60) return "Good 👍";
        if (pct >= 40) return "Fair";
        return "Needs improvement";
    }

    static void leaderboard() {
        List<String[]> rows = new ArrayList<>();
        for (String line : readLines(SCORE_FILE)) {
            String[] p = line.split("\\|", -1);
            if (p.length >= 4) rows.add(p);
        }
        if (rows.isEmpty()) { System.out.println("No scores yet."); return; }
        rows.sort((a, b) -> Double.compare(Double.parseDouble(b[3]), Double.parseDouble(a[3])));
        System.out.println("\n=========== LEADERBOARD ===========");
        System.out.printf("%-4s %-16s %-8s %-6s%n", "RANK", "NAME", "SCORE", "PCT");
        int rank = 1;
        for (String[] r : rows) {
            System.out.printf("%-4d %-16s %-8s %-6s%n", rank++, r[0], r[1] + "/" + r[2], r[3] + "%");
            if (rank > 10) break;
        }
    }

    static void saveScore(String name, int score, int total, double pct) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(SCORE_FILE, true))) {
            pw.println(String.join("|", name, String.valueOf(score), String.valueOf(total), String.format("%.0f", pct)));
        } catch (IOException e) { System.out.println("Error saving score: " + e.getMessage()); }
    }

    // ---------- Question bank ----------
    static void loadQuestions() {
        for (String line : readLines(Q_FILE)) {
            String[] p = line.split("\\|", -1);
            if (p.length == 7)
                bank.add(new Question(p[0], p[1], new String[]{p[2], p[3], p[4], p[5]}, Integer.parseInt(p[6])));
        }
    }

    static void seedIfMissing() {
        if (new File(Q_FILE).exists()) return;
        String[] seed = {
            "Java|Which keyword is used to inherit a class in Java?|extends|implements|inherits|super|0",
            "Java|What is the size of an int in Java?|16-bit|32-bit|64-bit|8-bit|1",
            "Java|Which collection does NOT allow duplicate elements?|List|Set|ArrayList|Queue|1",
            "Java|What does JVM stand for?|Java Virtual Machine|Java Visual Machine|Joint Virtual Memory|Java Variable Method|0",
            "General|Who is known as the father of computers?|Alan Turing|Charles Babbage|John von Neumann|Dennis Ritchie|1",
            "General|What does CPU stand for?|Central Process Unit|Central Processing Unit|Computer Personal Unit|Core Processing Unit|1",
            "Web|Which language styles web pages?|HTML|CSS|SQL|JSON|1",
            "Web|HTTP status 404 means?|OK|Server Error|Not Found|Redirect|2",
            "Database|Which command removes all rows but keeps the table?|DROP|DELETE|TRUNCATE|REMOVE|2",
            "Database|SQL stands for?|Structured Query Language|Simple Query Language|Server Query Logic|Sequential Query Language|0"
        };
        try (PrintWriter pw = new PrintWriter(new FileWriter(Q_FILE))) {
            for (String s : seed) pw.println(s);
        } catch (IOException e) { System.out.println("Error seeding questions: " + e.getMessage()); }
    }

    static List<String> readLines(String file) {
        List<String> out = new ArrayList<>();
        File f = new File(file);
        if (!f.exists()) return out;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String l; while ((l = br.readLine()) != null) if (!l.isBlank()) out.add(l);
        } catch (IOException e) { System.out.println("Error reading " + file + ": " + e.getMessage()); }
        return out;
    }

    static String prompt(String msg) { System.out.print(msg); return sc.nextLine().trim(); }
}
