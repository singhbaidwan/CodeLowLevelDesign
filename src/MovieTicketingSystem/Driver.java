package MovieTicketingSystem;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.text.SimpleDateFormat;

// Enums
enum PaymentStatus { PENDING, CONFIRMED, DECLINED, REFUNDED }
enum BookingStatus { PENDING, CONFIRMED, CANCELLED, DENIED, REFUNDED }
enum SeatStatus { AVAILABLE, BOOKED, RESERVED }

// Person hierarchy
abstract class Person {
    public String name;
    public String address;
    public String phone;
    public String email;
}

class Customer extends Person {
    public List<Booking> bookings = new ArrayList<>();

    public boolean createBooking(Booking booking) { return bookings.add(booking); }
    public boolean updateBooking(Booking booking) { return true; }
    public boolean deleteBooking(Booking booking) { return bookings.remove(booking); }
}

class Admin extends Person {
    public boolean addShow(ShowTime show) { return true; }
    public boolean updateShow(ShowTime show) { return true; }
    public boolean deleteShow(ShowTime show) { return true; }
    public boolean addMovie(Movie movie) { return true; }
    public boolean deleteMovie(Movie movie) { return true; }
}

class TicketAgent extends Person {
    public boolean createBooking(Booking booking) { return true; }
}

// Seat with lock for concurrency
abstract class Seat {
    public String seatNo;
    public volatile SeatStatus status = SeatStatus.AVAILABLE;
    public double rate;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile Integer reservedByBookingId = null;

    public boolean isAvailable() { return status == SeatStatus.AVAILABLE; }

    public boolean tryReserve(int bookingId) {
        if (lock.tryLock()) {
            try {
                if (status == SeatStatus.AVAILABLE) {
                    status = SeatStatus.RESERVED;
                    reservedByBookingId = bookingId;
                    return true;
                }
            } finally {
                lock.unlock();
            }
        }
        return false;
    }

    public boolean release(int bookingId) {
        lock.lock();
        try {
            if (status == SeatStatus.RESERVED && Objects.equals(reservedByBookingId, bookingId)) {
                status = SeatStatus.AVAILABLE;
                reservedByBookingId = null;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean book(int bookingId) {
        lock.lock();
        try {
            if (status == SeatStatus.RESERVED && Objects.equals(reservedByBookingId, bookingId)) {
                status = SeatStatus.BOOKED;
                reservedByBookingId = null;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public abstract void setSeat();
    public abstract void setRate();
}

class Platinum extends Seat { public void setSeat() { } public void setRate() { this.rate = 20.0; } }
class Gold extends Seat     { public void setSeat() { } public void setRate() { this.rate = 15.0; } }
class Silver extends Seat   { public void setSeat() { } public void setRate() { this.rate = 10.0; } }

// Payment
abstract class Payment {
    public double amount;
    public Date timestamp;
    public PaymentStatus status;
    public abstract boolean makePayment();
}

class Cash extends Payment {
    public boolean makePayment() { status = PaymentStatus.CONFIRMED; return true; }
}

class CreditCard extends Payment {
    public String nameOnCard;
    public String cardNumber;
    public String billingAddress;
    public int code;
    public boolean makePayment() { status = PaymentStatus.CONFIRMED; return true; }
}

// Notification
abstract class Notification {
    public int notificationId;
    public Date createdOn;
    public String content;
    public abstract void sendNotification(Person person);
}

class EmailNotification extends Notification {
    public void sendNotification(Person person) { System.out.printf("[EMAIL] To: %s | %s\n", person.email, content); }
}
class PhoneNotification extends Notification {
    public void sendNotification(Person person) { System.out.printf("[SMS] To: %s | %s\n", person.phone, content); }
}

// Booking and ticket
class MovieTicket {
    public int ticketId;
    public Seat seat;
    public Movie movie;
    public ShowTime show;
    public MovieTicket(int ticketId, Seat seat, Movie movie, ShowTime show) {
        this.ticketId = ticketId; this.seat = seat; this.movie = movie; this.show = show; }
}

class Booking {
    public int bookingId;
    public double amount;
    public int totalSeats;
    public Date createdOn;
    public BookingStatus status;
    public Payment payment;
    public List<MovieTicket> tickets;
    public List<Seat> seats;

    public Booking(int bookingId, double amount, int totalSeats, Date createdOn,
                   BookingStatus status, Payment payment, List<MovieTicket> tickets, List<Seat> seats) {
        this.bookingId = bookingId;
        this.amount = amount;
        this.totalSeats = totalSeats;
        this.createdOn = createdOn;
        this.status = status;
        this.payment = payment;
        this.tickets = tickets;
        this.seats = seats;
    }
}

// Movie entities
class Movie {
    public String title; public String genre; public Date releaseDate; public String language; public int duration; public List<ShowTime> shows;
    public Movie(String title, String genre, Date releaseDate, String language, int duration, List<ShowTime> shows) {
        this.title = title; this.genre = genre; this.releaseDate = releaseDate; this.language = language; this.duration = duration; this.shows = shows; }
}

class ShowTime {
    public int showId; public Date startTime; public Date endTime; public int duration; public List<Seat> seats;
    public ShowTime(int showId, Date startTime, Date endTime, int duration, List<Seat> seats) { this.showId = showId; this.startTime = startTime; this.endTime = endTime; this.duration = duration; this.seats = seats; }
}

// Location
class Hall { public int hallId; public List<ShowTime> shows; public Hall(int hallId, List<ShowTime> shows) { this.hallId = hallId; this.shows = shows; } }
class Cinema { public int cinemaId; public List<Hall> halls; public City city; public Cinema(int cinemaId, List<Hall> halls, City city) { this.cinemaId = cinemaId; this.halls = halls; this.city = city; } }
class City { public String name; public String state; public int zip; public List<Cinema> cinemas; public City(String name, String state, int zip, List<Cinema> cinemas) { this.name = name; this.state = state; this.zip = zip; this.cinemas = cinemas; } }

// Catalog
class Catalog {
    public Map<String, List<Movie>> movieTitles = new ConcurrentHashMap<>();
    public List<Movie> searchMovieTitle(String title) { return movieTitles.getOrDefault(title, Collections.emptyList()); }
}

// SeatLockService - manages temporary reservations with expiry
class SeatLockService {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentMap<Integer, List<Seat>> locks = new ConcurrentHashMap<>();
    private final long lockDurationSeconds;

    public SeatLockService(long lockDurationSeconds) { this.lockDurationSeconds = lockDurationSeconds; }

    public boolean reserveSeats(int bookingId, List<Seat> seats) {
        List<Seat> reserved = new ArrayList<>();
        for (Seat s : seats) {
            boolean ok = s.tryReserve(bookingId);
            if (!ok) {
                for (Seat r : reserved) r.release(bookingId);
                return false;
            }
            reserved.add(s);
        }
        locks.put(bookingId, reserved);
        scheduler.schedule(() -> expireLock(bookingId), lockDurationSeconds, TimeUnit.SECONDS);
        return true;
    }

    private void expireLock(int bookingId) {
        List<Seat> ss = locks.remove(bookingId);
        if (ss != null) {
            for (Seat s : ss) s.release(bookingId);
            System.out.printf("[SeatLock] Lock expired for booking %d, seats released\n", bookingId);
        }
    }

    public boolean confirmSeats(int bookingId) {
        List<Seat> ss = locks.remove(bookingId);
        if (ss == null) return false;
        boolean allConfirmed = true;
        for (Seat s : ss) {
            boolean ok = s.book(bookingId);
            if (!ok) allConfirmed = false;
        }
        return allConfirmed;
    }

    public boolean releaseSeats(int bookingId) {
        List<Seat> ss = locks.remove(bookingId);
        if (ss == null) return false;
        for (Seat s : ss) s.release(bookingId);
        return true;
    }

    public void shutdown() { scheduler.shutdownNow(); }
}

// BookingService
class BookingService {
    private final SeatLockService seatLockService;
    private final AtomicInteger bookingIdGen = new AtomicInteger(3000);
    private final AtomicInteger ticketIdGen = new AtomicInteger(2000);

    public BookingService(SeatLockService seatLockService) { this.seatLockService = seatLockService; }

    public BookingResult tryCreateBooking(Customer customer, Movie movie, ShowTime show, List<String> seatNos, Payment payment) {
        int bookingId = bookingIdGen.incrementAndGet();
        List<Seat> chosen = new ArrayList<>();
        for (String sNo : seatNos) {
            Seat s = show.seats.stream().filter(x -> sNo.equals(x.seatNo)).findFirst().orElse(null);
            if (s == null) return new BookingResult(false, "Seat not found: " + sNo, null);
            chosen.add(s);
        }
        boolean reserved = seatLockService.reserveSeats(bookingId, chosen);
        if (!reserved) return new BookingResult(false, "Unable to reserve seats (already reserved/ booked)", null);

        payment.timestamp = new Date();
        payment.amount = chosen.stream().mapToDouble(x -> x.rate).sum();
        payment.status = PaymentStatus.PENDING;
        boolean paid = payment.makePayment();

        if (!paid || payment.status != PaymentStatus.CONFIRMED) {
            seatLockService.releaseSeats(bookingId);
            return new BookingResult(false, "Payment failed", null);
        }

        boolean confirmed = seatLockService.confirmSeats(bookingId);
        if (!confirmed) return new BookingResult(false, "Seat confirm failed", null);

        List<MovieTicket> tickets = new ArrayList<>();
        for (Seat s : chosen) tickets.add(new MovieTicket(ticketIdGen.incrementAndGet(), s, movie, show));

        Booking booking = new Booking(bookingId, payment.amount, chosen.size(), new Date(), BookingStatus.CONFIRMED, payment, tickets, chosen);
        customer.createBooking(booking);
        return new BookingResult(true, "Booking confirmed", booking);
    }

    public boolean cancelBooking(Customer customer, Booking booking) {
        boolean removed = customer.deleteBooking(booking);
        if (removed) {
            for (Seat s : booking.seats) {
                s.status = SeatStatus.AVAILABLE;
            }
            booking.status = BookingStatus.CANCELLED;
            return true;
        }
        return false;
    }
}

class BookingResult {
    public boolean success;
    public String message;
    public Booking booking;
    public BookingResult(boolean success, String message, Booking booking) { this.success = success; this.message = message; this.booking = booking; }
}

// Driver
public class Driver {
    public static void main(String[] args) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        System.out.println("===========================================");
        System.out.println("MOVIE BOOKING SYSTEM - DEMO");
        System.out.println("===========================================\n");

        City city = new City("Seattle", "WA", 98101, new ArrayList<>());
        Cinema cinema = new Cinema(1, new ArrayList<>(), city); city.cinemas.add(cinema);
        Hall hall = new Hall(101, new ArrayList<>()); cinema.halls.add(hall);

        Movie inception = new Movie("Inception", "Sci-Fi", parseDate("2010-07-16"), "English", 148, new ArrayList<>());

        List<Seat> seatsShow1 = new ArrayList<>();
        for (int i = 0; i < 4; i++) { Platinum p = new Platinum(); p.seatNo = "A" + (i+1); p.setRate(); seatsShow1.add(p); }
        for (int i = 0; i < 6; i++) { Gold g = new Gold(); g.seatNo = "B" + (i+1); g.setRate(); seatsShow1.add(g); }
        for (int i = 0; i < 10; i++) { Silver s = new Silver(); s.seatNo = "C" + (i+1); s.setRate(); seatsShow1.add(s); }

        ShowTime show1 = new ShowTime(1001, new Date(), new Date(), 150, seatsShow1);
        hall.shows.add(show1); inception.shows.add(show1);

        Catalog catalog = new Catalog();
        catalog.movieTitles.put(inception.title, Arrays.asList(inception));

        Admin admin = new Admin(); admin.name = "Bob (Admin)"; admin.email = "bob.admin@cinema.com";
        Customer customer = new Customer(); customer.name = "Alice (Customer)"; customer.email = "alice@example.com";
        TicketAgent agent = new TicketAgent(); agent.name = "Eve (Agent)"; agent.email = "eve@cinema.com";

        SeatLockService lockService = new SeatLockService(5);
        BookingService bookingService = new BookingService(lockService);

        System.out.println("SCENARIO 1: Search for 'Inception'");
        List<Movie> found = catalog.searchMovieTitle("Inception");
        for (Movie m : found) System.out.printf(" - Found: %s (%s)\n", m.title, sdf.format(m.releaseDate));

        System.out.println("\nSCENARIO 2: Customer attempts booking");
        List<String> want = Arrays.asList("A1", "B1");
        CreditCard cc = new CreditCard(); cc.nameOnCard = customer.name; cc.cardNumber = "1111-2222-3333-4444"; cc.billingAddress = "Addr"; cc.code = 123;
        BookingResult res = bookingService.tryCreateBooking(customer, inception, show1, want, cc);
        System.out.println("Result: " + res.message);
        if (res.success) {
            System.out.printf("Booking id %d confirmed for %s, seats: %s\n", res.booking.bookingId, customer.name, want);
        }

        System.out.println("\nSCENARIO 3: Agent tries to book a seat already taken (should fail)");
        List<String> agentWant = Arrays.asList("A1");
        Cash cash = new Cash();
        BookingResult res2 = bookingService.tryCreateBooking(customer, inception, show1, agentWant, cash);
        System.out.println("Agent booking result: " + res2.message);

        System.out.println("\nSCENARIO 4: Reserve seats but don't confirm payment, wait for expiry");
        int tempBookingId = 9999;
        List<Seat> toReserve = Arrays.asList(show1.seats.get(2), show1.seats.get(3));
        boolean r = lockService.reserveSeats(tempBookingId, toReserve);
        System.out.println("Temp reserve result: " + r + " (locks for 5 seconds)");
        System.out.println("Waiting 6 seconds for expiry..."); Thread.sleep(6000);

        System.out.println("Seat status after expiry: " + toReserve.get(0).seatNo + " -> " + toReserve.get(0).status);

        lockService.shutdown();
        System.out.println("\nDemo complete.");
    }

    public static Date parseDate(String dateStr) { try { return new SimpleDateFormat("yyyy-MM-dd").parse(dateStr); } catch (Exception ex) { return new Date(); } }
}
