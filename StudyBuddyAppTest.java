import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class StudyBuddyAppTest {

    private StudyBuddyApp.Repository repo;
    private StudyBuddyApp.ProfileController profileCtl;
    private StudyBuddyApp.AvailabilityController availCtl;
    private StudyBuddyApp.SessionController sessionCtl;

    private int aliceId, bobId, jonId, maryId;

    @BeforeEach
    void setup() {
        repo = new StudyBuddyApp.Repository();
        profileCtl = new StudyBuddyApp.ProfileController(repo);
        availCtl = new StudyBuddyApp.AvailabilityController(repo);
        sessionCtl = new StudyBuddyApp.SessionController(repo);

        // Seed students
        aliceId = repo.createStudent("Alice").id;
        bobId   = repo.createStudent("Bob").id;
        jonId   = repo.createStudent("Jon").id;
        maryId  = repo.createStudent("Mary").id;

        // Enrollments
        repo.getStudent(aliceId).addCourse("CPSC 3720");
        repo.getStudent(bobId).addCourse("CPSC 3720");
        repo.getStudent(jonId).addCourse("MATH 3110");
        repo.getStudent(maryId).addCourse("MATH 3110");

        // Availability
        availCtl.addAvailability(aliceId,
                new StudyBuddyApp.TimeSlot(DayOfWeek.MONDAY, LocalTime.of(14,0), LocalTime.of(16,0)));
        availCtl.addAvailability(bobId,
                new StudyBuddyApp.TimeSlot(DayOfWeek.MONDAY, LocalTime.of(15,0), LocalTime.of(17,0)));
        availCtl.addAvailability(jonId,
                new StudyBuddyApp.TimeSlot(DayOfWeek.TUESDAY, LocalTime.of(9,0), LocalTime.of(11,0)));
        availCtl.addAvailability(maryId,
                new StudyBuddyApp.TimeSlot(DayOfWeek.TUESDAY, LocalTime.of(10,0), LocalTime.of(12,0)));
    }

    // ---- TimeSlot ----

    @Test
    void timeSlot_constructorRejectsInvalidTimes() {
        assertThrows(IllegalArgumentException.class, () ->
                new StudyBuddyApp.TimeSlot(DayOfWeek.MONDAY,
                        LocalTime.of(10,0), LocalTime.of(10,0)));
        assertThrows(IllegalArgumentException.class, () ->
                new StudyBuddyApp.TimeSlot(DayOfWeek.MONDAY,
                        LocalTime.of(11,0), LocalTime.of(10,0)));
    }

    @Test
    void timeSlot_overlapAndIntersection() {
        StudyBuddyApp.TimeSlot a = new StudyBuddyApp.TimeSlot(DayOfWeek.MONDAY,
                LocalTime.of(10,0), LocalTime.of(12,0));
        StudyBuddyApp.TimeSlot b = new StudyBuddyApp.TimeSlot(DayOfWeek.MONDAY,
                LocalTime.of(11,0), LocalTime.of(13,0));

        assertTrue(a.overlaps(b));
        var inter = a.intersection(b);
        assertNotNull(inter);
        assertEquals(LocalTime.of(11,0), inter.start);
        assertEquals(LocalTime.of(12,0), inter.end);
    }

    // ---- Profile / Availability ----

    @Test
    void profileController_createsProfileAndNormalizesCourses() {
        StudyBuddyApp.Student tim =
                profileCtl.createProfile("Tim", List.of("cPsC 3720","MATH 3110"));
        assertEquals("Tim", tim.name);
        assertTrue(tim.courses.contains("CPSC 3720"));
        assertTrue(tim.courses.contains("MATH 3110"));
    }

    @Test
    void availabilityController_addAndRemove() {
        StudyBuddyApp.Student alice = repo.getStudent(aliceId);
        int before = alice.availability.size();

        availCtl.addAvailability(aliceId,
                new StudyBuddyApp.TimeSlot(DayOfWeek.WEDNESDAY,
                        LocalTime.of(10,0), LocalTime.of(11,0)));
        assertEquals(before + 1, alice.availability.size());

        assertTrue(availCtl.removeAvailability(aliceId, before)); // remove the one we just added
        assertEquals(before, alice.availability.size());
    }

    // ---- Repository lookups ----

    @Test
    void classmatesInCourse_excludesSelfAndFiltersByCourse() {
        List<StudyBuddyApp.Student> cls = repo.classmatesInCourse(aliceId, "CPSC 3720");
        assertEquals(1, cls.size());
        assertEquals("Bob", cls.get(0).name);

        List<StudyBuddyApp.Student> math = repo.classmatesInCourse(aliceId, "MATH 3110");
// Since the method returns classmates in that course regardless of the caller’s enrollment:
        assertEquals(2, math.size());
        assertTrue(math.stream().anyMatch(s -> s.name.equals("Jon")));
        assertTrue(math.stream().anyMatch(s -> s.name.equals("Mary")));
    }

    // ---- Sessions ----

    @Test
    void session_createJoinConfirm_flow() {
        StudyBuddyApp.StudySession s = sessionCtl.create(
                "CPSC 3720",
                new StudyBuddyApp.TimeSlot(DayOfWeek.MONDAY, LocalTime.of(15,0), LocalTime.of(16,0)),
                List.of(aliceId)
        );
        assertNotNull(repo.getSession(s.id));
        assertTrue(s.isParticipant(aliceId));

        sessionCtl.join(s.id, bobId);
        assertTrue(s.isParticipant(bobId));

        sessionCtl.confirm(s.id, aliceId);
        assertFalse(s.isFullyConfirmed());
        sessionCtl.confirm(s.id, bobId);
        assertTrue(s.isFullyConfirmed());
    }

    @Test
    void session_searchByCourseAndByName() {
        StudyBuddyApp.StudySession s1 = sessionCtl.create(
                "CPSC 3720",
                new StudyBuddyApp.TimeSlot(DayOfWeek.MONDAY, LocalTime.of(15,0), LocalTime.of(16,0)),
                List.of(aliceId, bobId));
        StudyBuddyApp.StudySession s2 = sessionCtl.create(
                "MATH 3110",
                new StudyBuddyApp.TimeSlot(DayOfWeek.TUESDAY, LocalTime.of(10,30), LocalTime.of(11,30)),
                List.of(jonId, maryId));

        var byCourse = sessionCtl.searchByCourse("cpsc 3720");
        assertEquals(1, byCourse.size());
        assertEquals(s1.id, byCourse.get(0).id);

        var byName = sessionCtl.searchByStudentName("ar"); // matches Mary
        assertEquals(1, byName.size());
        assertEquals(s2.id, byName.get(0).id);
    }

    @Test
    void suggestMatches_findsOverlapWindows() {
        Map<StudyBuddyApp.Student, List<StudyBuddyApp.TimeSlot>> matches =
                sessionCtl.suggestMatches(aliceId, "CPSC 3720");

        assertEquals(1, matches.size());
        StudyBuddyApp.Student peer = matches.keySet().iterator().next();
        assertEquals("Bob", peer.name);

        boolean hasExpected = matches.get(peer).stream().anyMatch(ts ->
                ts.day == DayOfWeek.MONDAY &&
                        ts.start.equals(LocalTime.of(15,0)) &&
                        ts.end.equals(LocalTime.of(16,0)));
        assertTrue(hasExpected);
    }
}
