package MeetingScheduler;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

enum RSVPStatus{
    ACCEPTED,
    REJECTED,
    PENDING
}
class Interval {
    private Date startTime;
    private Date endTime;

    public Interval(Date startTime, Date endTime) {
        if (startTime == null || endTime == null) throw new IllegalArgumentException("Start and end cannot be null");
        if (!startTime.before(endTime)) throw new IllegalArgumentException("startTime must be before endTime");
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public boolean overlaps(Interval other){
        return !(this.endTime.compareTo(other.startTime) <= 0 || this.startTime.compareTo(other.endTime) >= 0);
    }

    @Override
    public String toString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return String.format("[%s -> %s]", sdf.format(startTime), sdf.format(endTime));
    }
}


class MeetingRoom{
    private int id;
    private int capacity;
    private List<Interval> bookedIntervals = new ArrayList<>();

    public MeetingRoom(int id, int capacity) {
        this.id = id;
        this.capacity = capacity;
    }
    public synchronized boolean isAvailableFor(Interval interval,int requiredCapacity){
        if(requiredCapacity > capacity) return false;
        for (Interval booked : bookedIntervals) {
            if (booked.overlaps(interval)) return false;
        }
        return true;
    }
    public synchronized void bookInterval(Interval interval) {
        bookedIntervals.add(interval);
    }
    public synchronized void releaseInterval(Interval interval) {
        // remove any overlapping interval equal to start/end
        bookedIntervals.removeIf(i -> i.getStartTime().equals(interval.getStartTime()) && i.getEndTime().equals(interval.getEndTime()));
    }

    public int getCapacity() {
        return capacity;
    }

    public int getId() {
        return id;
    }
    @Override
    public String toString() {
        return String.format("Room-%d (cap=%d)", id, capacity);
    }
}

class Meeting {
    private static final AtomicInteger ID_GEN = new AtomicInteger(1);
    private int id;
    private Map<User, RSVPStatus> participantStatus = new LinkedHashMap<>();
    private Interval interval;
    private MeetingRoom room;
    private String subject;
    private User organizer;

    public Meeting(User organizer, List<User> participants, Interval interval, MeetingRoom room, String subject) {
        this.id = ID_GEN.getAndIncrement();
        this.interval = interval;
        this.room = room;
        this.subject = subject;
        this.organizer = organizer;
    }

    public void addParticipants(List<User> participants) {
        for (User u : participants) participantStatus.putIfAbsent(u, RSVPStatus.PENDING);
    }
    public void updateParticipantStatus(User user, RSVPStatus status) {
        if (!participantStatus.containsKey(user)) return;
        participantStatus.put(user, status);
    }
    public List<User> getAcceptedParticipants() {
        List<User> res = new ArrayList<>();
        for (Map.Entry<User, RSVPStatus> e : participantStatus.entrySet()) if (e.getValue() == RSVPStatus.ACCEPTED) res.add(e.getKey());
        return res;
    }
    public List<User> getPendingParticipants() {
        List<User> res = new ArrayList<>();
        for (Map.Entry<User, RSVPStatus> e : participantStatus.entrySet()) if (e.getValue() == RSVPStatus.PENDING) res.add(e.getKey());
        return res;
    }

    public List<User> getRejectedParticipants() {
        List<User> res = new ArrayList<>();
        for (Map.Entry<User, RSVPStatus> e : participantStatus.entrySet()) if (e.getValue() == RSVPStatus.REJECTED) res.add(e.getKey());
        return res;
    }

    public int getId() {
        return id;
    }

    public Interval getInterval() {
        return interval;
    }

    public MeetingRoom getRoom() {
        return room;
    }

    public String getSubject() {
        return subject;
    }

    public User getOrganizer() {
        return organizer;
    }


    @Override
    public String toString() {
        return String.format("Meeting-%d '%s' by %s %s in %s", id, subject, organizer.getName(), interval, room);
    }

}


// Simple notification service (prints to console)
class Notification {
    private static final AtomicInteger NOTIF_ID = new AtomicInteger(1);

    public void sendInvite(User user, Meeting meeting) {
        int id = NOTIF_ID.getAndIncrement();
        System.out.printf("[Notif-%d] Invite to %s -> %s (%s)\n", id, meeting.getSubject(), user.getEmail(), meeting.getInterval());
    }

    public void sendCancelNotification(User user, Meeting meeting) {
        int id = NOTIF_ID.getAndIncrement();
        System.out.printf("[Notif-%d] Cancellation of %s -> %s\n", id, meeting.getSubject(), user.getEmail());
    }
}

class User{
    private String name;
    private String email;
    private UserCalendar calendar;

    public User(String userName, String email) {
        this.name = userName;
        this.email = email;
        this.calendar = new UserCalendar();
    }
    public void removeInvitation(Meeting meeting, RSVPStatus response){
        meeting.updateParticipantStatus(this, response);
        System.out.printf("%s responded %s to meeting '%s'\n", name, response, meeting.getSubject());

    }
    public List<Meeting> viewMeetings() { return calendar.getMeetings(); }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() { return Objects.hash(email); }

    @Override
    public String toString() { return String.format("%s <%s>", this.name, email); }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public UserCalendar getCalendar() {
        return calendar;
    }
}

class UserCalendar{
    private List<Meeting> meetings = new ArrayList<>();

    public synchronized void addMeeting(Meeting meeting){
        meetings.add(meeting);
    }
    public synchronized  void removeMeeting(Meeting meeting){
        meetings.remove(meeting);
    }
    public List<Meeting> getMeetings() {
        return meetings;
    }
}

class MeetingScheduler{
    private User organizer;
    private UserCalendar calendar;
    private List<MeetingRoom> rooms;
    private Notification notification = new Notification();
    private Random rand = new Random();

    public MeetingScheduler(User organizer, List<MeetingRoom> rooms) {
        this.organizer = organizer;
        this.calendar = organizer.getCalendar();
        this.rooms = new ArrayList<>(rooms);
    }
    public MeetingRoom checkRoomsAvailability(int capacity, Interval interval) {
        // simple first-fit
        for (MeetingRoom room : rooms) {
            if (room.isAvailableFor(interval, capacity)) return room;
        }
        return null;
    }

    public boolean bookRoom(MeetingRoom room, Interval interval) {
        if (room == null) return false;
        room.bookInterval(interval);
        System.out.println("Booked " + room + " for " + interval);
        return true;
    }

    public boolean releaseRoom(MeetingRoom room, Interval interval) {
        if (room == null) return false;
        room.releaseInterval(interval);
        System.out.println("Released " + room + " for " + interval);
        return true;
    }



    public Meeting scheduleMeeting(List<User> users, Interval interval, String subject) {
        int requiredCapacity = users.size();
        MeetingRoom room = checkRoomsAvailability(requiredCapacity, interval);
        if (room == null) {
            System.out.println("No rooms available for " + subject + " at " + interval);
            return null;
        }
        // book
        bookRoom(room, interval);
        // Create meeting
        Meeting meeting = new Meeting(organizer, users, interval, room, subject);
        for (User u : users) {
            u.getCalendar().addMeeting(meeting);
            notification.sendInvite(u, meeting);
        }
        organizer.getCalendar().addMeeting(meeting);
// Simulate RSVP responses for demo purposes (random accept/reject for non-organizers)
        for (User u : users) {
            if (u.equals(organizer)) continue;
            RSVPStatus resp = (rand.nextInt(100) < 75) ? RSVPStatus.ACCEPTED : RSVPStatus.REJECTED; // 75% accept chance
            meeting.updateParticipantStatus(u, resp);
            System.out.printf("Simulated RSVP: %s -> %s\n", u.getName(), resp);
        }

        // If too many rejected such that room capacity not utilized, we still keep meeting; this is a policy choice.
        System.out.println("Scheduled: " + meeting);
        return meeting;
    }

    public boolean cancelMeeting(Meeting meeting) {
        if (meeting == null) return false;
        for (User u : meeting.getAcceptedParticipants()) {
            u.getCalendar().removeMeeting(meeting);
            notification.sendCancelNotification(u, meeting);
        }
        for (User u : meeting.getPendingParticipants()) {
            u.getCalendar().removeMeeting(meeting);
            notification.sendCancelNotification(u, meeting);
        }
        for (User u : meeting.getRejectedParticipants()) {
            u.getCalendar().removeMeeting(meeting);
            notification.sendCancelNotification(u, meeting);
        }

        // release room
        releaseRoom(meeting.getRoom(), meeting.getInterval());
        System.out.println("Cancelled meeting: " + meeting.getSubject());
        return true;

    }
}

public class Driver {

    private static void header(String title) {
        System.out.println("\n==============================");
        System.out.println("▶ " + title);
        System.out.println("==============================\n");
    }
    private static void arrow(String msg) { System.out.println("→ " + msg); }
    private static Interval getInterval(int year, int month, int day, int startHour, int startMin, int endHour, int endMin) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(year, month, day, startHour, startMin, 0);
        Date start = cal.getTime();
        cal.set(year, month, day, endHour, endMin, 0);
        Date end = cal.getTime();
        return new Interval(start, end);
    }
    public static void main(String[] args){
        MeetingRoom roomA = new MeetingRoom(1, 4);
        MeetingRoom roomB = new MeetingRoom(2, 8);
        List<MeetingRoom> rooms = Arrays.asList(roomA, roomB);
        User alice = new User("Alice", "alice@email.com");
        User bob = new User("Bob", "bob@email.com");
        User charlie = new User("Charlie", "charlie@email.com");
        List<User> participants = Arrays.asList(alice, bob, charlie);
        MeetingScheduler scheduler = new MeetingScheduler(alice, rooms);

        // Scenario 1: All accept (simulated randomly)
        header("Scenario 1: Schedule a Meeting (Random Accept/Reject)");
        arrow("Scheduling meeting \"Design Review\" for Alice, Bob, Charlie...");
        Interval interval1 = getInterval(2025, java.util.Calendar.JULY, 10, 10, 0, 11, 0);
        Meeting meeting1 = scheduler.scheduleMeeting(participants, interval1, "Design Review");

        // Scenario 2: Schedule another meeting (possibly some will reject)
        header("Scenario 2: Schedule Another Meeting (Random Accept/Reject)");
        arrow("Scheduling meeting \"Sprint Planning\" for Alice, Bob, Charlie...");
        Interval interval2 = getInterval(2025, java.util.Calendar.JULY, 10, 12, 0, 13, 0);
        Meeting meeting2 = scheduler.scheduleMeeting(participants, interval2, "Sprint Planning");


        // Scenario 3: Cancel a meeting
        header("Scenario 3: Cancel Meeting");
        arrow("Cancelling meeting \"Design Review\"...");
        if (meeting1 != null) {
            scheduler.cancelMeeting(meeting1);
        }

        // Show each user's calendar
        header("Final calendars for each user");
        for (User u : Arrays.asList(alice, bob, charlie)) {
            System.out.println(u.getName() + "'s meetings:");
            for (Meeting m : u.viewMeetings()) System.out.println(" - " + m.getSubject() + " (" + m.getInterval() + ") in " + m.getRoom());
            System.out.println();
        }


    }
}
