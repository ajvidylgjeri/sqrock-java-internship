import java.io.*;
import java.util.*;

/**
 * Job Portal System — Sqrock IT Solutions Java Internship (Phase 1, Task 3)
 *
 * A console job portal demonstrating Core Java, OOP, role-based access,
 * collections and file handling. Employers post jobs; candidates search and
 * apply. Jobs and applications persist to text files.
 *
 * Author: Ajvi Dylgjeri
 */
public class JobPortal {

    static class Job {
        int id; String title, company, location, type; double salary; String skills; String employer;
        Job(int id, String title, String company, String location, String type,
            double salary, String skills, String employer) {
            this.id = id; this.title = title; this.company = company; this.location = location;
            this.type = type; this.salary = salary; this.skills = skills; this.employer = employer;
        }
        String toLine() {
            return String.join("|", String.valueOf(id), title, company, location, type,
                    String.valueOf(salary), skills, employer);
        }
        static Job fromLine(String l) {
            String[] p = l.split("\\|", -1);
            return new Job(Integer.parseInt(p[0]), p[1], p[2], p[3], p[4], Double.parseDouble(p[5]), p[6], p[7]);
        }
    }

    static final String JOB_FILE = "jobs.txt";
    static final String APP_FILE = "applications.txt";
    static List<Job> jobs = new ArrayList<>();
    static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        seedIfMissing();
        loadJobs();
        System.out.println("=====================================");
        System.out.println("        SQROCK JOB PORTAL SYSTEM");
        System.out.println("=====================================");
        boolean running = true;
        while (running) {
            System.out.println("\nWELCOME — choose your role:");
            System.out.println("1. Candidate");
            System.out.println("2. Employer");
            System.out.println("0. Exit");
            switch (prompt("Choose: ")) {
                case "1" -> candidateMenu();
                case "2" -> employerMenu();
                case "0" -> running = false;
                default -> System.out.println("Invalid option.");
            }
        }
        System.out.println("Thank you for using Sqrock Job Portal!");
    }

    // ---------- Candidate ----------
    static void candidateMenu() {
        String name = prompt("Your name: ");
        if (name.isBlank()) name = "Candidate";
        boolean in = true;
        while (in) {
            System.out.println("\n--- CANDIDATE (" + name + ") ---");
            System.out.println("1. View all jobs");
            System.out.println("2. Search jobs");
            System.out.println("3. Apply for a job");
            System.out.println("4. My applications");
            System.out.println("0. Back");
            switch (prompt("Choose: ")) {
                case "1" -> listJobs(jobs);
                case "2" -> searchJobs();
                case "3" -> applyJob(name);
                case "4" -> myApplications(name);
                case "0" -> in = false;
                default -> System.out.println("Invalid option.");
            }
        }
    }

    static void searchJobs() {
        String kw = prompt("Keyword (title/company/skill/location): ").toLowerCase();
        List<Job> found = new ArrayList<>();
        for (Job j : jobs)
            if ((j.title + " " + j.company + " " + j.skills + " " + j.location).toLowerCase().contains(kw))
                found.add(j);
        if (found.isEmpty()) System.out.println("No matching jobs.");
        else listJobs(found);
    }

    static void applyJob(String name) {
        listJobs(jobs);
        String idStr = prompt("Enter job ID to apply: ");
        Job j = findJob(idStr);
        if (j == null) { System.out.println("Job not found."); return; }
        // prevent duplicate applications
        for (String line : readLines(APP_FILE)) {
            String[] p = line.split("\\|", -1);
            if (p.length >= 2 && p[0].equals(name) && p[1].equals(String.valueOf(j.id))) {
                System.out.println("You have already applied to this job."); return;
            }
        }
        try (PrintWriter pw = new PrintWriter(new FileWriter(APP_FILE, true))) {
            pw.println(String.join("|", name, String.valueOf(j.id), j.title, j.company));
            System.out.println("✓ Applied to '" + j.title + "' at " + j.company + "!");
        } catch (IOException e) { System.out.println("Error applying: " + e.getMessage()); }
    }

    static void myApplications(String name) {
        System.out.println("\nApplications by " + name + ":");
        boolean any = false;
        for (String line : readLines(APP_FILE)) {
            String[] p = line.split("\\|", -1);
            if (p.length >= 4 && p[0].equals(name)) {
                System.out.println("  #" + p[1] + "  " + p[2] + " @ " + p[3]); any = true;
            }
        }
        if (!any) System.out.println("  (none yet)");
    }

    // ---------- Employer ----------
    static void employerMenu() {
        String company = prompt("Company name: ");
        if (company.isBlank()) { System.out.println("Company name required."); return; }
        boolean in = true;
        while (in) {
            System.out.println("\n--- EMPLOYER (" + company + ") ---");
            System.out.println("1. Post a job");
            System.out.println("2. View my posted jobs");
            System.out.println("3. View applicants for a job");
            System.out.println("4. Delete a job");
            System.out.println("0. Back");
            switch (prompt("Choose: ")) {
                case "1" -> postJob(company);
                case "2" -> listJobs(byCompany(company));
                case "3" -> viewApplicants(company);
                case "4" -> deleteJob(company);
                case "0" -> in = false;
                default -> System.out.println("Invalid option.");
            }
        }
    }

    static void postJob(String company) {
        String title = prompt("Job title: ");
        String location = prompt("Location: ");
        String type = prompt("Type (Full-time/Part-time/Internship): ");
        double salary = readAmount("Annual salary: ");
        String skills = prompt("Required skills (comma separated): ");
        int id = nextId();
        jobs.add(new Job(id, title, company, location, type, salary, skills, company));
        saveJobs();
        System.out.println("✓ Job posted with ID " + id + ".");
    }

    static void viewApplicants(String company) {
        String idStr = prompt("Job ID: ");
        Job j = findJob(idStr);
        if (j == null || !j.company.equals(company)) { System.out.println("Not your job / not found."); return; }
        System.out.println("\nApplicants for '" + j.title + "':");
        boolean any = false;
        for (String line : readLines(APP_FILE)) {
            String[] p = line.split("\\|", -1);
            if (p.length >= 2 && p[1].equals(String.valueOf(j.id))) { System.out.println("  - " + p[0]); any = true; }
        }
        if (!any) System.out.println("  (no applicants yet)");
    }

    static void deleteJob(String company) {
        String idStr = prompt("Job ID to delete: ");
        Job j = findJob(idStr);
        if (j == null || !j.company.equals(company)) { System.out.println("Not your job / not found."); return; }
        jobs.remove(j); saveJobs();
        System.out.println("✓ Deleted job #" + j.id + ".");
    }

    // ---------- Shared ----------
    static void listJobs(List<Job> list) {
        if (list.isEmpty()) { System.out.println("No jobs to show."); return; }
        System.out.println();
        for (Job j : list) {
            System.out.printf("#%d  %s @ %s%n", j.id, j.title, j.company);
            System.out.printf("    %s | %s | salary %.0f%n", j.location, j.type, j.salary);
            System.out.printf("    skills: %s%n", j.skills);
        }
    }
    static List<Job> byCompany(String c) {
        List<Job> out = new ArrayList<>();
        for (Job j : jobs) if (j.company.equals(c)) out.add(j);
        return out;
    }
    static Job findJob(String idStr) {
        try { int id = Integer.parseInt(idStr); for (Job j : jobs) if (j.id == id) return j; }
        catch (NumberFormatException ignored) {}
        return null;
    }
    static int nextId() { int m = 1000; for (Job j : jobs) m = Math.max(m, j.id); return m + 1; }

    // ---------- Persistence ----------
    static void loadJobs() { for (String l : readLines(JOB_FILE)) jobs.add(Job.fromLine(l)); }
    static void saveJobs() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(JOB_FILE))) {
            for (Job j : jobs) pw.println(j.toLine());
        } catch (IOException e) { System.out.println("Error saving jobs: " + e.getMessage()); }
    }
    static void seedIfMissing() {
        if (new File(JOB_FILE).exists()) return;
        List<Job> seed = List.of(
            new Job(1001, "Frontend Developer", "Brightwave", "Tirana", "Full-time", 26000, "HTML,CSS,JavaScript", "Brightwave"),
            new Job(1002, "Java Developer", "Corewave", "Remote", "Full-time", 32000, "Java,Spring,MySQL", "Corewave"),
            new Job(1003, "UI/UX Intern", "Nimbus", "Tirana", "Internship", 9000, "Figma,UI Design", "Nimbus")
        );
        jobs.addAll(seed); saveJobs();
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
    static double readAmount(String msg) {
        while (true) {
            try { return Double.parseDouble(prompt(msg)); }
            catch (NumberFormatException e) { System.out.println("Enter a valid number."); }
        }
    }
}
