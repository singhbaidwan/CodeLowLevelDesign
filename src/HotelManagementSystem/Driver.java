package HotelManagementSystem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;

public class Driver {

    // -------------------------
    // Enums
    // -------------------------
    enum RoomStyle {
        STANDARD,
        DELUXE,
        FAMILY_SUITE,
        BUSINESS_SUITE
    }

    enum RoomStatus {
        AVAILABLE,
        RESERVED,
        OCCUPIED,
        NOT_AVAILABLE,
        BEING_SERVICED,
        OTHER
    }

    enum BookingStatus {
        REQUESTED,
        PENDING,
        CONFIRMED,
        CANCELLED,
        ABANDONED
    }

    enum AccountStatus {
        ACTIVE,
        CLOSED,
        CANCELED,
        BLACKLISTED,
        BLOCKED
    }

    enum AccountType {
        MEMBER,
        GUEST,
        MANAGER,
        RECEPTIONIST
    }

    enum PaymentStatus {
        UNPAID,
        PENDING,
        COMPLETED,
        FILLED,
        DECLINED,
        CANCELLED,
        ABANDONED,
        SETTLING,
        SETTLED,
        REFUNDED
    }

    // -------------------------
    // Address
    // -------------------------
    static class Address {
        private final String streetAddress;
        private final String city;
        private final String state;
        private final int zipCode;
        private final String country;

        public Address() {
            this("", "", "", 0, "");
        }

        public Address(String streetAddress, String city, String state, int zipCode, String country) {
            this.streetAddress = streetAddress == null ? "" : streetAddress;
            this.city = city == null ? "" : city;
            this.state = state == null ? "" : state;
            this.zipCode = zipCode;
            this.country = country == null ? "" : country;
        }

        @Override
        public String toString() {
            return streetAddress + ", " + city + ", " + state + " " + zipCode + ", " + country;
        }
    }

    // -------------------------
    // Account
    // -------------------------
    static class Account {
        private static final AtomicLong idCounter = new AtomicLong(1000);
        private final String id;
        private volatile String password;
        private volatile AccountStatus status = AccountStatus.ACTIVE;
        private final AccountType type;

        public Account(String password, AccountType type) {
            this.id = "ACC" + idCounter.getAndIncrement();
            this.password = password;
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public AccountStatus getStatus() {
            return status;
        }

        public void setStatus(AccountStatus status) {
            this.status = status;
        }

        public synchronized boolean resetPassword(String newPassword) {
            if (newPassword == null || newPassword.length() < 4) return false;
            this.password = newPassword;
            return true;
        }

        @Override
        public String toString() {
            return "Account{" + id + ", " + type + ", " + status + "}";
        }
    }

    // -------------------------
    // Person (abstract) and subclasses
    // -------------------------
    static abstract class Person {
        private static final AtomicInteger PERSON_ID = new AtomicInteger(1);
        private final int personId;
        private String name;
        private Address address;
        private String email;
        private String phone;
        private Account account;

        public Person(String name) {
            this.personId = PERSON_ID.getAndIncrement();
            this.name = name;
        }

        public int getPersonId() {
            return personId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) { this.name = name; }

        public Address getAddress() { return address; }

        public void setAddress(Address address) { this.address = address; }

        public String getEmail() { return email; }

        public void setEmail(String email) { this.email = email; }

        public String getPhone() { return phone; }

        public void setPhone(String phone) { this.phone = phone; }

        public Account getAccount() { return account; }

        public void setAccount(Account account) { this.account = account; }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{" + personId + ":" + name + "}";
        }
    }

    static class Guest extends Person {
        private final CopyOnWriteArrayList<RoomBooking> bookings = new CopyOnWriteArrayList<>();
        private final AtomicInteger totalRoomsCheckedIn = new AtomicInteger(0);

        public Guest(String name) {
            super(name);
        }

        public List<RoomBooking> getBookings() {
            return bookings;
        }

        public boolean addBooking(RoomBooking booking) {
            if (booking == null) return false;
            bookings.add(booking);
            return true;
        }

        public int incrementCheckedIn() {
            return totalRoomsCheckedIn.incrementAndGet();
        }
    }

    static class Receptionist extends Person {
        public Receptionist(String name) { super(name); }

        // naive search by name across a collection - in real system this would query a repository
        public List<Person> searchMember(Collection<Person> people, String name) {
            List<Person> results = new ArrayList<>();
            for (Person p : people) {
                if (p.getName() != null && p.getName().toLowerCase().contains(name.toLowerCase())) {
                    results.add(p);
                }
            }
            return results;
        }
    }

    static class Housekeeper extends Person {
        public Housekeeper(String name) { super(name); }
        public boolean assignToRoom(Room room) {
            if (room == null) return false;
            return room.addHousekeepingEntry("Assigned housekeeping", 30, this);
        }
    }

    // -------------------------
    // Notification (abstract)
    // -------------------------
    static abstract class Notification {
        private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
        protected final int notificationId;
        protected LocalDateTime createdOn;
        protected String content;

        private static final ExecutorService NOTIFY_POOL = Executors.newCachedThreadPool();

        public Notification() {
            this.notificationId = NEXT_ID.getAndIncrement();
            this.createdOn = LocalDateTime.now();
        }

        public int getNotificationId() { return notificationId; }

        public void setContent(String content) {
            this.content = content;
        }

        public void setCreatedOn(LocalDateTime createdOn) {
            this.createdOn = createdOn;
        }

        public abstract void sendNotification(Person person);

        protected void asyncRun(Runnable r) {
            NOTIFY_POOL.submit(r);
        }
    }

    static class SMSNotification extends Notification {
        @Override
        public void sendNotification(Person person) {
            asyncRun(() -> {
                // simulate sending SMS (non-blocking)
                System.out.println("[SMS] To: " + (person == null ? "unknown" : person.getPhone())
                        + " - " + content + " (id:" + notificationId + ")");
            });
        }
    }

    static class EmailNotification extends Notification {
        @Override
        public void sendNotification(Person person) {
            asyncRun(() -> {
                System.out.println("[Email] To: " + (person == null ? "unknown" : person.getEmail())
                        + " - " + content + " (id:" + notificationId + ")");
            });
        }
    }

    // -------------------------
    // BillTransaction and subtypes
    // -------------------------
    static abstract class BillTransaction {
        private static final AtomicLong TX_ID = new AtomicLong(1);
        private final long id = TX_ID.getAndIncrement();
        private volatile LocalDateTime creationDate;
        private volatile double amount;
        private final Object statusLock = new Object();
        private volatile PaymentStatus status = PaymentStatus.UNPAID;

        public long getId() { return id; }
        public LocalDateTime getCreationDate() { return creationDate; }
        public void setCreationDate(LocalDateTime creationDate) { this.creationDate = creationDate; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public PaymentStatus getStatus() { return status; }

        protected void setStatus(PaymentStatus newStatus) {
            synchronized (statusLock) {
                this.status = newStatus;
            }
        }

        public abstract void initiateTransaction();
    }

    static class CheckTransaction extends BillTransaction {
        private final String bankName;
        private final String checkNumber;

        public CheckTransaction(String bankName, String checkNumber) {
            this.bankName = bankName;
            this.checkNumber = checkNumber;
        }

        @Override
        public void initiateTransaction() {
            setStatus(PaymentStatus.PENDING);
            // Simulate bank validation
            try {
                Thread.sleep(200);
                setStatus(PaymentStatus.COMPLETED);
            } catch (InterruptedException e) {
                setStatus(PaymentStatus.DECLINED);
                Thread.currentThread().interrupt();
            }
        }
    }

    static class CreditCardTransaction extends BillTransaction {
        private final String nameOnCard;
        private final int zipcode;

        public CreditCardTransaction(String nameOnCard, int zipcode) {
            this.nameOnCard = nameOnCard;
            this.zipcode = zipcode;
        }

        @Override
        public void initiateTransaction() {
            setStatus(PaymentStatus.PENDING);
            // simulate gateway
            try {
                Thread.sleep(150);
                setStatus(PaymentStatus.COMPLETED);
            } catch (InterruptedException e) {
                setStatus(PaymentStatus.DECLINED);
                Thread.currentThread().interrupt();
            }
        }
    }

    static class CashTransaction extends BillTransaction {
        private double cashTendered;

        public CashTransaction() {}

        public void setCashTendered(double cashTendered) { this.cashTendered = cashTendered; }

        @Override
        public void initiateTransaction() {
            setStatus(PaymentStatus.PENDING);
            // cash is immediate
            if (cashTendered >= getAmount()) {
                setStatus(PaymentStatus.COMPLETED);
            } else {
                setStatus(PaymentStatus.DECLINED);
            }
        }
    }

    // -------------------------
    // RoomKey
    // -------------------------
    static class RoomKey {
        private final String keyId;
        private final String barcode;
        private final LocalDateTime issuedAt;
        private volatile boolean isActive;
        private volatile boolean isMaster;

        public RoomKey(String keyId, String barcode, boolean isMaster) {
            this.keyId = keyId;
            this.barcode = barcode;
            this.issuedAt = LocalDateTime.now();
            this.isActive = true;
            this.isMaster = isMaster;
        }

        public boolean assignRoom(Room room) {
            if (room == null) return false;
            room.addKey(this);
            return true;
        }

        public void deactivate() { this.isActive = false; }
    }

    // -------------------------
    // RoomHousekeeping entry
    // -------------------------
    static class RoomHousekeeping {
        private final String description;
        private final LocalDateTime startDatetime;
        private final int durationMinutes;
        private final Housekeeper housekeeper;

        public RoomHousekeeping(String description, int durationMinutes, Housekeeper housekeeper) {
            this.description = description;
            this.startDatetime = LocalDateTime.now();
            this.durationMinutes = durationMinutes;
            this.housekeeper = housekeeper;
        }

        @Override
        public String toString() {
            return "HK{" + description + " by " + (housekeeper == null ? "unknown" : housekeeper.getName()) + "}";
        }
    }

    // -------------------------
    // Room
    // -------------------------
    static class Room {
        private final String roomNumber;
        private final RoomStyle style;
        private volatile RoomStatus status;
        private volatile double bookingPrice;
        private final boolean isSmoking;
        private final List<RoomKey> keys = new CopyOnWriteArrayList<>();
        private final List<RoomHousekeeping> housekeepingLog = new CopyOnWriteArrayList<>();
        private final ReentrantLock lock = new ReentrantLock(true);

        public Room(String roomNumber, RoomStyle style, RoomStatus status, double price, boolean isSmoking) {
            this.roomNumber = roomNumber;
            this.style = style;
            this.status = status;
            this.bookingPrice = price;
            this.isSmoking = isSmoking;
        }

        public String getRoomNumber() { return roomNumber; }
        public RoomStyle getStyle() { return style; }
        public RoomStatus getStatus() { return status; }
        public double getBookingPrice() { return bookingPrice; }
        public void setBookingPrice(double p) { this.bookingPrice = p; }

        public List<RoomKey> getKeys() { return keys; }
        public List<RoomHousekeeping> getHousekeepingLog() { return housekeepingLog; }

        public void addKey(RoomKey key) { if (key != null) keys.add(key); }

        public boolean isRoomAvailable(LocalDate startDate, int durationDays) {
            // For demo, availability is derived from status only
            return status == RoomStatus.AVAILABLE;
        }

        public boolean checkin() {
            lock.lock();
            try {
                if (status != RoomStatus.AVAILABLE && status != RoomStatus.RESERVED) return false;
                status = RoomStatus.OCCUPIED;
                return true;
            } finally {
                lock.unlock();
            }
        }

        public boolean checkout() {
            lock.lock();
            try {
                if (status != RoomStatus.OCCUPIED) return false;
                status = RoomStatus.AVAILABLE;
                return true;
            } finally {
                lock.unlock();
            }
        }

        public boolean addHousekeepingEntry(String description, int durationMinutes, Housekeeper hk) {
            lock.lock();
            try {
                RoomHousekeeping entry = new RoomHousekeeping(description, durationMinutes, hk);
                housekeepingLog.add(entry);
                status = RoomStatus.BEING_SERVICED;
                return true;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public String toString() {
            return "Room{" + roomNumber + "," + style + "," + status + "," + bookingPrice + "}";
        }
    }

    // -------------------------
    // RoomBooking
    // -------------------------
    static class RoomBooking {
        private static final AtomicLong BOOKING_ID = new AtomicLong(10000);
        private final String reservationNumber;
        private final LocalDate startDate;
        private final int durationInDays;
        private volatile BookingStatus status;
        private volatile LocalDateTime checkin;
        private volatile LocalDateTime checkout;
        private final int guestId;
        private final Room room;
        private Invoice invoice;
        private final List<Notification> notifications = new CopyOnWriteArrayList<>();

        public RoomBooking(LocalDate startDate, int durationInDays, int guestId, Room room) {
            this.reservationNumber = "RS" + BOOKING_ID.getAndIncrement();
            this.startDate = startDate;
            this.durationInDays = durationInDays;
            this.status = BookingStatus.REQUESTED;
            this.guestId = guestId;
            this.room = room;
        }

        public String getReservationNumber() { return reservationNumber; }
        public LocalDate getStartDate() { return startDate; }
        public int getDurationInDays() { return durationInDays; }
        public BookingStatus getStatus() { return status; }
        public void setStatus(BookingStatus s) { this.status = s; }
        public Room getRoom() { return room; }
        public void attachInvoice(Invoice invoice) { this.invoice = invoice; }

        public void addNotification(Notification n) {
            if (n != null) notifications.add(n);
        }

        public void sendNotifications(Person p) {
            for (Notification n : notifications) n.sendNotification(p);
        }

        public void markCheckedIn() {
            this.checkin = LocalDateTime.now();
            this.status = BookingStatus.CONFIRMED;
        }

        public void markCheckedOut() {
            this.checkout = LocalDateTime.now();
            // keep status as confirmed, or set to cancelled/abandoned per business rules
        }

        @Override
        public String toString() {
            return "Booking{" + reservationNumber + ":" + room + "," + startDate + "," + durationInDays + "," + status + "}";
        }
    }

    // -------------------------
    // Invoice
    // -------------------------
    static class Invoice {
        private static final AtomicLong ID = new AtomicLong(1);
        private final long invoiceId = ID.getAndIncrement();
        private final double amount;
        private final LocalDateTime createdOn = LocalDateTime.now();

        public Invoice(double amount) { this.amount = amount; }

        public boolean createBill() {
            // persist or send to billing system - mocked here
            return amount >= 0;
        }

        @Override
        public String toString() {
            return "Invoice{" + invoiceId + ", " + amount + "}";
        }
    }

    // -------------------------
    // Search interface & Catalog
    // -------------------------
    interface Search {
        List<Room> search(RoomStyle style, LocalDate date, int duration);
    }

    static class Catalog implements Search {
        // Map roomNumber -> Room
        private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<>();

        public Catalog() { }

        public Collection<Room> getRooms() {
            return rooms.values();
        }

        public boolean addRoom(Room room) {
            if (room == null) return false;
            rooms.put(room.getRoomNumber(), room);
            return true;
        }

        public Room getRoomByNumber(String number) {
            return rooms.get(number);
        }

        @Override
        public List<Room> search(RoomStyle style, LocalDate date, int duration) {
            List<Room> result = new ArrayList<>();
            for (Room r : rooms.values()) {
                if (r.getStyle() == style && r.isRoomAvailable(date, duration)) {
                    result.add(r);
                }
            }
            return result;
        }

        /**
         * Attempts to reserve a room atomically. Returns booking when reserved and null otherwise.
         */
        public RoomBooking tryReserve(Room r, Guest guest, LocalDate start, int durationDays) {
            if (r == null || guest == null) return null;
            // lock per room to avoid races
            boolean locked = false;
            ReentrantLock lock = r.lock;
            try {
                locked = lock.tryLock(500, TimeUnit.MILLISECONDS);
                if (!locked) return null;
                if (!r.isRoomAvailable(start, durationDays)) return null;
                // mark reserved and create booking
                r.status = RoomStatus.RESERVED;
                RoomBooking booking = new RoomBooking(start, durationDays, guest.getPersonId(), r);
                booking.setStatus(BookingStatus.CONFIRMED);
                guest.addBooking(booking);
                return booking;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } finally {
                if (locked) lock.unlock();
            }
        }
    }

    // -------------------------
    // HotelBranch & Hotel
    // -------------------------
    static class HotelBranch {
        private final String name;
        private final Address location;
        private final Catalog catalog;

        public HotelBranch(String name, Address location) {
            this.name = name;
            this.location = location;
            this.catalog = new Catalog();
        }

        public List<Room> getRooms() {
            return new ArrayList<>(catalog.getRooms());
        }

        public Catalog getCatalog() { return catalog; }

        @Override
        public String toString() {
            return "HotelBranch{" + name + "}";
        }
    }

    static class Hotel {
        private final String name;
        private final CopyOnWriteArrayList<HotelBranch> locations = new CopyOnWriteArrayList<>();

        public Hotel(String name) { this.name = name; }

        public String getName() { return name; }

        public List<HotelBranch> getLocations() {
            return new ArrayList<>(locations);
        }

        public boolean addLocation(HotelBranch location) {
            if (location == null) return false;
            return locations.addIfAbsent(location);
        }
    }

    // -------------------------
    // Driver main
    // -------------------------
    public static void main(String[] args) throws Exception {
        System.out.println("=== Scenario: Hotel System demo (concurrency-safe) ===");

        Hotel hotel = new Hotel("Ocean View Resort");
        HotelBranch beachBranch = new HotelBranch("Beachside Branch",
                new Address("1 Seaside St", "Goa", "GA", 403001, "India"));
        HotelBranch cityBranch = new HotelBranch("City Center Branch",
                new Address("10 Central Ave", "Mumbai", "MH", 400001, "India"));

        hotel.addLocation(beachBranch);
        hotel.addLocation(cityBranch);

        // add rooms to beach branch
        Room r101 = new Room("101", RoomStyle.STANDARD, RoomStatus.AVAILABLE, 120.0, false);
        Room r102 = new Room("102", RoomStyle.STANDARD, RoomStatus.AVAILABLE, 115.0, false);
        Room r202 = new Room("202", RoomStyle.DELUXE, RoomStatus.AVAILABLE, 220.0, true);

        beachBranch.getCatalog().addRoom(r101);
        beachBranch.getCatalog().addRoom(r102);
        beachBranch.getCatalog().addRoom(r202);

        System.out.println("Hotel created: " + hotel.getName() + " with branches: " + hotel.getLocations().size());
        System.out.println("Beach Branch rooms: " + beachBranch.getCatalog().getRooms().size());

        // Guest and booking
        Guest alice = new Guest("Alice Smith");
        alice.setEmail("alice.smith@example.com");
        alice.setPhone("+91-9876543210");

        // Search for STANDARD rooms
        List<Room> searchResult = beachBranch.getCatalog().search(RoomStyle.STANDARD, LocalDate.now(), 2);
        System.out.println("Found STANDARD rooms: " + searchResult.size());

        // Reserve room using Catalog.tryReserve (thread-safe)
        RoomBooking booking = beachBranch.getCatalog().tryReserve(r101, alice, LocalDate.now(), 2);
        if (booking != null) {
            System.out.println("Booking successful: " + booking.getReservationNumber() + " for room " + r101.getRoomNumber());
            // attach invoice
            Invoice invoice = new Invoice(r101.getBookingPrice() * booking.getDurationInDays());
            booking.attachInvoice(invoice);

            // notifications
            EmailNotification email = new EmailNotification();
            email.setContent("Hello " + alice.getName() + ", your room booking is confirmed! Reservation: " + booking.getReservationNumber());
            booking.addNotification(email);

            SMSNotification sms = new SMSNotification();
            sms.setContent("Hi " + alice.getName() + ", booking confirmed. Res#: " + booking.getReservationNumber());
            booking.addNotification(sms);

            booking.sendNotifications(alice);
        } else {
            System.out.println("Booking failed: no room reserved.");
        }

        // Payment simulation
        CashTransaction tx = new CashTransaction();
        tx.setCreationDate(LocalDateTime.now());
        tx.setAmount(r101.getBookingPrice() * 2);
        tx.setCashTendered(tx.getAmount()); // exact cash
        tx.initiateTransaction();
        System.out.println("Payment status: " + tx.getStatus());

        // Try concurrent booking on same room to test locks
        System.out.println("\n=== Concurrency test: two guests trying to reserve room 102 simultaneously ===");
        Guest bob = new Guest("Bob Kumar");
        Guest carol = new Guest("Carol Roy");
        ExecutorService exec = Executors.newFixedThreadPool(2);
        Catalog catalog = beachBranch.getCatalog();

        Callable<String> attemptBob = () -> {
            RoomBooking b = catalog.tryReserve(r102, bob, LocalDate.now(), 1);
            return b == null ? "Bob failed to reserve" : "Bob reserved " + b.getReservationNumber();
        };
        Callable<String> attemptCarol = () -> {
            RoomBooking b = catalog.tryReserve(r102, carol, LocalDate.now(), 1);
            return b == null ? "Carol failed to reserve" : "Carol reserved " + b.getReservationNumber();
        };

        List<Future<String>> futures = exec.invokeAll(Arrays.asList(attemptBob, attemptCarol));
        for (Future<String> f : futures) System.out.println(f.get());
        exec.shutdown();

        // Demonstrate checkin / checkout
        System.out.println("\n=== Checkin/Checkout ===");
        boolean checkedIn = r101.checkin();
        System.out.println("Checkin r101 success? " + checkedIn + " status: " + r101.getStatus());
        boolean checkedOut = r101.checkout();
        System.out.println("Checkout r101 success? " + checkedOut + " status: " + r101.getStatus());

        // Housekeeping assignment
        Housekeeper hk = new Housekeeper("Raju");
        boolean hkAssigned = hk.assignToRoom(r101);
        System.out.println("Housekeeper assigned to r101? " + hkAssigned);
        System.out.println("r101 housekeeping log size: " + r101.getHousekeepingLog().size());

        System.out.println("\n✅ Demo finished.");
    }
}
