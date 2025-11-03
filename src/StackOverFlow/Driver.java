package StackOverFlow;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/**
 * Thread-safe Stack Overflow-like simulation with instance-based design.
 */
public class Driver {

    // -----------------------------
    // Enums
    // -----------------------------
    public enum QuestionStatus { ACTIVE, CLOSED, FLAGGED, BOUNTIED }
    public enum UserStatus { ACTIVE, BLOCKED }

    // -----------------------------
    // ID generators
    // -----------------------------
    private final AtomicInteger QUESTION_ID_GEN = new AtomicInteger(1);
    private final AtomicInteger ANSWER_ID_GEN = new AtomicInteger(1);
    private final AtomicInteger COMMENT_ID_GEN = new AtomicInteger(1);
    private final AtomicInteger NOTIF_ID_GEN = new AtomicInteger(1);

    // -----------------------------
    // User base class
    // -----------------------------
    public class User {
        protected final ReadWriteLock lock = new ReentrantReadWriteLock();
        protected final AtomicInteger reputationPoints = new AtomicInteger(0);
        protected String username;
        protected final CopyOnWriteArrayList<Badge> badges = new CopyOnWriteArrayList<>();
        protected Question currentDraft;
        protected UserStatus status = UserStatus.ACTIVE;
        protected final CopyOnWriteArrayList<Question> authoredQuestions = new CopyOnWriteArrayList<>();

        public void setReputationPoints(int p) { reputationPoints.set(p); }
        public int getReputationPoints() { return reputationPoints.get(); }
        public void setName(String name) { this.username = name; }
        public String getName() { return username; }
        public void setStatus(UserStatus s) { this.status = s; }
        public UserStatus getStatus() { return status; }

        public Question create(String title, String body, List<Tag> tags) {
            if (status != UserStatus.ACTIVE) return null;
            Question q = new Question(title, body, this, tags);
            this.currentDraft = q;
            return q;
        }

        public boolean publishQuestion() {
            if (status != UserStatus.ACTIVE) return false;
            Question draft = this.currentDraft;
            if (draft == null) return false;
            draft.publish();
            authoredQuestions.add(draft);
            SearchCatalog.getInstance().indexQuestion(draft);
            this.currentDraft = null;
            return true;
        }

        public void addBounty(int value) {
            if (status != UserStatus.ACTIVE) return;
            if (currentDraft != null) {
                currentDraft.addBounty(new Bounty(value));
            }
        }

        public boolean addAnswer(Question question, Answer answer) {
            if (status != UserStatus.ACTIVE) return false;
            return question.addAnswer(answer);
        }

        public boolean createComment(Comment comment) {
            if (status != UserStatus.ACTIVE) return false;
            if (comment == null) return false;
            if (comment.getPostedOn() instanceof Question) {
                ((Question) comment.getPostedOn()).addComment(comment);
                return true;
            }
            if (comment.getPostedOn() instanceof Answer) {
                ((Answer) comment.getPostedOn()).addComment(comment);
                return true;
            }
            return false;
        }

        public boolean createTag(Tag tag) {
            if (tag == null) return false;
            TagRegistry.getInstance().register(tag);
            return true;
        }

        public void flagQuestion(Question question) { if (question != null) question.flag(); }
        public void flagAnswer(Answer answer) { if (answer != null) answer.flag(); }
        public void upvote(int id) {
            VoteRegistry.getInstance().voteUp(id);
        }
        public void downvote(int id) { VoteRegistry.getInstance().voteDown(id); }
        public void voteToCloseQuestion(Question question) { if (question != null) question.voteToClose(this); }
        public void voteToDeleteQuestion(Question question) { if (question != null) question.voteToDelete(this); }
        public void acceptAnswer(Answer answer) { if (answer != null) answer.accept(); }
        public void addReputation(int points) { reputationPoints.addAndGet(points); }
    }

    public class Admin extends User {
        public boolean blockUser(User user) { if (user == null) return false; user.setStatus(UserStatus.BLOCKED); return true; }
        public boolean unblockUser(User user) { if (user == null) return false; user.setStatus(UserStatus.ACTIVE); return true; }
        public void awardBadge(User user, Badge badge) {
            if (user == null || badge == null) return;
            user.badges.add(badge);
            new Notification("You were awarded: " + badge.name).sendNotification(user);
        }
    }

    public class Moderator extends User {
        public void closeQuestion(Question question) { if (question != null) question.setStatus(QuestionStatus.CLOSED); }
        public void reopenQuestion(Question question) { if (question != null) question.setStatus(QuestionStatus.ACTIVE); }
        public void deleteQuestion(Question question) { if (question != null) question.setStatus(QuestionStatus.CLOSED); }
        public void restoreQuestion(Question question) { if (question != null) question.setStatus(QuestionStatus.ACTIVE); }
        public void deleteAnswer(Answer answer) { if (answer != null) answer.markDeleted(); }
    }

    public class Guest {
        public User registerAccount() {
            User u = new User();
            u.setName("guest_" + UUID.randomUUID().toString().substring(0, 6));
            u.setReputationPoints(1);
            return u;
        }

        public List<Question> searchQuestions(String query) { return SearchCatalog.getInstance().searchByWords(query); }
        public void viewQuestion(Question q) { if (q != null) System.out.println("Viewing: " + q.title + " (" + q.getId() + ")"); }
    }

    public class Question {
        private final int id;
        private final String title;
        private String content;
        private final User createdBy;
        private QuestionStatus status;
        private final AtomicInteger upvotes = new AtomicInteger(0);
        private final AtomicInteger downvotes = new AtomicInteger(0);
        private final AtomicInteger voteCount = new AtomicInteger(0);
        private final Date creationDate;
        private Date modificationDate;
        private Bounty bounty;
        private final CopyOnWriteArrayList<Tag> tags = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<Comment> comments = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<Answer> answers = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<User> followers = new CopyOnWriteArrayList<>();
        private final ConcurrentMap<User, Boolean> closeVotes = new ConcurrentHashMap<>();
        private final ConcurrentMap<User, Boolean> deleteVotes = new ConcurrentHashMap<>();

        public Question(String title, String content, User author, List<Tag> tags) {
            this.id = QUESTION_ID_GEN.getAndIncrement();
            this.title = title;
            this.content = content;
            this.createdBy = author;
            this.status = QuestionStatus.ACTIVE;
            this.creationDate = new Date();
            this.modificationDate = new Date();
            if (tags != null) this.tags.addAll(tags);
        }

        public int getId() { return id; }
        public void publish() { this.status = QuestionStatus.ACTIVE; this.modificationDate = new Date(); }
        public void addComment(Comment comment) {
            this.comments.add(comment);
            this.modificationDate = new Date();
        }
        public boolean addAnswer(Answer a) { if (a == null) return false;
            a.setId(ANSWER_ID_GEN.getAndIncrement());
            answers.add(a);
            this.modificationDate = new Date();
            SearchCatalog.getInstance().indexQuestion(this); return true;
        }
        public void addBounty(Bounty b) {
            if (b == null) return;
            this.bounty = b;
            this.status = QuestionStatus.BOUNTIED;
        }
        public void notify(String msg) {
            for (User u : followers) new Notification(msg).sendNotification(u);
        }
        public void flag() {
            this.status = QuestionStatus.FLAGGED;
        }
        public void voteToClose(User u) {
            if (u == null) return;
            closeVotes.put(u, true);
            if (closeVotes.size() >= 3)
                this.status = QuestionStatus.CLOSED;
        }
        public void voteToDelete(User u) {
            if (u == null) return;
            deleteVotes.put(u, true);
            if (deleteVotes.size() >= 3)
                this.status = QuestionStatus.CLOSED;
        }
        public void setStatus(QuestionStatus s) { this.status = s; }
        public String toString() {
            return String.format("Question[%d] %s - %s (status=%s)", id, title, createdBy.getName(), status);
        }
    }

    public class Comment {
        private final int id;
        private final String content;
        private final AtomicInteger flagCount = new AtomicInteger(0);
        private final AtomicInteger upvotes = new AtomicInteger(0);
        private final Date creationDate;
        private final User postedBy;
        private final Object postedOn;

        public Comment(String content, User postedBy, Object postedOn) {
            this.id = COMMENT_ID_GEN.getAndIncrement();
            this.content = content;
            this.creationDate = new Date();
            this.postedBy = postedBy;
            this.postedOn = postedOn;
        }

        public Object getPostedOn() { return postedOn; }
        public int getId() { return id; }
    }

    public class Answer {
        private int id;
        private String content;
        private final AtomicInteger flagCount = new AtomicInteger(0);
        private final AtomicInteger voteCount = new AtomicInteger(0);
        private final AtomicInteger upvotes = new AtomicInteger(0);
        private final AtomicInteger downvotes = new AtomicInteger(0);
        private boolean isAccepted = false;
        private final Date creationTime = new Date();
        private final User postedBy;
        private final CopyOnWriteArrayList<Comment> comments = new CopyOnWriteArrayList<>();
        private boolean deleted = false;

        public Answer(String content, User postedBy) {
            this.content = content;
            this.postedBy = postedBy;
        }
        public void setId(int id) {
            this.id = id;
        }
        public int getId() { return id; }
        public void addComment(Comment comment) { comments.add(comment); }
        public void flag() { flagCount.incrementAndGet(); }
        public void accept() { isAccepted = true; }
        public void markDeleted() { deleted = true; }
    }

    public class Bounty {
        private final AtomicInteger reputationPoints = new AtomicInteger(0);
        private Date expiryDate;
        public Bounty(int reputation) {
            this.reputationPoints.set(reputation);
            this.expiryDate = new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7));
        }
        public boolean updateReputationPoints(int r) {
            this.reputationPoints.set(r);
            return true;
        }
    }
    public class Badge {
        public final String name;
        public final String description;
        public Badge(String n, String d) {
            this.name = n;
            this.description = d;
        }
    }
    public class Tag {
        public final String name;
        public final String description;
        public Tag(String n, String d) {
            this.name = n;
            this.description = d;
        }
    }

    public class Notification {
        private final int notificationId;
        private final Date createdOn = new Date();
        private final String content;
        public Notification(String content) {
            this.notificationId = NOTIF_ID_GEN.getAndIncrement();
            this.content = content;
        }
        public boolean sendNotification(User user) {
            System.out.printf("[Notification %d -> %s] %s\n", notificationId, user.getName(), content);
            return true;
        }
    }

    public interface Search {
        List<Question> searchByTags(String tagName);
        List<Question> searchByUsers(String username);
        List<Question> searchByWords(String word);
    }

    public static class SearchCatalog implements Search {
        private final ConcurrentHashMap<String, CopyOnWriteArrayList<Driver.Question>> questionsUsingTags = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, CopyOnWriteArrayList<Driver.Question>> questionsUsingUsers = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, CopyOnWriteArrayList<Driver.Question>> questionsUsingWords = new ConcurrentHashMap<>();
        private static final SearchCatalog INSTANCE = new SearchCatalog();
        public static SearchCatalog getInstance() { return INSTANCE; }
        public List<Driver.Question> searchByTags(String t) {
            return questionsUsingTags.getOrDefault(t, new CopyOnWriteArrayList<>());
        }
        public List<Driver.Question> searchByUsers(String u) {
            return questionsUsingUsers.getOrDefault(u, new CopyOnWriteArrayList<>());
        }
        public List<Driver.Question> searchByWords(String w) {
            return questionsUsingWords.getOrDefault(w.toLowerCase(), new CopyOnWriteArrayList<>());
        }
        public void indexQuestion(Driver.Question q) {
            if (q == null) return;
            for (Driver.Tag t : q.tags)
                questionsUsingTags.computeIfAbsent(t.name, k -> new CopyOnWriteArrayList<>()).addIfAbsent(q);
            questionsUsingUsers.computeIfAbsent(q.createdBy.getName(), k -> new CopyOnWriteArrayList<>()).addIfAbsent(q);
            for (String w : q.title.split("\\W+")) {
                if (!w.trim().isEmpty())
                questionsUsingWords.computeIfAbsent(w.toLowerCase(), k -> new CopyOnWriteArrayList<>()).addIfAbsent(q);
            }
        }
    }

    public static class TagRegistry {
        private final ConcurrentHashMap<String, Driver.Tag> registry = new ConcurrentHashMap<>();
        private static final TagRegistry INSTANCE = new TagRegistry();
        public static TagRegistry getInstance() { return INSTANCE; }
        public void register(Driver.Tag t) {
            registry.putIfAbsent(t.name, t);
        }
    }

    public static class VoteRegistry {
        private final ConcurrentHashMap<Integer, AtomicInteger> votes = new ConcurrentHashMap<>();
        private static final VoteRegistry INSTANCE = new VoteRegistry();
        public static VoteRegistry getInstance() { return INSTANCE; }
        public void voteUp(int id) {
            votes.computeIfAbsent(id, k -> new AtomicInteger(0)).incrementAndGet();
        }
        public void voteDown(int id) {
            votes.computeIfAbsent(id, k -> new AtomicInteger(0)).decrementAndGet();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Driver d = new Driver();
        Guest guest = d.new Guest();
        User alice = d.new User(); alice.setName("alice123"); alice.setReputationPoints(10);
        User bob = d.new User(); bob.setName("bob_dev"); bob.setReputationPoints(7);

        Tag java = d.new Tag("java", "Java programming language");
        Tag oop = d.new Tag("oop", "Object-oriented programming");
        alice.createTag(java); alice.createTag(oop);
        List<Tag> tags = Arrays.asList(java, oop);

        Question q = alice.create("What is the difference between interface and abstract class in Java?", "I am confused about when to use interface and when to use abstract class.", tags);
        alice.addBounty(100);
        alice.publishQuestion();
        Answer a = d.new Answer("Use interface when you want to define a contract.", bob);
        bob.addAnswer(q, a);
        alice.acceptAnswer(a);

        ExecutorService ex = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 20; i++) {
            int id = q.getId();
            ex.submit(() -> VoteRegistry.getInstance().voteUp(id)); }

        ex.shutdown();
        ex.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("Search by tag 'java': " + SearchCatalog.getInstance().searchByTags("java"));
    }
}

