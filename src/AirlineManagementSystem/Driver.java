package AirlineManagementSystem;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;

/* ----------------------------- Address & Enums ------------------------------ */

class Address {
    private final int zipCode;
    private final String streetAddress;
    private final String city;
    private final String state;
    private final String country;

    public Address(int zipCode, String streetAddress, String city, String state, String country) {
        this.zipCode = zipCode;
        this.streetAddress = streetAddress;
        this.city = city;
        this.state = state;
        this.country = country;
    }

    @Override
    public String toString() {
        return streetAddress + ", " + city + ", " + state + " - " + zipCode + ", " + country;
    }
}

enum AccountStatus { ACTIVE, DISABLED, CLOSED, BLOCKED }
enum SeatStatus { AVAILABLE, BOOKED, CHANCE }
enum SeatType { REGULAR, ACCESSIBLE, EMERGENCY_EXIT, EXTRA_LEG_ROOM }
enum SeatClass { ECONOMY, ECONOMY_PLUS, BUSINESS, FIRST_CLASS }
enum FlightStatus { ACTIVE, SCHEDULED, DELAYED, LANDED, DEPARTED, CANCELED, DIVERTED, UNKNOWN }
enum ReservationStatus { REQUESTED, PENDING, CONFIRMED, CHECKED_IN, CANCELED }
enum PaymentStatus { PENDING, COMPLETED, FAILED, DECLINED, CANCELED, REFUNDED }

/* ----------------------------- Account & Person --------------------------- */

class Account {
    private final int accountId;
    private String username;
    private volatile String password;
    private volatile AccountStatus status = AccountStatus.ACTIVE;
    private final ReentrantLock lock = new ReentrantLock();

    public Account(int accountId, String username, String password) {
        this.accountId = accountId;
        this.username = username;
        this.password = password;
    }

    public int getAccountId() { return accountId; }
    public String getUsername() { return username; }
    public AccountStatus getStatus() { return status; }

    public boolean resetPassword(String newPassword) {
        lock.lock();
        try {
            if (status != AccountStatus.ACTIVE) return false;
            this.password = newPassword;
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean disable() {
        lock.lock();
        try {
            status = AccountStatus.DISABLED;
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean enable() {
        lock.lock();
        try {
            status = AccountStatus.ACTIVE;
            return true;
        } finally {
            lock.unlock();
        }
    }
}

abstract class Person {
    protected String name;
    protected Address address;
    protected String email;
    protected String phone;
    protected Account account;

    public Person(String name, Address address, String email, String phone, Account account) {
        this.name = name;
        this.address = address;
        this.email = email;
        this.phone = phone;
        this.account = account;
    }

    public String getName() { return name; }
}

/* ----------------------------- Notification ------------------------------- */

abstract class Notification {
    private final int notificationId;
    private final Date createdOn;
    private final String content;
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    public Notification(String content) {
        this.notificationId = NEXT_ID.getAndIncrement();
        this.createdOn = new Date();
        this.content = content;
    }

    public abstract void sendNotification(Account account);

    public String getContent() { return content; }
}

class SmsNotification extends Notification {
    public SmsNotification(String content) { super(content); }
    @Override
    public void sendNotification(Account account) {
        System.out.println("[SMS] To account " + account.getAccountId() + ": " + getContent());
    }
}

class EmailNotification extends Notification {
    public EmailNotification(String content) { super(content); }
    @Override
    public void sendNotification(Account account) {
        System.out.println("[Email] To account " + account.getAccountId() + ": " + getContent());
    }
}

/* ----------------------------- Seat & Flight ------------------------------ */

class Seat {
    private final String seatNumber;
    private final SeatType type;
    private final SeatClass seatClass;

    public Seat(String seatNumber, SeatType type, SeatClass seatClass) {
        this.seatNumber = seatNumber;
        this.type = type;
        this.seatClass = seatClass;
    }

    public String getSeatNumber() { return seatNumber; }
    public SeatType getType() { return type; }
    public SeatClass getSeatClass() { return seatClass; }
}

class FlightSeat extends Seat {
    private volatile double fare;
    private volatile SeatStatus status;
    private volatile String reservationNumber;
    private final ReentrantLock lock = new ReentrantLock();

    public FlightSeat(String seatNumber, SeatType type, SeatClass seatClass, double fare) {
        super(seatNumber, type, seatClass);
        this.fare = fare;
        this.status = SeatStatus.AVAILABLE;
    }

    public boolean reserve(String reservationNumber) {
        lock.lock();
        try {
            if (this.status == SeatStatus.AVAILABLE) {
                this.status = SeatStatus.BOOKED;
                this.reservationNumber = reservationNumber;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean cancelReservation(String reservationNumber) {
        lock.lock();
        try {
            if (Objects.equals(this.reservationNumber, reservationNumber)) {
                this.status = SeatStatus.AVAILABLE;
                this.reservationNumber = null;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public SeatStatus getStatus() { return status; }
    public double getFare() { return fare; }
    public String getReservationNumber() { return reservationNumber; }

    @Override
    public String toString() {
        return getSeatNumber() + " [" + getSeatClass() + "] - " + status;
    }
}

class Flight {
    private final String flightNo;
    private final int durationMin;
    private final Airport departure;
    private final Airport arrival;
    private final List<FlightInstance> instances = new CopyOnWriteArrayList<>();

    public Flight(String flightNo, int durationMin, Airport departure, Airport arrival) {
        this.flightNo = flightNo;
        this.durationMin = durationMin;
        this.departure = departure;
        this.arrival = arrival;
    }

    public String getFlightNo() { return flightNo; }
    public Airport getDeparture() { return departure; }
    public Airport getArrival() { return arrival; }
    public List<FlightInstance> getInstances() { return instances; }

    public void addInstance(FlightInstance fi) { instances.add(fi); }
}

class FlightInstance {
    private final Flight flight;
    private final Date departureTime;
    private final String gate;
    private volatile FlightStatus status;
    private final Aircraft aircraft;
    private final List<FlightSeat> seats = Collections.synchronizedList(new ArrayList<>());

    public FlightInstance(Flight flight, Date departureTime, String gate, FlightStatus status, Aircraft aircraft) {
        this.flight = flight;
        this.departureTime = departureTime;
        this.gate = gate;
        this.status = status;
        this.aircraft = aircraft;
    }

    public Flight getFlight() { return flight; }
    public Date getDepartureTime() { return departureTime; }
    public String getGate() { return gate; }
    public FlightStatus getStatus() { return status; }
    public Aircraft getAircraft() { return aircraft; }
    public List<FlightSeat> getSeats() { return seats; }

    public void addSeat(FlightSeat seat) { seats.add(seat); }
}

/* ----------------------------- Reservation & Payment ---------------------- */

class Passenger {
    private final int passengerId;
    private final String name;
    private final String gender;
    private final Date dateOfBirth;
    private final String passportNumber;

    public Passenger(int passengerId, String name, String gender, Date dateOfBirth, String passportNumber) {
        this.passengerId = passengerId;
        this.name = name;
        this.gender = gender;
        this.dateOfBirth = dateOfBirth;
        this.passportNumber = passportNumber;
    }

    public String getName() { return name; }
}

class FlightReservation {
    private final String reservationNumber;
    private final FlightInstance flightInstance;
    private final Map<Passenger, FlightSeat> seatMap;
    private volatile ReservationStatus status;
    private final Date creationDate;
    private static final ConcurrentMap<String, FlightReservation> RESERVATIONS = new ConcurrentHashMap<>();

    public FlightReservation(String reservationNumber, FlightInstance flightInstance, Map<Passenger, FlightSeat> seatMap, ReservationStatus status, Date creationDate) {
        this.reservationNumber = reservationNumber;
        this.flightInstance = flightInstance;
        this.seatMap = new ConcurrentHashMap<>(seatMap);
        this.status = status;
        this.creationDate = creationDate;
        RESERVATIONS.put(reservationNumber, this);
    }

    public static FlightReservation fetchReservationDetails(String reservationNumber) {
        return RESERVATIONS.get(reservationNumber);
    }

    public Map<Passenger, FlightSeat> getSeatMap() { return seatMap; }
}

abstract class Payment {
    private final int paymentId;
    protected final double amount;
    protected volatile PaymentStatus status;
    private final Date timestamp;
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    protected Payment(double amount) {
        this.paymentId = NEXT_ID.getAndIncrement();
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.timestamp = new Date();
    }

    // ✅ fixed: removed 'synchronized' modifier
    public abstract boolean makePayment();

    public PaymentStatus getStatus() { return status; }
}

class Cash extends Payment {
    public Cash(double amount) { super(amount); }

    @Override
    public synchronized boolean makePayment() {
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        status = PaymentStatus.COMPLETED;
        return true;
    }
}

class CreditCard extends Payment {
    private final String nameOnCard;
    private final String cardNumber;

    public CreditCard(double amount, String nameOnCard, String cardNumber) {
        super(amount);
        this.nameOnCard = nameOnCard;
        this.cardNumber = cardNumber;
    }

    @Override
    public synchronized boolean makePayment() {
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        if (cardNumber == null || cardNumber.length() < 12) {
            status = PaymentStatus.DECLINED;
            return false;
        }
        status = PaymentStatus.COMPLETED;
        return true;
    }
}

/* ----------------------------- Airline & Roles ---------------------------- */

class Aircraft {
    private final String name;
    private final String code;
    private final String model;
    private final int seatCapacity;
    private final List<Seat> seats;

    public Aircraft(String name, String code, String model, int seatCapacity, List<Seat> seats) {
        this.name = name;
        this.code = code;
        this.model = model;
        this.seatCapacity = seatCapacity;
        this.seats = seats;
    }

    public String getName() { return name; }
    public List<Seat> getSeats() { return seats; }
}

class Airport {
    private final String name;
    private final String code;
    private final Address address;
    private final List<Flight> flights = new CopyOnWriteArrayList<>();

    public Airport(String name, String code, Address address) {
        this.name = name;
        this.code = code;
        this.address = address;
    }

    public String getName() { return name; }
    public String getCode() { return code; }
    public List<Flight> getFlights() { return flights; }
    public void addFlight(Flight flight) { flights.add(flight); }
}

class Airline {
    private final List<Flight> flights = new CopyOnWriteArrayList<>();
    private final List<Aircraft> aircrafts = new CopyOnWriteArrayList<>();

    private static volatile Airline instance = null;

    private Airline() {}

    public static Airline getInstance() {
        if (instance == null) {
            synchronized (Airline.class) {
                if (instance == null) instance = new Airline();
            }
        }
        return instance;
    }

    public void addFlight(Flight f) { flights.add(f); }
    public void addAircraft(Aircraft a) { aircrafts.add(a); }
}

class CrewMember extends Person {
    private final List<FlightInstance> assigned = new CopyOnWriteArrayList<>();
    public CrewMember(String name, Address address, String email, String phone, Account account) {
        super(name, address, email, phone, account);
    }
    public List<FlightInstance> viewSchedule() { return assigned; }
    public void assign(FlightInstance fi) { assigned.add(fi); }
}

class Admin extends Person {
    public Admin(String name, Address address, String email, String phone, Account account) {
        super(name, address, email, phone, account);
    }
    public boolean addAircraft(Aircraft a) { Airline.getInstance().addAircraft(a); return true; }
    public boolean addFlight(Flight f) { Airline.getInstance().addFlight(f); f.getDeparture().addFlight(f); return true; }
    public boolean assignCrew(CrewMember c, FlightInstance fi) { c.assign(fi); return true; }
}

class Customer extends Person {
    private final int customerId;

    public Customer(int customerId, String name, Address address, String email, String phone, Account account) {
        super(name, address, email, phone, account);
        this.customerId = customerId;
    }

    public FlightReservation createReservation(FlightInstance fi, List<Passenger> passengers, String reservationNumber) {
        Map<Passenger, FlightSeat> seatMap = new HashMap<>();
        for (Passenger p : passengers) {
            for (FlightSeat s : fi.getSeats()) {
                if (s.reserve(reservationNumber)) {
                    seatMap.put(p, s);
                    break;
                }
            }
        }
        if (seatMap.isEmpty()) return null;
        return new FlightReservation(reservationNumber, fi, seatMap, ReservationStatus.CONFIRMED, new Date());
    }

    public boolean makePayment(Payment payment) {
        return payment.makePayment();
    }
}

/* ----------------------------- Driver (Main) ------------------------------ */

public class Driver {

    private static Date createDate(int y, int m, int d) {
        Calendar c = Calendar.getInstance();
        c.set(y, m - 1, d);
        return c.getTime();
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("== Initializing system... ==");

        Address addr1 = new Address(12345, "Street 1", "CityA", "StateA", "CountryA");
        Address addr2 = new Address(67890, "Street 2", "CityB", "StateB", "CountryB");

        Airport a1 = new Airport("Alpha", "ALP", addr1);
        Airport a2 = new Airport("Beta", "BET", addr2);

        List<Seat> baseSeats = new ArrayList<>();
        for (int i = 1; i <= 5; i++)
            baseSeats.add(new Seat("A" + i, SeatType.REGULAR, SeatClass.ECONOMY));

        Aircraft plane = new Aircraft("Boeing", "B737", "737", 5, baseSeats);
        Airline.getInstance().addAircraft(plane);

        Account custAcc = new Account(1, "user", "pw");
        Account adminAcc = new Account(2, "admin", "adminpw");
        Account crewAcc = new Account(3, "crew", "crew123");

        Customer cust = new Customer(1, "John", addr1, "john@x.com", "123", custAcc);
        Admin admin = new Admin("Alice", addr2, "admin@x.com", "456", adminAcc);
        CrewMember crew = new CrewMember("Captain", addr1, "crew@x.com", "789", crewAcc);

        Flight flight = new Flight("FL123", 120, a1, a2);
        FlightInstance inst = new FlightInstance(flight, new Date(System.currentTimeMillis() + 3600000), "G1", FlightStatus.SCHEDULED, plane);

        for (Seat s : plane.getSeats())
            inst.addSeat(new FlightSeat(s.getSeatNumber(), s.getType(), s.getSeatClass(), 150.0));

        flight.addInstance(inst);
        admin.addFlight(flight);
        admin.assignCrew(crew, inst);

        System.out.println("Setup complete. Starting booking...");

        Passenger p1 = new Passenger(1, "John", "M", createDate(1990, 1, 1), "P123");
        FlightReservation res = cust.createReservation(inst, List.of(p1), "RES001");

        if (res != null) {
            System.out.println("Reserved seat for: " + p1.getName());
            CreditCard card = new CreditCard(150.0, "John", "4111111111111111");
            cust.makePayment(card);
            System.out.println("Payment: " + card.getStatus());
        }

        System.out.println("== System ready and running ==");
    }
}
