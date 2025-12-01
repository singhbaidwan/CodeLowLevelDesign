package LibraryManagementSystem;

// LibrarySystemDemo.java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.text.SimpleDateFormat;

// -------------------- ENUMS --------------------
enum BookFormat { HARDCOVER, PAPERBACK, AUDIOBOOK, EBOOK, NEWSPAPER, MAGAZINE, JOURNAL }
enum BookStatus  { AVAILABLE, RESERVED, LOANED, LOST }
enum ReservationStatus { WAITING, PENDING, CANCELED, NONE }
enum AccountStatus { ACTIVE, CLOSED, CANCELED, BLOCKLISTED, NONE }

// -------------------- Basic Data Types --------------------
class Address {
    public final String street;
    public final String city;
    public final String state;
    public final int zip;
    public final String country;
    public Address(String street, String city, String state, int zip, String country) {
        this.street = street; this.city = city; this.state = state; this.zip = zip; this.country = country;
    }
    @Override public String toString() {
        return String.format("%s, %s, %s - %d, %s", street, city, state, zip, country);
    }
}

class Person {
    public String name;
    public Address address;
    public String email;
    public String phone;
    public Person(String name, Address address, String email, String phone) {
        this.name = name; this.address = address; this.email = email; this.phone = phone;
    }
}

// -------------------- Domain: Author, Book, BookItem, Rack --------------------
class Author {
    public final String name;
    public final Address address;
    public final String email;
    public final String phone;
    public final String bio;
    public Author(String name, Address address, String email, String phone, String bio) {
        this.name = name; this.address = address; this.email = email; this.phone = phone; this.bio = bio;
    }
}

class Book {
    public final String isbn;
    public final String title;
    public final String subject;
    public final String publisher;
    public final Date publicationDate;
    public final String language;
    public final int numberOfPages;
    public final BookFormat bookFormat;
    public final List<Author> authors;

    public Book(String isbn, String title, String subject, String publisher,
                Date publicationDate, String language, int numberOfPages,
                BookFormat bookFormat, List<Author> authors) {
        this.isbn = isbn; this.title = title; this.subject = subject; this.publisher = publisher;
        this.publicationDate = publicationDate; this.language = language; this.numberOfPages = numberOfPages;
        this.bookFormat = bookFormat; this.authors = authors == null ? new ArrayList<>() : authors;
    }
}

class Rack {
    public final int number;
    public final String locationIdentifier;
    private final List<BookItem> bookItems = new CopyOnWriteArrayList<>();
    public Rack(int number, String locationIdentifier) {
        this.number = number; this.locationIdentifier = locationIdentifier;
    }
    public void addBookItem(BookItem bookItem) {
        if (bookItem == null) return;
        bookItems.add(bookItem);
        bookItem.setPlacedAt(this);
    }
    public List<BookItem> getBookItems() { return Collections.unmodifiableList(bookItems); }
}

// BookItem: thread-safe operations for checkout/reserve/return/renew
class BookItem {
    public final String id;
    public final Book book;
    public volatile boolean isReferenceOnly;
    public volatile Date borrowed;
    public volatile Date dueDate;
    public volatile double price;
    public volatile BookStatus status;
    public volatile Date dateOfPurchase;
    public volatile Date publicationDate;
    public volatile Rack placedAt;
    private Librarian addedBy;

    // lock to guard operations on this BookItem
    private final ReentrantLock lock = new ReentrantLock();

    public BookItem(String id, Book book, Rack placedAt, double price, Date dateOfPurchase, Date publicationDate) {
        this.id = id; this.book = book; this.placedAt = placedAt; this.price = price;
        this.dateOfPurchase = dateOfPurchase; this.publicationDate = publicationDate;
        this.status = BookStatus.AVAILABLE;
    }

    public void setPlacedAt(Rack rack) {
        this.placedAt = rack;
    }
    public void setAddedBy(Librarian librarian) {
        this.addedBy = librarian;
    }

    // Reserve: mark RESERVED if AVAILABLE
    public boolean reserve(String memberId) {
        lock.lock();
        try {
            if (status != BookStatus.AVAILABLE) return false;
            status = BookStatus.RESERVED;
            return true;
        } finally {
            lock.unlock();
        }
    }

    // Checkout: only if AVAILABLE or RESERVED by the same member (we keep it simple)
    public boolean checkout(String memberId, int loanDays) {
        lock.lock();
        try {
            if (isReferenceOnly) return false;
            if (status == BookStatus.AVAILABLE || status == BookStatus.RESERVED) {
                status = BookStatus.LOANED;
                borrowed = new Date();
                Calendar cal = Calendar.getInstance();
                cal.setTime(borrowed);
                cal.add(Calendar.DATE, loanDays);
                dueDate = cal.getTime();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    // Return: set AVAILABLE and clear borrowed/due
    public double returnItem() {
        lock.lock();
        try {
            if (status != BookStatus.LOANED) {
                // if not loaned, nothing to do
                return 0.0;
            }
            Date now = new Date();
            long lateDays = 0;
            if (dueDate != null && now.after(dueDate)) {
                long diff = now.getTime() - dueDate.getTime();
                lateDays = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
            }
            status = BookStatus.AVAILABLE;
            borrowed = null;
            dueDate = null;
            return lateDays;
        } finally {
            lock.unlock();
        }
    }

    public boolean renew(int extraDays) {
        lock.lock();
        try {
            if (status != BookStatus.LOANED || dueDate == null) return false;
            Calendar cal = Calendar.getInstance();
            cal.setTime(dueDate);
            cal.add(Calendar.DATE, extraDays);
            dueDate = cal.getTime();
            return true;
        } finally {
            lock.unlock();
        }
    }
}

// -------------------- Notifications --------------------
abstract class Notification {
    protected final String notificationId;
    protected final Date creationDate;
    protected final String content;
    protected final BookLending bookLending;
    protected final BookReservation bookReservation;

    public Notification(String notificationId, String content, BookLending lending, BookReservation reservation) {
        this.notificationId = notificationId;
        this.creationDate = new Date();
        this.content = content;
        this.bookLending = lending;
        this.bookReservation = reservation;
    }

    public abstract boolean sendNotification();
}

class PostalNotification extends Notification {
    private final Address address;
    public PostalNotification(String notificationId, String content, Address address) {
        super(notificationId, content, null, null);
        this.address = address;
    }
    public boolean sendNotification(){
        System.out.printf("PostalNotification[%s] -> %s : %s%n", notificationId, address, content);
        return true;
    }
}

class EmailNotification extends Notification {
    private final String email;
    public EmailNotification(String notificationId, String content, String email) {
        super(notificationId, content, null, null);
        this.email = email;
    }
    public boolean sendNotification(){
        System.out.printf("EmailNotification[%s] -> %s : %s%n", notificationId, email, content);
        return true;
    }
}

// -------------------- Catalog & Search --------------------
class Catalog {
    // We'll index BookItems by title, author names, subject, and publication date (simple maps)
    private final ConcurrentMap<String, CopyOnWriteArrayList<BookItem>> byTitle = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArrayList<BookItem>> byAuthor = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArrayList<BookItem>> bySubject = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArrayList<BookItem>> byPubDate = new ConcurrentHashMap<>();

    // Add single book item into indices
    public void addBookItem(BookItem bi) {
        if (bi == null) return;
        String titleKey = normalize(bi.book.title);
        byTitle.computeIfAbsent(titleKey, k -> new CopyOnWriteArrayList<>()).add(bi);

        for (Author a : bi.book.authors) {
            String ak = normalize(a.name);
            byAuthor.computeIfAbsent(ak, k -> new CopyOnWriteArrayList<>()).add(bi);
        }
        String subj = normalize(bi.book.subject);
        bySubject.computeIfAbsent(subj, k -> new CopyOnWriteArrayList<>()).add(bi);

        String pub = bi.book.publicationDate == null ? "unknown" : new SimpleDateFormat("yyyy-MM-dd").format(bi.book.publicationDate);
        byPubDate.computeIfAbsent(pub, k -> new CopyOnWriteArrayList<>()).add(bi);
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    public List<BookItem> searchByTitle(String query) {
        if (query == null) return Collections.emptyList();
        String q = normalize(query);
        List<BookItem> res = byTitle.getOrDefault(q, new CopyOnWriteArrayList<>());
        return new ArrayList<>(res);
    }
    public List<BookItem> searchByAuthor(String query) {
        if (query == null) return Collections.emptyList();
        String q = normalize(query);
        List<BookItem> res = byAuthor.getOrDefault(q, new CopyOnWriteArrayList<>());
        return new ArrayList<>(res);
    }
    public List<BookItem> searchBySubject(String query) {
        if (query == null) return Collections.emptyList();
        String q = normalize(query);
        List<BookItem> res = bySubject.getOrDefault(q, new CopyOnWriteArrayList<>());
        return new ArrayList<>(res);
    }
    public List<BookItem> searchByPublicationDate(String queryDate) {
        if (queryDate == null) return Collections.emptyList();
        List<BookItem> res = byPubDate.getOrDefault(queryDate, new CopyOnWriteArrayList<>());
        return new ArrayList<>(res);
    }
}

// -------------------- Reservation, Lending, Fine --------------------
class BookReservation {
    public final String itemId;
    public final Date creationDate;
    public volatile ReservationStatus status;
    public final String memberId;

    private BookReservation(String itemId, String memberId) {
        this.itemId = itemId; this.memberId = memberId; this.creationDate = new Date(); this.status = ReservationStatus.PENDING;
    }

    // simple manager to keep reservations
    private static final ConcurrentMap<String, BookReservation> reservations = new ConcurrentHashMap<>();

    public static BookReservation createReservation(String bookItemId, String memberId) {
        BookReservation r = new BookReservation(bookItemId, memberId);
        reservations.put(bookItemId, r);
        return r;
    }

    public static BookReservation fetchReservationDetails(String bookItemId) {
        return reservations.get(bookItemId);
    }

    public void cancel() { this.status = ReservationStatus.CANCELED; reservations.remove(itemId); }
}

class BookLending {
    public final String itemId;
    public final Date creationDate;
    public final Date dueDate;
    public volatile Date returnDate;
    public final String memberId;
    public volatile BookReservation bookReservation;
    public final User user;

    private BookLending(String itemId, String memberId, Date dueDate, User user) {
        this.itemId = itemId; this.memberId = memberId; this.creationDate = new Date(); this.dueDate = dueDate; this.user = user;
        this.returnDate = null;
    }

    private static final ConcurrentMap<String, BookLending> lendings = new ConcurrentHashMap<>();

    // Lend a book: returns true on success
    public static boolean lendBook(String bookItemId, String memberId) {
        Library lib = Library.getInstance();
        BookItem bi = lib.getBookItemById(bookItemId);
        if (bi == null) return false;
        // patron/members are in Library - in Driver member exists, skip deep validation for brevity
        boolean ok = bi.checkout(memberId, 14); // 2-week loan
        if (!ok) return false;
        Date due = bi.dueDate;
        BookLending bl = new BookLending(bookItemId, memberId, due, null);
        lendings.put(bookItemId, bl);
        return true;
    }

    public static BookLending fetchLendingDetails(String bookItemId) {
        return lendings.get(bookItemId);
    }
}

class Fine {
    public final Date creationDate;
    public final String bookItemId;
    public final String memberId;
    public Fine(String bookItemId, String memberId) {
        this.creationDate = new Date();
        this.bookItemId = bookItemId;
        this.memberId = memberId;
    }

    // collect fine based on days late - simple flat fee per day
    public static double collectFine(String memberId, long days) {
        if (days <= 0) return 0.0;
        double perDay = 1.5; // currency units per day
        double total = perDay * days;
        System.out.printf("Collected fine for member %s : days=%d amount=%.2f%n", memberId, days, total);
        return total;
    }
}

// -------------------- Users: Account, Librarian, Member --------------------
abstract class User {
    protected final String id;
    protected String password;
    protected AccountStatus status;
    public Person person;
    public LibraryCard card;

    public User(String id, String password, Person person, LibraryCard card) {
        this.id = id; this.password = password; this.person = person; this.card = card; this.status = AccountStatus.ACTIVE;
    }

    public abstract boolean resetPassword();
}

class Librarian extends User {
    public Librarian(String id, String password, Person person, LibraryCard card) {
        super(id, password, person, card);
    }

    public boolean addBookItem(BookItem bookItem) {
        if (bookItem == null) return false;
        Library lib = Library.getInstance();
        lib.catalog.addBookItem(bookItem);
        bookItem.setAddedBy(this);
        if (bookItem.placedAt != null) bookItem.placedAt.addBookItem(bookItem);
        System.out.printf("Librarian %s added BookItem %s (%s)%n", person.name, bookItem.id, bookItem.book.title);
        return true;
    }

    public boolean blockMember(Member member) {
        if (member == null) return false;
        member.status = AccountStatus.BLOCKLISTED;
        System.out.printf("Librarian %s blocked member %s%n", person.name, member.person.name);
        return true;
    }
    public boolean unBlockMember(Member member) {
        if (member == null) return false;
        member.status = AccountStatus.ACTIVE;
        System.out.printf("Librarian %s unblocked member %s%n", person.name, member.person.name);
        return true;
    }
    @Override
    public boolean resetPassword() {
        this.password = "lib-default";
        return true;
    }
}

class Member extends User {
    private final AtomicInteger totalBooksCheckedOut = new AtomicInteger(0);
    private final ReentrantLock lock = new ReentrantLock();

    public Member(String id, String password, Person person, LibraryCard card) {
        super(id, password, person, card);
    }

    public boolean reserveBookItem(BookItem bookItem) {
        if (bookItem == null) return false;
        boolean ok = bookItem.reserve(this.id);
        if (ok) {
            BookReservation.createReservation(bookItem.id, this.id);
            System.out.printf("Member %s reserved BookItem %s%n", person.name, bookItem.id);
        } else {
            System.out.printf("Member %s failed to reserve BookItem %s (status=%s)%n", person.name, bookItem.id, bookItem.status);
        }
        return ok;
    }

    private void incrementTotalBooksCheckedOut() { totalBooksCheckedOut.incrementAndGet(); }

    public boolean checkoutBookItem(BookItem bookItem) {
        lock.lock();
        try {
            if (bookItem == null) return false;
            boolean ok = bookItem.checkout(this.id, 14);
            if (ok) {
                incrementTotalBooksCheckedOut();
                BookLending.lendBook(bookItem.id, this.id); // create lending record
                System.out.printf("Member %s checked out BookItem %s (due=%s)%n", person.name, bookItem.id, bookItem.dueDate);
            } else {
                System.out.printf("Member %s failed to checkout BookItem %s (status=%s)%n", person.name, bookItem.id, bookItem.status);
            }
            return ok;
        } finally {
            lock.unlock();
        }
    }

    private void checkForFine(String bookItemId) {
        // fetch lending and compute late days
        BookItem bi = Library.getInstance().getBookItemById(bookItemId);
        if (bi == null) return;
        // if dueDate exists and past -> collect fine
        if (bi.dueDate != null && new Date().after(bi.dueDate)) {
            long diff = new Date().getTime() - bi.dueDate.getTime();
            long days = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
            Fine.collectFine(this.id, days);
        }
    }

    public void returnBookItem(BookItem bookItem) {
        if (bookItem == null) return;
        long lateDays = (long) bookItem.returnItem();
        if (lateDays > 0) {
            Fine.collectFine(this.id, lateDays);
        }
        System.out.printf("Member %s returned BookItem %s (lateDays=%d)%n", person.name, bookItem.id, lateDays);
    }

    public boolean renewBookItem(BookItem bookItem) {
        if (bookItem == null) return false;
        boolean ok = bookItem.renew(7); // extra 7 days
        if (ok) System.out.printf("Member %s renewed BookItem %s (newDue=%s)%n", person.name, bookItem.id, bookItem.dueDate);
        else System.out.printf("Member %s failed to renew BookItem %s%n", person.name, bookItem.id);
        return ok;
    }

    @Override
    public boolean resetPassword() {
        this.password = "member-default";
        return true;
    }
}

// -------------------- Library Card --------------------
class LibraryCard {
    public final String cardId;
    public final Date issuedOn;
    public LibraryCard(String cardId, Date issuedOn) { this.cardId = cardId; this.issuedOn = issuedOn; }
}

// -------------------- Library Singleton --------------------
class Library {
    private final String name;
    private final Address address;

    public final Catalog catalog = new Catalog();

    // all BookItems by id
    private final ConcurrentMap<String, BookItem> itemsById = new ConcurrentHashMap<>();

    // users (simple maps)
    private final ConcurrentMap<String, Member> members = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Librarian> librarians = new ConcurrentHashMap<>();

    // singleton instance
    private static volatile Library library = null;
    private final ReentrantLock lock = new ReentrantLock();

    private Library(String name, Address address) {
        this.name = name; this.address = address;
    }

    public static Library getInstance() {
        if (library == null) throw new IllegalStateException("Library not initialized. Use getInstance(name,address) first.");
        return library;
    }

    public static synchronized Library getInstance(String name, Address address) {
        if (library == null) library = new Library(name, address);
        return library;
    }

    // helper to add book item to central registry and catalog
    public void addCatalogBookItem(BookItem bi) {
        if (bi == null) return;
        itemsById.put(bi.id, bi);
        catalog.addBookItem(bi);
        if (bi.placedAt != null) bi.placedAt.addBookItem(bi);
    }

    public BookItem getBookItemById(String id) {
        return itemsById.get(id);
    }

    public void registerMember(Member m) {
        if (m == null) return;
        members.put(m.id, m);
    }
    public void registerLibrarian(Librarian l) {
        if (l == null) return;
        librarians.put(l.id, l);
    }

    // Utility getters for driver
    public Address getAddress() { return address; }

    // For driver convenience: public fields used earlier
    public Catalog getCatalog() { return catalog; }
}

// -------------------- Driver --------------------
public class Driver {
    public static void main(String[] args) {
        try {
            System.out.println("\n========== SYSTEM INITIALIZATION ==========\n");
            Address libAddress = new Address("1 Main St", "Springfield", "State", 12345, "Country");
            Library library = Library.getInstance("Springfield Public Library", libAddress);

            // Authors
            Author author1 = new Author("Jane Austen", libAddress, "austen@email.com", "123456", "Famous novelist");
            Author author2 = new Author("Mark Twain", libAddress, "twain@email.com", "234567", "Another novelist");

            // Books and BookItems
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Book book1 = new Book("ISBN123", "Pride and Prejudice", "Novel", "Publisher A",
                    sdf.parse("2000-01-01"), "English", 300, BookFormat.HARDCOVER, Arrays.asList(author1));
            Book book2 = new Book("ISBN456", "Adventures of Huckleberry Finn", "Adventure", "Publisher B",
                    sdf.parse("2005-05-20"), "English", 250, BookFormat.PAPERBACK, Arrays.asList(author2));

            Rack rack1 = new Rack(1, "A1");
            Rack rack2 = new Rack(2, "B2");

            BookItem bookItem1 = new BookItem("BI001", book1, rack1, 30.0, sdf.parse("2020-01-01"), book1.publicationDate);
            BookItem bookItem2 = new BookItem("BI002", book2, rack2, 25.0, sdf.parse("2021-06-15"), book2.publicationDate);

            // Add to library catalog and central registry
            library.addCatalogBookItem(bookItem1);
            library.addCatalogBookItem(bookItem2);

            // Users
            LibraryCard cardMember = new LibraryCard("CARD1001", new Date());
            Person personMember = new Person("Alice", libAddress, "alice@email.com", "345678");
            Member member = new Member("MEM001", "pass", personMember, cardMember);
            library.registerMember(member);

            LibraryCard cardLibrarian = new LibraryCard("CARD2001", new Date());
            Person personLibrarian = new Person("Libby", libAddress, "libby@email.com", "456789");
            Librarian librarian = new Librarian("LIB001", "pass", personLibrarian, cardLibrarian);
            library.registerLibrarian(librarian);

            // Librarian adds items (already added, but demonstrate)
            librarian.addBookItem(bookItem1);
            librarian.addBookItem(bookItem2);

            // SCENARIO 1: Search and Reserve
            System.out.println("\n------------------------------");
            System.out.println(">>> SCENARIO 1: Member searches and reserves a book");
            System.out.println("------------------------------\n");

            List<BookItem> foundBooks = library.catalog.searchByTitle("Pride and Prejudice");
            if (!foundBooks.isEmpty()) {
                BookItem bookItem = foundBooks.get(0);

                System.out.println("-> Member [" + member.person.name + "] attempts to reserve book item [" + bookItem.id + "]");
                member.reserveBookItem(bookItem);

                System.out.println("-> Member [" + member.person.name + "] checks out reserved book item [" + bookItem.id + "]");
                member.checkoutBookItem(bookItem);

            } else {
                System.out.println("Book not found.");
            }

            // SCENARIO 2: Return Book (Late Return)
            System.out.println("\n------------------------------");
            System.out.println(">>> SCENARIO 2: Member returns the book late");
            System.out.println("------------------------------\n");

            // Fast forward due date to simulate late return: set due to 3 days ago
            Calendar lateCalendar = Calendar.getInstance();
            lateCalendar.setTime(new Date());
            lateCalendar.add(Calendar.DATE, -3);
            bookItem1.dueDate = lateCalendar.getTime();

            System.out.println("-> Member [" + member.person.name + "] returns book item [" + bookItem1.id + "] after due date.");
            member.returnBookItem(bookItem1);

            // SCENARIO 3: Renew Book
            System.out.println("\n------------------------------");
            System.out.println(">>> SCENARIO 3: Member renews a book");
            System.out.println("------------------------------\n");

            // Check out again for demonstration
            System.out.println("-> Member [" + member.person.name + "] checks out the book again.");
            member.checkoutBookItem(bookItem1);

            System.out.println("-> Member [" + member.person.name + "] renews the book.");
            member.renewBookItem(bookItem1);

            // NOTIFICATIONS
            System.out.println("\n------------------------------");
            System.out.println(">>> NOTIFICATIONS");
            System.out.println("------------------------------\n");

            EmailNotification emailNotification = new EmailNotification(
                    "N001", "Your book is overdue!", member.person.email);
            emailNotification.sendNotification();

            PostalNotification postalNotification = new PostalNotification(
                    "N002", "Please return your book!", member.person.address);
            postalNotification.sendNotification();

            System.out.println("\n========== END OF DEMO ==========\n");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
