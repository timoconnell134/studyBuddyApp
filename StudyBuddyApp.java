import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Study Buddy - Ultra-simple CLI app for Clemson students.
 * Pattern: MVC (Models + Controllers + CLI View in one file for simplicity)
 *
 * Requirements covered (only these):
 * - Create a student profile with enrolled courses (prompted at startup).
 * - Add/remove availability.
 * - See classmates in the same course (pre-seeded classmates).
 * - Suggest matches (find overlapping availability by course).
 * - Propose sessions and the invitee can confirm/decline.
 * - Command-line, in-memory, no auth.
 *
 * Seeded data (exact per request):
 *   People: Alice, Bob, Jon, Mary (4 classmates) + the profile you create at runtime.
 *   Courses: CPSC 3720, MATH 3110.
 *
 * Compile:  javac StudyBuddyApp.java
 * Run:      java StudyBuddyApp
 */
public class StudyBuddyApp {

    // ======== MODEL ======== //

    /** Represents a day/time window on a single day. */
    static class TimeSlot {
        final DayOfWeek day;
        final LocalTime start;
        final LocalTime end;

        TimeSlot(DayOfWeek day, LocalTime start, LocalTime end) {
            if (end.isBefore(start) || end.equals(start)) {
                throw new IllegalArgumentException("End time must be after start time");
            }
            this.day = Objects.requireNonNull(day);
            this.start = Objects.requireNonNull(start);
            this.end = Objects.requireNonNull(end);
        }

        boolean overlaps(TimeSlot other) {
            if (this.day != other.day) return false;
            return !(this.end.isBefore(other.start) || this.start.isAfter(other.end));
        }

        /** Returns the intersection of two overlapping slots (same day). Returns null if no overlap. */
        TimeSlot intersection(TimeSlot other) {
            if (!overlaps(other)) return null;
            LocalTime s = this.start.isAfter(other.start) ? this.start : other.start;
            LocalTime e = this.end.isBefore(other.end) ? this.end : other.end;
            if (e.isAfter(s)) return new TimeSlot(this.day, s, e);
            return null;
        }

        @Override public String toString() {
            return day + " " + start + "-" + end;
        }
    }

    static class Student {
        final int id;
        String name;
        final Set<String> courses = new LinkedHashSet<>();
        final List<TimeSlot> availability = new ArrayList<>();

        Student(int id, String name) { this.id = id; this.name = name; }
        void addCourse(String course) { courses.add(normalizeCourse(course)); }
        void addAvailability(TimeSlot slot) { availability.add(slot); }
        boolean removeAvailability(int index) {
            if (index < 0 || index >= availability.size()) return false;
            availability.remove(index);
            return true;
        }
        @Override public String toString() {
            return String.format("[%d] %s | Courses=%s | Avail=%d slots", id, name, courses, availability.size());
        }
    }

    enum SessionStatus { PENDING, CONFIRMED, DECLINED }

    static class StudySession {
        final int id;
        final int inviterId;
        final int inviteeId;
        final String course; // normalized
        final TimeSlot time;
        SessionStatus status = SessionStatus.PENDING;

        StudySession(int id, int inviterId, int inviteeId, String course, TimeSlot time) {
            this.id = id; this.inviterId = inviterId; this.inviteeId = inviteeId; this.course = course; this.time = time;
        }
        @Override public String toString() {
            return String.format("Session[%d] %s | %s | inviter=%d -> invitee=%d | status=%s", id, course, time, inviterId, inviteeId, status);
        }
    }

    /** Simple in-memory repository. */
    static class Repository {
        private final Map<Integer, Student> students = new LinkedHashMap<>();
        private final Map<Integer, StudySession> sessions = new LinkedHashMap<>();
        private int studentSeq = 1;
        private int sessionSeq = 1;

        Student createStudent(String name) {
            Student s = new Student(studentSeq++, name);
            students.put(s.id, s);
            return s;
        }
        Student getStudent(int id) { return students.get(id); }
        Collection<Student> allStudents() { return students.values(); }

        List<Student> classmatesInCourse(int studentId, String course) {
            String c = normalizeCourse(course);
            List<Student> res = new ArrayList<>();
            for (Student s : students.values()) {
                if (s.id == studentId) continue;
                if (s.courses.contains(c)) res.add(s);
            }
            return res;
        }

        StudySession proposeSession(int inviterId, int inviteeId, String course, TimeSlot time) {
            StudySession ss = new StudySession(sessionSeq++, inviterId, inviteeId, normalizeCourse(course), time);
            sessions.put(ss.id, ss);
            return ss;
        }
        List<StudySession> incomingFor(int studentId) {
            List<StudySession> res = new ArrayList<>();
            for (StudySession s : sessions.values()) if (s.inviteeId == studentId) res.add(s);
            return res;
        }
        List<StudySession> outgoingFor(int studentId) {
            List<StudySession> res = new ArrayList<>();
            for (StudySession s : sessions.values()) if (s.inviterId == studentId) res.add(s);
            return res;
        }
        void respond(int sessionId, boolean accept) {
            StudySession ss = sessions.get(sessionId);
            if (ss != null && ss.status == SessionStatus.PENDING) {
                ss.status = accept ? SessionStatus.CONFIRMED : SessionStatus.DECLINED;
            }
        }
    }

    // ======== CONTROLLERS (minimal) ======== //

    static class ProfileController {
        private final Repository repo; ProfileController(Repository r) { this.repo = r; }
        Student createProfile(String name, List<String> initialCourses) {
            Student s = repo.createStudent(name);
            for (String c : initialCourses) s.addCourse(c);
            return s;
        }
    }

    static class AvailabilityController {
        private final Repository repo; AvailabilityController(Repository r) { this.repo = r; }
        void addAvailability(int studentId, TimeSlot slot) { Student s = repo.getStudent(studentId); if (s!=null) s.addAvailability(slot); }
        boolean removeAvailability(int studentId, int index) { Student s = repo.getStudent(studentId); return s!=null && s.removeAvailability(index); }
    }

    static class SessionController {
        private final Repository repo; SessionController(Repository r) { this.repo = r; }
        List<Student> classmates(int studentId, String course) { return repo.classmatesInCourse(studentId, course); }
        Map<Student, List<TimeSlot>> suggestMatches(int studentId, String course) {
            Student me = repo.getStudent(studentId);
            Map<Student, List<TimeSlot>> res = new LinkedHashMap<>();
            if (me == null) return res;
            for (Student peer : classmates(studentId, course)) {
                List<TimeSlot> overlaps = new ArrayList<>();
                for (TimeSlot a : me.availability) for (TimeSlot b : peer.availability) {
                    TimeSlot inter = a.intersection(b);
                    if (inter != null) overlaps.add(inter);
                }
                if (!overlaps.isEmpty()) res.put(peer, mergeAdjacent(overlaps));
            }
            return res;
        }
        StudySession propose(int inviterId, int inviteeId, String course, TimeSlot chosenFromInviterAvail) { return repo.proposeSession(inviterId, inviteeId, course, chosenFromInviterAvail); }
        List<StudySession> incoming(int studentId) { return repo.incomingFor(studentId); }
        List<StudySession> outgoing(int studentId) { return repo.outgoingFor(studentId); }
        void respond(int sessionId, boolean accept) { repo.respond(sessionId, accept); }

        private static List<TimeSlot> mergeAdjacent(List<TimeSlot> slots) {
            if (slots.isEmpty()) return slots;
            slots.sort(Comparator.<TimeSlot, DayOfWeek>comparing(ts -> ts.day).thenComparing(ts -> ts.start).thenComparing(ts -> ts.end));
            List<TimeSlot> merged = new ArrayList<>();
            TimeSlot cur = slots.get(0);
            for (int i = 1; i < slots.size(); i++) {
                TimeSlot nxt = slots.get(i);
                if (cur.day == nxt.day && !cur.end.isBefore(nxt.start)) {
                    cur = new TimeSlot(cur.day, cur.start, cur.end.isAfter(nxt.end) ? cur.end : nxt.end);
                } else { merged.add(cur); cur = nxt; }
            }
            merged.add(cur);
            return merged;
        }
    }

    // ======== VIEW (CLI) ======== //

    static class CLI {
        private final Scanner in = new Scanner(System.in);
        private final Repository repo;
        private final ProfileController profileCtl;
        private final AvailabilityController availCtl;
        private final SessionController sessionCtl;
        private Integer activeStudentId = null;
        private final DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");

        // Fixed course catalog per spec
        private static final String C1 = "CPSC 3720";
        private static final String C2 = "MATH 3110";

        CLI(Repository repo) {
            this.repo = repo;
            this.profileCtl = new ProfileController(repo);
            this.availCtl = new AvailabilityController(repo);
            this.sessionCtl = new SessionController(repo);
        }

        void run() {
            println(" === Study Buddy (CLI) ===");
                    seedClassmates();
            promptCreateProfile();
            showRoster();
            // main loop
            while (true) {
                try {
                    showHeader();
                    showMenu();
                    String choice = prompt("Select> ");
                    switch (choice) {
                        case "1": manageAvailability(); break;
                        case "2": viewClassmates(); break;
                        case "3": suggestMatches(); break;
                        case "4": proposeSession(); break;
                        case "5": reviewIncoming(); break;
                        case "6": reviewOutgoing(); break;
                        case "0": println("Goodbye!"); return;
                        default: println("Unknown option");
                    }
                } catch (Exception e) {
                    println("Error: " + e.getMessage());
                }
            }
        }

        private void showHeader() {
            println(" Active: " + repo.getStudent(activeStudentId));
        }
        private void showMenu() {
            println(" 1) Manage availability (add/remove)");
            println("2) View classmates in my course");
            println("3) Suggest matches (overlaps)");
            println("4) Propose a study session");
            println("5) Review incoming (confirm/decline)");
            println("6) Review outgoing");
            println("0) Exit");
        }

        // ---- Startup helpers ----
        private void seedClassmates() {
            // EXACT requested seed: Alice, Bob, Jon, Mary and courses CPSC 3720 / MATH 3110
            Student alice = repo.createStudent("Alice");
            alice.addCourse(C1); // CPSC 3720
            alice.addAvailability(new TimeSlot(DayOfWeek.MONDAY, LocalTime.of(14,0), LocalTime.of(16,0)));
            alice.addAvailability(new TimeSlot(DayOfWeek.WEDNESDAY, LocalTime.of(10,0), LocalTime.of(11,30)));

            Student bob = repo.createStudent("Bob");
            bob.addCourse(C1);
            bob.addAvailability(new TimeSlot(DayOfWeek.MONDAY, LocalTime.of(15,0), LocalTime.of(17,0)));

            Student jon = repo.createStudent("Jon");
            jon.addCourse(C2); // MATH 3110
            jon.addAvailability(new TimeSlot(DayOfWeek.TUESDAY, LocalTime.of(9,0), LocalTime.of(11,0)));

            Student mary = repo.createStudent("Mary");
            mary.addCourse(C2);
            mary.addAvailability(new TimeSlot(DayOfWeek.TUESDAY, LocalTime.of(10,0), LocalTime.of(12,0)));
        }

        private void promptCreateProfile() {
            println(" -- Create Your Profile --");
                    String name = prompt("Your name: ");
            Student me = profileCtl.createProfile(name, chooseCourses());
            activeStudentId = me.id;
            println("Welcome, " + name + "! Profile created.");
        }

        private List<String> chooseCourses() {
            println("Enroll in one or both courses (comma-separated indices):");
            println("  [0] " + C1);
            println("  [1] " + C2);
            String input = prompt("Choose (e.g., 0 or 0,1): ");
            Set<Integer> idxs = new LinkedHashSet<>();
            for (String part : input.split(",")) {
                part = part.trim(); if (part.isEmpty()) continue;
                int i = Integer.parseInt(part);
                if (i==0 || i==1) idxs.add(i); else throw new IllegalArgumentException("Invalid index: "+i);
            }
            if (idxs.isEmpty()) throw new IllegalArgumentException("Select at least one course.");
            List<String> courses = new ArrayList<>();
            if (idxs.contains(0)) courses.add(C1);
            if (idxs.contains(1)) courses.add(C2);
            return courses;
        }

        private void showRoster() {
            println(" -- Current Roster --");
            for (Student s : repo.allStudents()) println("  - " + s);
        }

        // ---- Core actions ----
        private void manageAvailability() {
            Student me = repo.getStudent(activeStudentId);
            println(" Your availability:");
            for (int i = 0; i < me.availability.size(); i++) println("  [" + i + "] " + me.availability.get(i));
            println("a) add, r) remove, x) back");
            String ch = prompt("> ");
            if (ch.equalsIgnoreCase("a")) {
                DayOfWeek day = parseDay(prompt("Day (Mon..Sun): "));
                LocalTime start = parseTime(prompt("Start (HH:mm): "));
                LocalTime end = parseTime(prompt("End   (HH:mm): "));
                availCtl.addAvailability(me.id, new TimeSlot(day, start, end));
                println("Added.");
            } else if (ch.equalsIgnoreCase("r")) {
                int idx = Integer.parseInt(prompt("Index to remove: "));
                boolean ok = availCtl.removeAvailability(me.id, idx);
                println(ok ? "Removed." : "Invalid index.");
            }
        }

        private void viewClassmates() {
            Student me = repo.getStudent(activeStudentId);
            String course = pickCourseFromMine(me);
            List<Student> peers = sessionCtl.classmates(me.id, course);
            if (peers.isEmpty()) println("No classmates found for that course.");
            else {
                println("Classmates in " + normalizeCourse(course) + ":");
                for (Student s : peers) println("  - " + s);
            }
        }

        private void suggestMatches() {
            Student me = repo.getStudent(activeStudentId);
            String course = pickCourseFromMine(me);
            Map<Student, List<TimeSlot>> suggestions = sessionCtl.suggestMatches(me.id, course);
            if (suggestions.isEmpty()) { println("No overlapping availability found."); return; }
            println(" Suggested matches (overlaps):");
            for (Map.Entry<Student, List<TimeSlot>> e : suggestions.entrySet()) {
                println("* " + e.getKey());
                for (TimeSlot ts : e.getValue()) println("    - " + ts);
            }
        }

        private void proposeSession() {
            Student me = repo.getStudent(activeStudentId);
            if (me.availability.isEmpty()) { println("Add availability first."); return; }
            String course = pickCourseFromMine(me);
            List<Student> peers = sessionCtl.classmates(me.id, course);
            if (peers.isEmpty()) { println("No classmates in that course."); return; }
            println("Choose invitee:");
            for (int i = 0; i < peers.size(); i++) println("  [" + i + "] " + peers.get(i));
            int idx = Integer.parseInt(prompt("Index: "));
            if (idx < 0 || idx >= peers.size()) { println("Invalid index"); return; }
            Student invitee = peers.get(idx);

            println("Choose a time from YOUR availability:");
            for (int i = 0; i < me.availability.size(); i++) println("  [" + i + "] " + me.availability.get(i));
            int tIdx = Integer.parseInt(prompt("Index: "));
            if (tIdx < 0 || tIdx >= me.availability.size()) { println("Invalid index"); return; }
            TimeSlot chosen = me.availability.get(tIdx);

            StudySession ss = sessionCtl.propose(me.id, invitee.id, course, chosen);
            println("Proposed: " + ss);
        }

        private void reviewIncoming() {
            List<StudySession> incoming = sessionCtl.incoming(activeStudentId);
            if (incoming.isEmpty()) { println("No incoming proposals."); return; }
            println("Incoming proposals:");
            for (StudySession s : incoming) println("  " + s);
            String sIdStr = prompt("Enter session ID to respond (or blank to skip): ");
            if (sIdStr.isBlank()) return;
            int sId = Integer.parseInt(sIdStr);
            String resp = prompt("Accept (y) / Decline (n): ");
            sessionCtl.respond(sId, resp.equalsIgnoreCase("y"));
            println("Updated.");
        }

        private void reviewOutgoing() {
            List<StudySession> outgoing = sessionCtl.outgoing(activeStudentId);
            if (outgoing.isEmpty()) { println("No outgoing proposals."); return; }
            println("Outgoing proposals:");
            for (StudySession s : outgoing) println("  " + s);
        }

        // --- helpers ---
        private String pickCourseFromMine(Student me) {
            if (me.courses.isEmpty()) throw new IllegalStateException("You must be enrolled in at least one course.");
            List<String> mine = new ArrayList<>(me.courses);
            for (int i = 0; i < mine.size(); i++) println("  [" + i + "] " + mine.get(i));
            int idx = Integer.parseInt(prompt("Choose your course index: "));
            if (idx < 0 || idx >= mine.size()) throw new IllegalArgumentException("Invalid index");
            return mine.get(idx);
        }

        private String prompt(String msg) { System.out.print(msg); return in.nextLine().trim(); }
        private void println(String s) { System.out.println(s); }
        private LocalTime parseTime(String s) { return LocalTime.parse(s, tf); }
        private DayOfWeek parseDay(String s) {
            s = s.trim();
            try { return DayOfWeek.valueOf(s.toUpperCase()); } catch (Exception ignored) {}
            switch (s.toLowerCase()) {
                case "mon": return DayOfWeek.MONDAY;
                case "tue": case "tues": return DayOfWeek.TUESDAY;
                case "wed": return DayOfWeek.WEDNESDAY;
                case "thu": case "thur": case "thurs": return DayOfWeek.THURSDAY;
                case "fri": return DayOfWeek.FRIDAY;
                case "sat": return DayOfWeek.SATURDAY;
                case "sun": return DayOfWeek.SUNDAY;
            }
            throw new IllegalArgumentException("Unrecognized day: " + s);
        }
    }

    // ======== BOOT ======== //
    public static void main(String[] args) {
        Repository repo = new Repository();
        CLI cli = new CLI(repo);
        cli.run();
    }

    // ======== UTIL ======== //
    static String normalizeCourse(String c) { return c.trim().toUpperCase(Locale.ROOT); }
}
