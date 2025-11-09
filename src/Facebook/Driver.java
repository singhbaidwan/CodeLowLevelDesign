package Facebook;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

// ------------------------ Enums & Models ------------------------
enum AccountStatus { ACTIVE, BLOCKED, DISABLED, DELETED }
enum FriendInviteStatus { PENDING, ACCEPTED, REJECTED, CANCELED }
enum PostPrivacySettings { PUBLIC, FRIENDS_OF_FRIENDS, ONLY_FRIENDS, CUSTOM }
enum Role { USER, ADMIN }

// ------------------------ Address ------------------------
class Address {
    private int zipCode;
    private String houseNo;
    private String city;
    private String state;
    private String country;

    public Address() {}
    public Address(int zipCode, String houseNo, String city, String state, String country) {
        this.zipCode = zipCode; this.houseNo = houseNo; this.city = city; this.state = state; this.country = country;
    }

    // Getters / Setters
    public int getZipCode() { return zipCode; }
    public void setZipCode(int zipCode) { this.zipCode = zipCode; }
    public String getHouseNo() { return houseNo; }
    public void setHouseNo(String houseNo) { this.houseNo = houseNo; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}

// ------------------------ Interfaces ------------------------
interface PageFunctionsByUser {
    Page createPage(String name);
    Page sharePage(Page page);
    void likePage(Page page);
    void followPage(Page page);
    void unLikePage(Page page);
    void unFollowPage(Page page);
}

interface GroupFunctionsByUser {
    Group createGroup(String name);
    void joinGroup(Group group);
    void leaveGroup(Group group);
    void sendGroupInvite(Group group);
}

interface PostFunctionsByUser {
    Post createPost(String content);
    Post sharePost(Post post);
    void commentOnPost(Post post);
    void likePost(Post post);
}

interface CommentFunctionsByUser {
    Comment createComment(Post post, String content);
    void likeComment(Comment comment);
}

// ------------------------ Profile & related ------------------------
class Work {
    private String title, company, location, description;
    private Date startDate, endDate;

    public Work(String title, String company, String location, String description, Date startDate, Date endDate) {
        this.title = title;
        this.company = company;
        this.location = location;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }
}

class Place {
    private String name;
    public Place() {}
    public Place(String n) { this.name = n; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

}

class Education {
    private String school, degree, description;
    private Date startDate, endDate;

    public Education(String school, String degree, String description, Date startDate, Date endDate) {
        this.school = school;
        this.degree = degree;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getSchool() {
        return school;
    }

    public void setSchool(String school) {
        this.school = school;
    }

    public String getDegree() {
        return degree;
    }

    public void setDegree(String degree) {
        this.degree = degree;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }
}

class ProfilePrivacy {
    public void changeFriendsListVisibility(Profile profile) {}
    public void lockProfile(Profile profile) {}
    public void lockProfilePicture(Profile profile) {}
}

class Profile {
    private String gender;
    private byte[] profilePicture;
    private byte[] coverPhoto;

    // Use thread-safe list implementations because friends list may be accessed/modified concurrently
    public final List<User> friends = Collections.synchronizedList(new ArrayList<>());
    public final List<Integer> usersFollowed = Collections.synchronizedList(new ArrayList<>());
    public final List<Integer> pagesFollowed = Collections.synchronizedList(new ArrayList<>());
    public final List<Integer> groupsJoined = Collections.synchronizedList(new ArrayList<>());

    private ProfilePrivacy privacy;
    private final List<Work> workExperience = new ArrayList<>();
    private final List<Education> educationInfo = new ArrayList<>();
    private final List<Place> places = new ArrayList<>();

    public Profile() {}

    public List<User> getFriends() { return friends; }
    public void setFriends(List<User> f) {
        friends.clear();
        if (f != null) friends.addAll(f);
    }

    public boolean addWorkExperience(Work work) {
        synchronized (workExperience) { return workExperience.add(work); }
    }
    public boolean addEducation(Education education) {
        synchronized (educationInfo) { return educationInfo.add(education); }
    }
    public boolean addPlace(Place place) {
        synchronized (places) { return places.add(place); }
    }
    public boolean addProfilePicture(byte[] image) { this.profilePicture = image; return true; }
    public boolean addCoverPhoto(byte[] image) { this.coverPhoto = image; return true; }
    public boolean addGender(String gender) { this.gender = gender; return true; }

    // Add friend safely
    public boolean addFriend(User user) {
        synchronized (friends) {
            if (!friends.contains(user)) {
                friends.add(user);
                return true;
            }
            return false;
        }
    }
}

// ------------------------ Person / User ------------------------
abstract class Person {
    protected String Id;
    protected String password;
    protected String name;
    protected String email;
    protected String phone;
    protected Address address;
    protected AccountStatus status = AccountStatus.ACTIVE;

    public boolean resetPassword() {
        // minimal implementation
        this.password = UUID.randomUUID().toString();
        return true;
    }

    // getters / setters
    public void setName(String name) { this.name = name; }
    public String getName() { return name; }
    public void setAddress(Address addr) { this.address = addr; }
    public Address getAddress() { return address; }
}

class Page {
    private final int pageId;
    private String name;
    private String description;
    private final AtomicInteger likeCount = new AtomicInteger(0);
    private final AtomicInteger followerCount = new AtomicInteger(0);

    Page(int id, String name) { this.pageId = id; this.name = name; }

    public int getPageId() { return pageId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String d) { this.description = d; }
    public int incrementLikes() { return likeCount.incrementAndGet(); }
    public int incrementFollowers() { return followerCount.incrementAndGet(); }
    public int decrementFollowers() { return Math.max(0, followerCount.decrementAndGet()); }
    public int getFollowerCount() { return followerCount.get(); }
}

class Post {
    private final int postId;
    private String content;
    private byte[] image;
    private final AtomicInteger likeCount = new AtomicInteger(0);
    public final AtomicInteger shareCount = new AtomicInteger(0);
    private User postOwner;
    private PostPrivacySettings settings = PostPrivacySettings.PUBLIC;

    Post(int id, String content, User owner) { this.postId = id; this.content = content; this.postOwner = owner; }

    public int getPostId() { return postId; }
    public double getLikeCount() { return likeCount.get(); }
    public void like() { likeCount.incrementAndGet(); }
    public void changePostVisibility(PostPrivacySettings s) { this.settings = s; }
}

class Comment {
    private final int commentId;
    private String content;
    private final AtomicInteger likeCount = new AtomicInteger(0);
    private User commentOwner;

    Comment(int id, String content, User owner) { this.commentId = id;
        this.content = content;
        this.commentOwner = owner; }
    public void like() { likeCount.incrementAndGet(); }
}

// ------------------------ Group ------------------------
interface GroupFunctions {
    boolean addUser(User user);
    boolean deleteUser(User user);
    boolean notifyUser(User user);
}

class Group implements GroupFunctions {
    private int groupId;
    private String name;
    private String description;
    private byte[] coverPhoto;
    private final AtomicInteger totalUsers = new AtomicInteger(0);
    private volatile boolean isPrivate;
    private final List<User> users = Collections.synchronizedList(new ArrayList<>());

    public Group() {}
    public Group(int id, String name) { this.groupId = id; this.name = name; }

    public boolean addUser(User user) {
        synchronized (users) {
            if (users.contains(user)) return false;
            users.add(user);
            totalUsers.incrementAndGet();
            // track in user's profile
            if (user.getProfile() != null) user.getProfile().getFriends(); // noop access to ensure not null
            return true;
        }
    }

    public boolean deleteUser(User user) {
        synchronized (users) {
            boolean removed = users.remove(user);
            if (removed) totalUsers.decrementAndGet();
            return removed;
        }
    }

    public boolean notifyUser(User user) {
        // basic placeholder
        return true;
    }

    public void updateDescription(String description) { this.description = description; }
    public void addCoverPhoto(byte[] image) { this.coverPhoto = image; }

    // getters / setters
    public void setName(String name) { this.name = name; }
    public String getName() { return name; }
    public void setUsers(List<User> u) {
        users.clear();
        if (u != null) users.addAll(u);
        totalUsers.set(users.size());
    }
    public void setTotalUsers(int t) { totalUsers.set(t); }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
}

// ------------------------ Messaging ------------------------
class Message {
    private int messageId;
    private User sender;
    private String content;
    private final List<User> recipients = Collections.synchronizedList(new ArrayList<>());
    private final List<byte[]> multimedia = Collections.synchronizedList(new ArrayList<>());

    public boolean addRecipient(List<User> users) {
        synchronized (recipients) {
            recipients.addAll(users);
            return true;
        }
    }
}

// ------------------------ Notification ------------------------
class Notification {
    private int notificationId;
    private Date createdOn;
    private String content;

    public boolean sendNotification(Profile profile) {
        // in real system we'd push; here we return true
        return true;
    }
}

// ------------------------ FriendRequest ------------------------
class FriendRequest {
    private User recipient;
    private User sender;
    private FriendInviteStatus status;
    private Date requestSent;
    private Date requestStatusModified;

    public FriendRequest() {}

    public void setSender(User s) { this.sender = s; }
    public void setRecipent(User r) { this.recipient = r; }
    public User getSender() { return sender; }
    public User getRecipient() { return recipient; }
    public FriendInviteStatus getStatus() { return status; }
    public void setStatus(FriendInviteStatus s) { this.status = s;
        this.requestStatusModified = new Date(); }

    public boolean acceptRequest(User user) {
        if (recipient == null || !recipient.equals(user)) return false;
        setStatus(FriendInviteStatus.ACCEPTED);
        boolean added = addToFriendList();
        return added;
    }

    public boolean rejectRequest(User user) {
        if (recipient == null || !recipient.equals(user)) return false;
        setStatus(FriendInviteStatus.REJECTED);
        return true;
    }

    public boolean sendFriendRequest() {
        if (sender == null || recipient == null) return false;
        setStatus(FriendInviteStatus.PENDING);
        this.requestSent = new Date();
        // optionally add to some inbox; for our demo we simply return true
        return true;
    }

    private boolean addToFriendList() {
        if (sender == null || recipient == null) return false;
        boolean a = sender.addFriend(recipient);
        boolean b = recipient.addFriend(sender);
        return a || b;
    }

    public boolean sendNotification() { return true; }
}

// ------------------------ Search Catalog ------------------------
class SearchCatalog {
    private final ConcurrentHashMap<String, List<User>> userNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Group>> groupNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Page>> pageTitles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Post>> posts = new ConcurrentHashMap<>();

    public boolean addNewUser(User user) {
        userNames.compute(user.getName().toLowerCase(), (k, v) -> {
            if (v == null) v = new ArrayList<>();
            v.add(user);
            return v;
        });
        return true;
    }
    public boolean addNewGroup(Group group) {
        groupNames.compute(group.getName().toLowerCase(), (k, v) -> {
            if (v == null) v = new ArrayList<>(); v.add(group); return v;
        });
        return true;
    }
    public boolean addNewPage(Page page) {
        pageTitles.compute(page.getName().toLowerCase(), (k, v) -> {
            if (v == null) v = new ArrayList<>(); v.add(page); return v;
        });
        return true;
    }
    public boolean addNewPost(Post post) {
        posts.compute(String.valueOf(post.getPostId()), (k, v) -> {
            if (v == null) v = new ArrayList<>(); v.add(post); return v;
        });
        return true;
    }

    public List<User> searchUsers(String name) {
        return userNames.getOrDefault(name.toLowerCase(), Collections.emptyList());
    }
    public List<Group> searchGroups(String name) {
        return groupNames.getOrDefault(name.toLowerCase(), Collections.emptyList());
    }
    public List<Page> searchPages(String name) {
        return pageTitles.getOrDefault(name.toLowerCase(), Collections.emptyList());
    }
    public List<Post> searchPosts(String keywords) {
        return posts.getOrDefault(keywords.toLowerCase(), Collections.emptyList());
    }
}

// ------------------------ User ------------------------
class User extends Person implements PageFunctionsByUser, GroupFunctionsByUser, PostFunctionsByUser, CommentFunctionsByUser {
    private Date dateOfJoining;
    private final List<Page> pagesAdmin = Collections.synchronizedList(new ArrayList<>());
    private final List<Group> groupsAdmin = Collections.synchronizedList(new ArrayList<>());
    private Profile profile;
    private final Set<Role> roles = Collections.synchronizedSet(new HashSet<>());

    // simple counters/registries
    private static final AtomicInteger PAGE_ID_GENERATOR = new AtomicInteger(1);
    private static final AtomicInteger POST_ID_GENERATOR = new AtomicInteger(1);

    public User() { super(); }
    public User(String name) { this.name = name; }

    // profile helpers
    public Profile getProfile() { return profile; }
    public void setProfile(Profile p) { this.profile = p; }

    public boolean hasRole(Role role) { return roles.contains(role); }
    public void addRole(Role role) { roles.add(role); }

    // friend operations
    public boolean sendFriendRequest(User user) {
        FriendRequest fr = new FriendRequest();
        fr.setSender(this);
        fr.setRecipent(user);
        return fr.sendFriendRequest();
    }

    public boolean addFriend(User userB) {
        if (profile == null) profile = new Profile();
        boolean added = profile.addFriend(userB);
        return added;
    }

    public boolean accept(FriendRequest request) {
        if (request == null) return false;
        return request.acceptRequest(this);
    }

    public boolean reject(FriendRequest request) {
        if (request == null) return false;
        return request.rejectRequest(this);
    }

    // messaging / recommendations - simplified placeholders
    public boolean sendMessage(Message message) { return true; }
    public boolean sendRecommendation(Page page, Group group, User user) { return true; }
    public boolean unfriendUser(User user) {
        if (profile == null) return false;
        synchronized (profile.getFriends()) {
            return profile.getFriends().remove(user);
        }
    }
    public boolean blockUser(User user) { return false; }
    public boolean followUser(User user) { return false; }

    // Page functions
    @Override
    public Page createPage(String name) {
        int id = PAGE_ID_GENERATOR.getAndIncrement();
        Page p = new Page(id, name);
        pagesAdmin.add(p);
        if (profile == null) profile = new Profile();
        profile.pagesFollowed.add(id); // creator automatically follows (simple assumption)
        return p;
    }
    @Override
    public Page sharePage(Page page) { return page; }
    @Override
    public void likePage(Page page) { page.incrementLikes(); }
    @Override
    public void followPage(Page page) {
        page.incrementFollowers();
        if (profile == null)
            profile = new Profile();
        profile.pagesFollowed.add(page.getPageId());
    }
    @Override
    public void unLikePage(Page page) {}
    @Override
    public void unFollowPage(Page page) {
        page.decrementFollowers();
        if (profile != null)
            profile.pagesFollowed.remove((Integer) page.getPageId());
    }

    // Group functions
    @Override
    public Group createGroup(String name) {
        Group g = new Group();
        g.setName(name);
        groupsAdmin.add(g);
        return g;
    }
    @Override
    public void joinGroup(Group group) {
        group.addUser(this);
        if (profile==null) profile=new Profile();
        profile.groupsJoined.add(group.hashCode());
    }
    @Override
    public void leaveGroup(Group group) {
        group.deleteUser(this);
        if (profile!=null) profile.groupsJoined.remove((Integer)group.hashCode());
    }
    @Override
    public void sendGroupInvite(Group group) {}

    // Post functions
    @Override
    public Post createPost(String content) {
        int id = POST_ID_GENERATOR.getAndIncrement();
        Post p = new Post(id, content, this);
        return p;
    }
    @Override
    public Post sharePost(Post post) { post.shareCount.incrementAndGet(); return post; }
    @Override
    public void commentOnPost(Post post) {}
    @Override
    public void likePost(Post post) { post.like(); }

    // Comment functions
    @Override
    public Comment createComment(Post post, String content) {
        Comment c = new Comment((int)(Math.random()*10000), content, this);
        return c;
    }
    @Override
    public void likeComment(Comment comment) { comment.like(); }

    // search helper (very naive)
    public List<User> search(String name) {
        List<User> found = new ArrayList<>();
        if (this.name != null && this.name.equalsIgnoreCase(name)) found.add(this);
        return found;
    }

    // Admin functions (no-op placeholders)
    public void enablePage(Page page) {}
    public void disablePage(Page page) {}
    public void blockPageUser(User user) {}
    public void unblockPageUser(User user) {}
    public void blockGroupUser(User user) {}
    public void unblockGroupUser(User user) {}
    public void changeGroupPrivacy(Group group) {}
}

// ------------------------ Driver / Demo ------------------------
public class Driver {
    public static void main(String[] args) throws Exception {
        System.out.println("=========================================");
        System.out.println("📌 SCENARIO 1: Friend Request and Acceptance");
        System.out.println("=========================================");

        // Create Profiles
        Profile profileA = new Profile();
        profileA.setFriends(new ArrayList<>());

        Profile profileB = new Profile();
        profileB.setFriends(new ArrayList<>());

        // Create Users
        User userA = new User();
        userA.setName("Alice");
        userA.setProfile(profileA);

        User userB = new User();
        userB.setName("Bob");
        userB.setProfile(profileB);

        // Step 1: UserA searches for UserB (demo)
        System.out.println("🔍 UserA searches for UserB...");
        System.out.println("📢 Found user: " + userB.getName());

        // Step 2: UserA sends a friend request
        FriendRequest friendRequest = new FriendRequest();
        friendRequest.setSender(userA);
        friendRequest.setRecipent(userB);
        friendRequest.sendFriendRequest();
        System.out.println("📨 Friend request sent from " + userA.getName() + " to " + userB.getName());

        // Simulate concurrency: accept in a thread pool to show it's safe
        ExecutorService ex = Executors.newFixedThreadPool(2);
        Future<Boolean> acceptFuture = ex.submit(() -> userB.accept(friendRequest));
        boolean accepted = acceptFuture.get();
        ex.shutdown();

        if (accepted) {
            System.out.println("✅ " + userB.getName() + " accepted the friend request.");
        } else {
            System.out.println("❌ " + userB.getName() + " could not accept the friend request.");
        }

        // Step 4: Display updated friend lists
        System.out.println("👥 Friends of " + userA.getName() + ": " +
                userA.getProfile().getFriends().stream().map(User::getName).toList());
        System.out.println("👥 Friends of " + userB.getName() + ": " +
                userB.getProfile().getFriends().stream().map(User::getName).toList());

        System.out.println("\n=========================================");
        System.out.println("📌 SCENARIO 2: User Creates and Follows a Page");
        System.out.println("=========================================");

        // Step 1: UserA creates a page
        Page techPage = userA.createPage("Tech Updates");
        System.out.println("📄 " + userA.getName() + " created a page: " + techPage.getName());

        // Step 2: UserB follows the page
        userB.followPage(techPage);
        System.out.println("👣 " + userB.getName() + " followed the page: " + techPage.getName() + " (followers: " + techPage.getFollowerCount() + ")");

        System.out.println("\n=========================================");
        System.out.println("📌 SCENARIO 3: Creating and Joining a Group");
        System.out.println("=========================================");

        // Step 1: A group is created directly
        Group javaGroup = new Group();
        javaGroup.setName("Java Enthusiasts");
        javaGroup.setUsers(new ArrayList<>());
        javaGroup.setTotalUsers(0);
        javaGroup.setPrivate(false);
        System.out.println("👥 A new group created: " + javaGroup.getName());

        // Step 2: UserA joins the group (thread-safe)
        boolean joined = javaGroup.addUser(userA);
        if (joined) {
            System.out.println("✅ " + userA.getName() + " joined the group: " + javaGroup.getName());
        } else {
            System.out.println("⚠️ " + userA.getName() + " could not join the group (already a member?)");
        }
    }
}
