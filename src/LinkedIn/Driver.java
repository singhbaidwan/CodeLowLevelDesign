package LinkedIn;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;

/*
 * Single-file, thread-safe LinkedIn-like mini-system.
 * - Uses ConcurrentHashMap, CopyOnWriteArrayList, ReentrantLock
 * - Implements missing methods from user's model
 * - Small demo in main() illustrating concurrent operations
 *
 * Compile: javac Driver.java
 * Run:     java Driver
 */

/* ------------------------- Basic Types & Address -------------------------- */

class Address {
    private int zipCode;
    private String streetAddress;
    private String city;
    private String state;
    private String country;

    public Address() {}

    public Address(int zipCode, String streetAddress, String city, String state, String country) {
        this.zipCode = zipCode;
        this.streetAddress = streetAddress;
        this.city = city;
        this.state = state;
        this.country = country;
    }

    public String getCity(){ return city; }
    public void setCity(String c){ this.city = c; }
    public void setCountry(String c){ this.country = c; }
    public String toString(){ return streetAddress + ", " + city + ", " + state + ", " + country + " - " + zipCode; }
}

/* ----------------------------- Enums ------------------------------------- */

enum ConnectionInviteStatus { PENDING, ACCEPTED, IGNORED }
enum JobStatus { OPEN, ON_HOLD, CLOSED }

/* ---------------------------- Abstract Person ---------------------------- */

abstract class Person {
    protected String name;
    protected Address address;
    protected String phone;
    protected String email;
    protected String password;

    public String getName() { return name; }
}

/* ------------------------------ DataStore --------------------------------
   Global thread-safe repositories for Users, Posts, Groups, Pages, Jobs etc.
----------------------------------------------------------------------------*/

class DataStore {
    static final ConcurrentMap<Integer, User> users = new ConcurrentHashMap<>();
    static final ConcurrentMap<Integer, Post> posts = new ConcurrentHashMap<>();
    static final ConcurrentMap<Integer, Comment> comments = new ConcurrentHashMap<>();
    static final ConcurrentMap<Integer, CompanyPage> pages = new ConcurrentHashMap<>();
    static final ConcurrentMap<Integer, Group> groups = new ConcurrentHashMap<>();
    static final ConcurrentMap<Integer, Message> messages = new ConcurrentHashMap<>();
    static final ConcurrentMap<Integer, Job> jobs = new ConcurrentHashMap<>();
    static final ConcurrentMap<Integer, ConnectionInvitation> invitations = new ConcurrentHashMap<>();

    static final AtomicInteger USER_ID = new AtomicInteger(1000);
    static final AtomicInteger POST_ID = new AtomicInteger(2000);
    static final AtomicInteger COMMENT_ID = new AtomicInteger(3000);
    static final AtomicInteger PAGE_ID = new AtomicInteger(4000);
    static final AtomicInteger GROUP_ID = new AtomicInteger(5000);
    static final AtomicInteger MESSAGE_ID = new AtomicInteger(6000);
    static final AtomicInteger JOB_ID = new AtomicInteger(7000);
    static final AtomicInteger INVITE_ID = new AtomicInteger(8000);
}

/* ------------------------------- Recommendation, Achievement, Analytics --*/

class Recommendation {
    private final int userId;
    private final Date createdOn;
    private final String description;
    private volatile boolean isAccepted;

    public Recommendation(int userId, String description) {
        this.userId = userId;
        this.description = description;
        this.createdOn = new Date();
        this.isAccepted = false;
    }

    public int getUserId(){ return userId; }
    public void accept(){ isAccepted = true; }
}

class Achievement {
    private final String title;
    private final Date dateAwarded;
    private final String description;

    public Achievement(String title, Date dateAwarded, String description) {
        this.title = title;
        this.dateAwarded = dateAwarded;
        this.description = description;
    }
}

class Analytics {
    private final AtomicInteger searchAppearances = new AtomicInteger(0);
    private final AtomicInteger profileViews = new AtomicInteger(0);
    private final AtomicInteger postImpressions = new AtomicInteger(0);
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    public void incSearchAppearances(){ searchAppearances.incrementAndGet(); }
    public void incProfileViews(){ profileViews.incrementAndGet(); }
    public void incPostImpressions(){ postImpressions.incrementAndGet(); }
    public void incTotalConnections(){ totalConnections.incrementAndGet(); }

    public String toString(){
        return "searchAppearances=" + searchAppearances.get() + ", profileViews=" + profileViews.get() +
                ", postImpressions=" + postImpressions.get() + ", totalConnections=" + totalConnections.get();
    }
}

/* -------------------------------- Job ------------------------------------ */

class Job {
    private final int jobId;
    private final String jobTitle;
    private final Date dateOfPosting;
    private final String description;
    private final CompanyPage company;
    private final String employmentType;
    private final Address location;
    private volatile JobStatus status;

    public Job(String jobTitle, String description, CompanyPage company, String employmentType, Address location) {
        this.jobId = DataStore.JOB_ID.getAndIncrement();
        this.jobTitle = jobTitle;
        this.description = description;
        this.company = company;
        this.employmentType = employmentType;
        this.location = location;
        this.dateOfPosting = new Date();
        this.status = JobStatus.OPEN;
        DataStore.jobs.put(jobId, this);
    }

    public int getJobId(){ return jobId; }
    public JobStatus getStatus(){ return status; }
    public void setStatus(JobStatus s){ status = s; }
    public String getJobTitle(){ return jobTitle; }
}

/* ------------------------------ CompanyPage ------------------------------- */

class CompanyPage {
    private final int pageId;
    private final String name;
    private final String description;
    private final String type;
    private final int companySize;
    private final User createdBy;
    private final CopyOnWriteArrayList<Job> jobs = new CopyOnWriteArrayList<>();
    private final Lock lock = new ReentrantLock();

    public CompanyPage(String name, String description, String type, int companySize, User createdBy) {
        this.pageId = DataStore.PAGE_ID.getAndIncrement();
        this.name = name;
        this.description = description;
        this.type = type;
        this.companySize = companySize;
        this.createdBy = createdBy;
        DataStore.pages.put(pageId, this);
    }

    public int getPageId(){ return pageId; }
    public String getName(){ return name; }

    // thread-safe creation of a job posting related to this page
    public boolean createJobPosting(String jobTitle, String description, String employmentType, Address location) {
        lock.lock();
        try {
            Job job = new Job(jobTitle, description, this, employmentType, location);
            jobs.add(job);
            return true;
        } finally {
            lock.unlock();
        }
    }

    // delete job posting (by id)
    public boolean deleteJobPosting(Job job) {
        if (job == null) return false;
        lock.lock();
        try {
            boolean removed = jobs.remove(job);
            if (removed) {
                job.setStatus(JobStatus.CLOSED);
                DataStore.jobs.remove(job.getJobId());
            }
            return removed;
        } finally {
            lock.unlock();
        }
    }

    public List<Job> getJobs(){ return new ArrayList<>(jobs); }
}

/* -------------------------------- Group ---------------------------------- */

class Group {
    private final int groupId;
    private String name;
    private String description;
    private final AtomicInteger totalMembers = new AtomicInteger(0);
    private final CopyOnWriteArrayList<User> members = new CopyOnWriteArrayList<>();
    private final Lock lock = new ReentrantLock();

    public Group(String name, String description) {
        this.groupId = DataStore.GROUP_ID.getAndIncrement();
        this.name = name;
        this.description = description;
        DataStore.groups.put(groupId, this);
    }

    public int getGroupId(){ return groupId; }
    public String getName(){ return name; }

    public boolean updateDescription(String d) {
        lock.lock();
        try {
            this.description = d;
            return true;
        } finally { lock.unlock(); }
    }

    public boolean addMember(User u) {
        if (u == null) return false;
        boolean added = members.addIfAbsent(u); // CopyOnWriteArrayList has no addIfAbsent, emulate:
        if (!members.contains(u)) {
            members.add(u);
            totalMembers.incrementAndGet();
            return true;
        }
        return false;
    }

    public boolean removeMember(User u) {
        boolean removed = members.remove(u);
        if (removed) totalMembers.decrementAndGet();
        return removed;
    }

    public List<User> getMembers(){ return new ArrayList<>(members); }
}

/* -------------------------------- Post ----------------------------------- */

class Post {
    private final int postId;
    private final User postOwner;
    private volatile String text;
    private final CopyOnWriteArrayList<byte[]> media = new CopyOnWriteArrayList<>();
    private final AtomicInteger totalReacts = new AtomicInteger(0);
    private final AtomicInteger totalShares = new AtomicInteger(0);
    private final CopyOnWriteArrayList<Comment> comments = new CopyOnWriteArrayList<>();
    private final Lock lock = new ReentrantLock();

    public Post(User owner, String text, List<byte[]> mediaList) {
        this.postId = DataStore.POST_ID.getAndIncrement();
        this.postOwner = owner;
        this.text = text;
        if (mediaList != null) this.media.addAll(mediaList);
        DataStore.posts.put(postId, this);
    }

    public int getPostId(){ return postId; }
    public User getOwner(){ return postOwner; }

    public boolean updateText(String newText) {
        lock.lock();
        try {
            this.text = newText;
            return true;
        } finally { lock.unlock(); }
    }

    public boolean addComment(Comment c) {
        if (c == null) return false;
        comments.add(c);
        return true;
    }

    public boolean removeComment(Comment c) {
        if (c == null) return false;
        return comments.remove(c);
    }

    public void react() { totalReacts.incrementAndGet(); }
    public int getReacts(){ return totalReacts.get(); }
    public List<Comment> getComments(){ return new ArrayList<>(comments); }
}

/* ------------------------------- Comment --------------------------------- */

class Comment {
    private final int commentId;
    private final User commentOwner;
    private volatile String text;
    private final AtomicInteger totalReacts = new AtomicInteger(0);
    private final CopyOnWriteArrayList<Comment> comments = new CopyOnWriteArrayList<>();
    private final Lock lock = new ReentrantLock();

    public Comment(User owner, String text) {
        this.commentId = DataStore.COMMENT_ID.getAndIncrement();
        this.commentOwner = owner;
        this.text = text;
        DataStore.comments.put(commentId, this);
    }

    public int getCommentId(){ return commentId; }
    public boolean updateText(String newText) {
        lock.lock();
        try {
            this.text = newText;
            return true;
        } finally { lock.unlock(); }
    }

    public boolean addReply(Comment c) {
        if (c == null) return false;
        comments.add(c);
        return true;
    }
}

/* -------------------------------- Message -------------------------------- */

class Message {
    private final int messageId;
    private final User sender;
    private final CopyOnWriteArrayList<User> recipients = new CopyOnWriteArrayList<>();
    private final String text;
    private final CopyOnWriteArrayList<byte[]> media = new CopyOnWriteArrayList<>();
    private final Lock lock = new ReentrantLock();

    public Message(User sender, List<User> recipientsList, String text, List<byte[]> mediaList) {
        this.messageId = DataStore.MESSAGE_ID.getAndIncrement();
        this.sender = sender;
        if (recipientsList != null) this.recipients.addAll(recipientsList);
        this.text = text;
        if (mediaList != null) this.media.addAll(mediaList);
        DataStore.messages.put(messageId, this);
    }

    public int getMessageId(){ return messageId; }
    public boolean addRecipients(List<User> recipientsToAdd) {
        if (recipientsToAdd == null || recipientsToAdd.isEmpty()) return false;
        lock.lock();
        try {
            for (User u : recipientsToAdd) {
                if (u != null && !recipients.contains(u)) recipients.add(u);
            }
            return true;
        } finally { lock.unlock(); }
    }

    public List<User> getRecipients(){ return new ArrayList<>(recipients); }
}

/* ------------------------- Connection Invitation -------------------------- */

class ConnectionInvitation {
    private final int inviteId;
    public final User sender;
    public final User recipient;
    private final Date dateCreated;
    private volatile ConnectionInviteStatus status;
    private final Lock lock = new ReentrantLock();

    public ConnectionInvitation(User sender, User recipient) {
        this.inviteId = DataStore.INVITE_ID.getAndIncrement();
        this.sender = sender;
        this.recipient = recipient;
        this.dateCreated = new Date();
        this.status = ConnectionInviteStatus.PENDING;
        DataStore.invitations.put(inviteId, this);
    }

    public int getInviteId(){ return inviteId; }
    public ConnectionInviteStatus getStatus(){ return status; }

    public boolean acceptConnection() {
        lock.lock();
        try {
            if (status != ConnectionInviteStatus.PENDING) return false;
            status = ConnectionInviteStatus.ACCEPTED;
            // add to connections list of both users
            sender._addConnection(recipient);
            recipient._addConnection(sender);
            return true;
        } finally { lock.unlock(); }
    }

    public boolean rejectConnection() {
        lock.lock();
        try {
            if (status != ConnectionInviteStatus.PENDING) return false;
            status = ConnectionInviteStatus.IGNORED;
            return true;
        } finally { lock.unlock(); }
    }
}

/* --------------------------------- User ---------------------------------- */

class User extends Person {
    private final int userId;
    private final Date dateOfJoining;
    private final Profile profile = new Profile();
    private final CopyOnWriteArrayList<User> connections = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<User> followsUsers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CompanyPage> followCompanies = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Group> joinedGroups = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CompanyPage> createdPages = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Group> createdGroups = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Post> posts = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Recommendation> recommendations = new CopyOnWriteArrayList<>();
    private final Analytics analytics = new Analytics();
    private final Lock lock = new ReentrantLock();

    public User(String name, Address address, String phone, String email, String password) {
        this.userId = DataStore.USER_ID.getAndIncrement();
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.password = password;
        this.dateOfJoining = new Date();
        DataStore.users.put(userId, this);
    }

    public int getUserId(){ return userId; }
    public String toString() { return "User(" + userId + "," + name + ")"; }

    /* internal helper used when an invitation is accepted to avoid deadlocks */
    void _addConnection(User other) {
        if (other == null) return;
        if (!connections.contains(other)) connections.add(other);
        analytics.incTotalConnections();
    }

    /* Search by name substring; increments analytics */
    public boolean search(User user) {
        if (user == null) return false;
        // search in datastore; simple substring match on username
        analytics.incSearchAppearances();
        for (User u : DataStore.users.values()) {
            if (u.name != null && u.name.toLowerCase().contains(user.name.toLowerCase())) {
                // found at least one — in real app return results; here return true
                return true;
            }
        }
        return false;
    }

    /* Send a message to recipients (thread-safe). Returns true on success. */
    public boolean sendMessage(Message message) {
        if (message == null) return false;
        // store the message (already done in Message constructor)
        // simulate delivering message: add to recipients' notifications (not modeled)
        return true;
    }

    /* Send an invite — creates a ConnectionInvitation and stores it */
    public boolean sendInvite(ConnectionInvitation invite) {
        if (invite == null) return false;
        // invite already placed into DataStore in constructor
        return true;
    }

    /* Cancel an invite by changing its status (if sender matches) */
    public boolean cancelInvite(ConnectionInvitation invite) {
        if (invite == null) return false;
        // only sender can cancel; mark as ignored
        if (invite.sender != this) return false;
        return invite.rejectConnection();
    }

    public CompanyPage createPage(CompanyPage page) {
        if (page == null) return null;
        createdPages.add(page);
        return page;
    }

    public boolean deletePage(CompanyPage page) {
        if (page == null) return false;
        boolean removed = createdPages.remove(page);
        if (removed) DataStore.pages.remove(page.getPageId());
        return removed;
    }

    public Group createGroup(Group group) {
        if (group == null) return null;
        createdGroups.add(group);
        return group;
    }

    public boolean deleteGroup(Group group) {
        if (group == null) return false;
        boolean removed = createdGroups.remove(group);
        if (removed) DataStore.groups.remove(group.getGroupId());
        return removed;
    }

    public Post createPost(String text, List<byte[]> media) {
        Post p = new Post(this, text, media);
        posts.add(p);
        return p;
    }

    public boolean deletePost(Post post) {
        if (post == null) return false;
        boolean removed = posts.remove(post);
        if (removed) DataStore.posts.remove(post.getPostId());
        return removed;
    }

    public Comment createComment(String text) {
        Comment c = new Comment(this, text);
        return c;
    }

    public boolean deleteComment(Comment comment) {
        if (comment == null) return false;
        boolean removed = DataStore.comments.remove(comment.getCommentId(), comment);
        return removed;
    }

    public boolean react(Post post) {
        if (post == null) return false;
        post.react();
        return true;
    }

    public boolean requestRecommendation(User user) {
        if (user == null) return false;
        Recommendation r = new Recommendation(this.getUserId(), "Please recommend me");
        user.recommendations.add(r);
        return true;
    }

    public boolean acceptRecommendation(User user) {
        if (user == null) return false;
        // find first recommendation for this user and accept
        for (Recommendation r : recommendations) {
            if (r.getUserId() == user.getUserId()) {
                r.accept();
                return true;
            }
        }
        return false;
    }

    public boolean applyForJob(Job job) {
        if (job == null) return false;
        if (job.getStatus() != JobStatus.OPEN) return false;
        // in a real system we'd create an Application object; here we accept
        return true;
    }

    /* follows user */
    public boolean followUser(User other) {
        if (other == null) return false;
        if (!followsUsers.contains(other)) followsUsers.add(other);
        return true;
    }

    /* follow company page */
    public boolean followCompany(CompanyPage page) {
        if (page == null) return false;
        if (!followCompanies.contains(page)) followCompanies.add(page);
        return true;
    }

    /* join a group */
    public boolean joinGroup(Group g) {
        if (g == null) return false;
        boolean ok = g.addMember(this);
        if (ok) joinedGroups.add(g);
        return ok;
    }

    /* send a connection invite to another user */
    public ConnectionInvitation sendConnectionInvite(User to) {
        if (to == null) return null;
        ConnectionInvitation inv = new ConnectionInvitation(this, to);
        return inv;
    }

    /* send a message to list of users */
    public Message composeAndSendMessage(List<User> to, String text) {
        Message m = new Message(this, to, text, null);
        // in real app we'd enqueue notifications for recipients
        return m;
    }

    public Analytics getAnalytics(){ return analytics; }
}

/* ------------------------------- Profile --------------------------------- */

class Profile {
    // placeholder for more fields (experience, headline, summary)
}

/* --------------------------------- Demo ---------------------------------- */

public class Driver {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Starting LinkedIn-like Simulation (thread-safe) ===");

        // Create few users
        User alice = new User("Alice", new Address(400001, "MG Road", "Bengaluru", "KA", "India"), "1111111111", "alice@x.com", "pw");
        User bob = new User("Bob", new Address(110001, "Connaught Pl", "Delhi", "DL", "India"), "2222222222", "bob@x.com", "pw");
        User carol = new User("Carol", new Address(700001, "Park St", "Kolkata", "WB", "India"), "3333333333", "carol@x.com", "pw");
        System.out.println("Created users: " + alice + ", " + bob + ", " + carol);

        // Alice creates a company page and posts a job
        CompanyPage acme = new CompanyPage("Acme Corp", "We build things", "Tech", 200, alice);
        alice.createPage(acme);
        acme.createJobPosting("SDE I", "Java engineer", "Full-time", new Address(560001, "Some St", "Bengaluru", "KA", "India"));
        System.out.println("Company page & job created: " + acme.getName() + " jobs: " + acme.getJobs().size());

        // Bob follows Acme and applies for job
        bob.followCompany(acme);
        Job job = acme.getJobs().get(0);
        System.out.println("Bob applying for job " + job.getJobTitle() + " status: " + job.getStatus());
        boolean applied = bob.applyForJob(job);
        System.out.println("Bob applied: " + applied);

        // Alice sends connection invites to Bob & Carol concurrently
        ExecutorService ex = Executors.newFixedThreadPool(4);
        ex.submit(() -> {
            ConnectionInvitation inv = alice.sendConnectionInvite(bob);
            System.out.println("Alice sent invite to Bob: " + inv.getInviteId());
        });
        ex.submit(() -> {
            ConnectionInvitation inv = alice.sendConnectionInvite(carol);
            System.out.println("Alice sent invite to Carol: " + inv.getInviteId());
        });

        // Carol accepts invite concurrently
        ex.submit(() -> {
            // find invite from DataStore to Carol
            for (ConnectionInvitation ci : DataStore.invitations.values()) {
                if (ci != null && ci.recipient == carol) {
                    boolean ok = ci.acceptConnection();
                    System.out.println("Carol accepted invite " + ci.getInviteId() + ": " + ok);
                }
            }
        });

        // Posting & commenting concurrently
        Post alicePost = alice.createPost("Hello world! My first post.", null);
        ex.submit(() -> {
            Comment c = bob.createComment("Nice post, Alice!");
            alicePost.addComment(c);
            System.out.println("Bob commented on Alice's post. Comment id: " + c.getCommentId());
        });
        ex.submit(() -> {
            Comment c2 = carol.createComment("Welcome!");
            alicePost.addComment(c2);
            System.out.println("Carol commented on Alice's post. Comment id: " + c2.getCommentId());
        });

        // Reacts concurrently
        ex.submit(() -> alice.react(alicePost));
        ex.submit(() -> bob.react(alicePost));
        ex.submit(() -> carol.react(alicePost));

        // Message sending concurrently
        ex.submit(() -> {
            Message m = bob.composeAndSendMessage(List.of(alice, carol), "Hey team, check this out!");
            System.out.println("Bob sent message id: " + m.getMessageId() + " to " + m.getRecipients().size() + " recipients");
        });

        ex.shutdown();
        ex.awaitTermination(3, TimeUnit.SECONDS);

        System.out.println("Alice post reactions: " + alicePost.getReacts());
        System.out.println("Alice post comments: " + alicePost.getComments().size());

        System.out.println("Alice analytics: " + alice.getAnalytics());
        System.out.println("Done.");
    }
}

