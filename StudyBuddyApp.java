import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Study Buddy - Streamlined CLI app for Clemson students.
 *
 * Features:
 * - Create profile (prompted at startup) & enroll in courses.
 * - Add/remove availability.
 * - View classmates in your course.
 * - Suggest matches (overlapping availability).
 * - Search existing sessions; join one (course check enforced).
 * - Create your own session (you choose course + day/time).
 * - Confirm meetings you’re in (per-participant confirmations).
 * - View all students' availability.
 *
 * Seeded data:
 *   Students: Alice, Bob, Jon, Mary (+ your new profile)
 *   Courses: CPSC 3720, MATH 3110
 *   Pre-planned sessions exist so you can join/confirm immediately.
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

        TimeSlot intersection(TimeSlot other) {
            if (!overlaps(other)) return null;
            LocalTime s = this.start.isAfter(other.start) ? this.start : other.start;
            LocalTime e = this.end.isBefore(other.end) ? this.end : other.end;
            if (e.isAfter(s)) return new TimeSlot(this.day, s, e);
            return null;
        }

        @Override public String toString() { return day + " " + start + "-" + end; }
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

    /** Meeting/session with a participant list and confirmations per participant. */
    static class StudySession {
        final int id;
        final String course; // normalized
        final TimeSlot time;
        final Set<Integer> participantIds = new LinkedHashSet<>();
        final Set<Integer> confirmedIds = new LinkedHashSet<>();

        StudySession(int id, String course, TimeSlot time, Collection<Integer> participants) {
            this.id = id; this.course = normalizeCourse(course); this.time = time;
            if (participants != null) participantIds.addAll(participants);
        }
        boolean isParticipant(int studentId) { return participantIds.contains(studentId); }
        void addParticipant(int studentId) { participantIds.add(studentId); }
        void confirm(int studentId) { if (participantIds.contains(studentId)) confirmedIds.add(studentId); }
        boolean isFullyConfirmed() { return !participantIds.isEmpty() && confirmedIds.containsAll(participantIds); }
        @Override public String toString() {
            // Minimal—CLI prints friendly names.
            return String.format("Session[%d] %s | %s", id, course, time);
        }
    }

    // ======== REPOSITORY ======== //

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

        StudySession createSession(String course, TimeSlot time, Collection<Integer> participants) {
            StudySession ss = new StudySession(sessionSeq++, course, time, participants);
            sessions.put(ss.id, ss);
            return ss;
        }
        StudySession getSession(int id) { return sessions.get(id); }
        Collection<StudySession> allSessions() { return sessions.values(); }

        List<Student> classmatesInCourse(int studentId, String course) {
            String c = normalizeCourse(course);
            List<Student> res = new ArrayList<>();
            for (Student s : students.values()) {
                if (s.id == studentId) continue;
                if (s.courses.contains(c)) res.add(s);
            }
            return res;
        }

        List<StudySession> searchSessionsByCourse(String course) {
            String c = normalizeCourse(course);
            List<StudySession> res = new ArrayList<>();
            for (StudySession s : sessions.values()) if (s.course.equals(c)) res.add(s);
            return res;
        }
        List<StudySession> searchSessionsByStudentName(String nameSubstr) {
            String q = nameSubstr.toLowerCase();
            List<StudySession> res = new ArrayList<>();
            for (StudySession s : sessions.values()) {
                for (Integer pid : s.participantIds) {
                    Student st = students.get(pid);
                    if (st != null && st.name.toLowerCase().contains(q)) { res.add(s); break; }
                }
            }
            return res;
        }
    }

    // ======== CONTROLLERS ======== //

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
        Collection<StudySession> allSessions() { return repo.allSessions(); }
        List<StudySession> searchByCourse(String course) { return repo.searchSessionsByCourse(course); }
        List<StudySession> searchByStudentName(String nameSubstr) { return repo.searchSessionsByStudentName(nameSubstr); }
        StudySession getSession(int id) { return repo.getSession(id); }
        StudySession create(String course, TimeSlot time, Collection<Integer> participants) { return repo.createSession(course, time, participants); }
        void join(int sessionId, int studentId) { StudySession s = repo.getSession(sessionId); if (s!=null) s.addParticipant(studentId); }
        void confirm(int sessionId, int studentId) { StudySession s = repo.getSession(sessionId); if (s!=null) s.confirm(studentId); }

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

        private static List<TimeSlot> mergeAdjacent(List<TimeSlot> slots) {
            if (slots.isEmpty()) return slots;
            slots.sort(Comparator.<TimeSlot, DayOfWeek>comparing(ts -> ts.day)
                    .thenComparing(ts -> ts.start)
                    .thenComparing(ts -> ts.end));
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
            println("\n=== Study Buddy (CLI) ===");
            seedClassmates();
            seedPlannedSessions();
            promptCreateProfile();
            // (Removed showRoster() at startup)
            while (true) {
                try {
                    showHeader();
                    showMenu();
                    String choice = prompt("Select> ");
                    switch (choice) {
                        case "1": manageAvailability(); break;
                        case "2": viewClassmates(); break;
                        case "3": suggestMatches(); break;
                        case "4": searchSessions(); break;
                        case "5": createNewSession(); break;
                        case "6": joinSession(); break;
                        case "7": confirmMyMeetings(); break;
                        case "8": viewAllStudentsAvailability(); break; // NEW
                        case "0": println("Goodbye!"); return;
                        default: println("Unknown option");
                    }
                } catch (Exception e) {
                    println("Error: " + e.getMessage());
                }
            }
        }

        private void showHeader() { println("\nActive: " + repo.getStudent(activeStudentId)); }
        private void showMenu() {
            println("\n1) Manage availability (add/remove)");
            println("2) View classmates in my course");
            println("3) Suggest matches (overlaps)");
            println("4) Search sessions (all / by course / by name)");
            println("5) Create a new session");
            println("6) Join an existing session");
            println("7) Confirm my meetings");
            println("8) View students' availability");
            println("0) Exit");
        }

        // ---- Startup helpers ----
        private void seedClassmates() {
            Student alice = repo.createStudent("Alice");
            alice.addCourse(C1);
            alice.addAvailability(new TimeSlot(DayOfWeek.MONDAY, LocalTime.of(14,0), LocalTime.of(16,0)));
            alice.addAvailability(new TimeSlot(DayOfWeek.WEDNESDAY, LocalTime.of(10,0), LocalTime.of(11,30)));

            Student bob = repo.createStudent("Bob");
            bob.addCourse(C1);
            bob.addAvailability(new TimeSlot(DayOfWeek.MONDAY, LocalTime.of(15,0), LocalTime.of(17,0)));

            Student jon = repo.createStudent("Jon");
            jon.addCourse(C2);
            jon.addAvailability(new TimeSlot(DayOfWeek.TUESDAY, LocalTime.of(9,0), LocalTime.of(11,0)));

            Student mary = repo.createStudent("Mary");
            mary.addCourse(C2);
            mary.addAvailability(new TimeSlot(DayOfWeek.TUESDAY, LocalTime.of(10,0), LocalTime.of(12,0)));
        }

        private void seedPlannedSessions() {
            Student alice = findByName("Alice");
            Student bob = findByName("Bob");
            Student jon = findByName("Jon");
            Student mary = findByName("Mary");
            if (alice != null && bob != null) {
                sessionCtl.create(C1, new TimeSlot(DayOfWeek.MONDAY, LocalTime.of(15,0), LocalTime.of(16,0)),
                        List.of(alice.id, bob.id));
            }
            if (jon != null && mary != null) {
                sessionCtl.create(C2, new TimeSlot(DayOfWeek.TUESDAY, LocalTime.of(10,30), LocalTime.of(11,30)),
                        List.of(jon.id, mary.id));
            }
        }

        private Student findByName(String name) {
            for (Student s : repo.allStudents()) if (s.name.equalsIgnoreCase(name)) return s;
            return null;
        }

        private void promptCreateProfile() {
            println("\n-- Create Your Profile --");
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

        // ---- Core actions ----
        private void manageAvailability() {
            Student me = repo.getStudent(activeStudentId);
            println("\nYour availability:");
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
            println("\nSuggested matches (overlaps):");
            for (Map.Entry<Student, List<TimeSlot>> e : suggestions.entrySet()) {
                println("* " + e.getKey());
                for (TimeSlot ts : e.getValue()) println("    - " + ts);
            }
        }

        private void searchSessions() {
            println("\nSearch sessions: a) all, c) by course, n) by name, x) back");
            String ch = prompt("> ");
            if (ch.equalsIgnoreCase("a")) {
                for (StudySession s : sessionCtl.allSessions()) printSessionLine(s);
            } else if (ch.equalsIgnoreCase("c")) {
                String course = prompt("Course (e.g., CPSC 3720): ");
                List<StudySession> list = sessionCtl.searchByCourse(course);
                if (list.isEmpty()) println("No sessions for that course.");
                for (StudySession s : list) printSessionLine(s);
            } else if (ch.equalsIgnoreCase("n")) {
                String name = prompt("Student name contains: ");
                List<StudySession> list = sessionCtl.searchByStudentName(name);
                if (list.isEmpty()) println("No sessions involving that name.");
                for (StudySession s : list) printSessionLine(s);
            }
        }

        private void createNewSession() {
            Student me = repo.getStudent(activeStudentId);
            String course = pickCourseFromMine(me);
            DayOfWeek day = parseDay(prompt("Day (Mon..Sun): "));
            LocalTime start = parseTime(prompt("Start (HH:mm): "));
            LocalTime end = parseTime(prompt("End   (HH:mm): "));
            TimeSlot ts = new TimeSlot(day, start, end);
            StudySession ss = sessionCtl.create(course, ts, List.of(me.id));
            println("Created session:");
            printSessionLine(ss);
        }

        private void joinSession() {
            List<StudySession> all = new ArrayList<>(sessionCtl.allSessions());
            if (all.isEmpty()) { println("No sessions available."); return; }
            println("\nSessions:");
            for (StudySession s : all) printSessionLine(s);
            int id = Integer.parseInt(prompt("Enter session ID to join: "));
            StudySession target = sessionCtl.getSession(id);
            if (target == null) { println("No such session."); return; }
            Student me = repo.getStudent(activeStudentId);
            if (!me.courses.contains(target.course)) {
                println("You must be enrolled in " + target.course + " to join.");
                return;
            }
            sessionCtl.join(id, me.id);
            println("Joined session " + id + ".");
        }

        private void confirmMyMeetings() {
            Student me = repo.getStudent(activeStudentId);
            List<StudySession> mine = new ArrayList<>();
            for (StudySession s : sessionCtl.allSessions()) if (s.isParticipant(me.id)) mine.add(s);
            if (mine.isEmpty()) { println("You are not in any sessions yet. Join or create one first."); return; }
            println("\nYour sessions:");
            for (StudySession s : mine) printSessionLine(s);

            String pick = prompt("Enter session ID to confirm (or blank to cancel): ");
            if (pick.isBlank()) return;
            int id = Integer.parseInt(pick);
            StudySession target = sessionCtl.getSession(id);
            if (target == null || !target.isParticipant(me.id)) { println("Invalid choice."); return; }
            println("Are you sure you want to meet for " + target.course + " at " + target.time +
                    " with participants " + toNames(target.participantIds) + "? Type 'confirm' to proceed: ");
            String resp = prompt("");
            if (resp.equalsIgnoreCase("confirm")) {
                sessionCtl.confirm(id, me.id);
                println("Confirmed. " + (target.isFullyConfirmed() ? "(All participants confirmed!)" : ""));
            } else {
                println("Not confirmed.");
            }
        }

        // NEW: View all students' availability (names with each slot)
        private void viewAllStudentsAvailability() {
            println("\n-- Students' Availability --");
            for (Student s : repo.allStudents()) {
                println("  " + s.name + " (" + s.courses + "):");
                if (s.availability.isEmpty()) {
                    println("    (no availability added)");
                } else {
                    for (TimeSlot ts : s.availability) {
                        println("    - " + ts);
                    }
                }
            }
        }

        // --- printing helpers ---
        private void printSessionLine(StudySession s) {
            String participants = toNames(s.participantIds);
            String confirmed = toNames(s.confirmedIds);
            String tail = s.isFullyConfirmed() ? " (ALL CONFIRMED)" : "";
            println("  [" + s.id + "] " + s.course + " | " + s.time +
                    " | participants=" + participants +
                    " | confirmed=" + confirmed + tail);
        }

        private String toNames(Collection<Integer> ids) {
            List<String> names = new ArrayList<>();
            for (Integer pid : ids) {
                Student s = repo.getStudent(pid);
                if (s != null) names.add(s.name);
            }
            return names.toString();
        }

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
