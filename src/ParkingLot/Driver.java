package ParkingLot;

// ParkingSystemDemo.java
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.*;

// ----------------------------- Enums --------------------------------
enum PaymentStatus { COMPLETED, FAILED, PENDING, UNPAID, REFUNDED }
enum AccountStatus { ACTIVE, CLOSED, CANCELED, BLOCKLISTED, NONE }
enum TicketStatus { ISSUED, IN_USE, PAID, VALIDATED, CANCELED, REFUNDED }

enum SpotType { ACCESSIBLE, COMPACT, LARGE, MOTORCYCLE }
enum VehicleType { CAR, VAN, TRUCK, MOTORCYCLE }

// ----------------------------- Data Types ---------------------------
class Person {
    private String name;
    private String address;
    private String phone;
    private String email;
    public Person(String name) { this.name = name; }
    // getters/setters omitted for brevity
}

class Address {
    private int zipCode;
    private String street;
    private String city;
    private String state;
    private String country;
    public Address() {}
    // getters/setters omitted for brevity
}

// ----------------------------- Vehicles & Spots ---------------------
abstract class Vehicle {
    private final String licenseNo;
    private final VehicleType type;
    public Vehicle(String licenseNo, VehicleType type) {
        this.licenseNo = licenseNo;
        this.type = type;
    }
    public String getLicenseNo() { return licenseNo; }
    public VehicleType getVehicleType() { return type; }
}

class Car extends Vehicle { public Car(String l) { super(l, VehicleType.CAR); } }
class Van extends Vehicle { public Van(String l) { super(l, VehicleType.VAN); } }
class Truck extends Vehicle { public Truck(String l) { super(l, VehicleType.TRUCK); } }
class Motorcycle extends Vehicle { public Motorcycle(String l) { super(l, VehicleType.MOTORCYCLE); } }

abstract class ParkingSpot {
    private final int id;
    private final SpotType type;
    private volatile boolean occupied = false;
    private volatile String occupiedBy = null;
    private final Lock lock = new ReentrantLock();

    public ParkingSpot(int id, SpotType type) {
        this.id = id; this.type = type;
    }
    public int getId() { return id; }
    public SpotType getType() { return type; }

    public boolean tryOccupy(String license) {
        if (!lock.tryLock()) return false;
        try {
            if (occupied) return false;
            occupied = true;
            occupiedBy = license;
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void release() {
        lock.lock();
        try {
            occupied = false;
            occupiedBy = null;
        } finally {
            lock.unlock();
        }
    }

    public boolean isOccupied() { return occupied; }
    public String getOccupiedBy() { return occupiedBy; }
}

class Accessible extends ParkingSpot { public Accessible(int id) { super(id, SpotType.ACCESSIBLE); } }
class Compact extends ParkingSpot { public Compact(int id) { super(id, SpotType.COMPACT); } }
class Large extends ParkingSpot { public Large(int id) { super(id, SpotType.LARGE); } }
class MotorcycleSpot extends ParkingSpot { public MotorcycleSpot(int id) { super(id, SpotType.MOTORCYCLE); } }

// ----------------------------- Payment --------------------------------
abstract class Payment {
    protected double amount;
    protected PaymentStatus status = PaymentStatus.UNPAID;
    protected Instant timestamp;
    public Payment(double amount) { this.amount = amount; }
    public abstract boolean initiateTransaction();
    public PaymentStatus getStatus() { return status; }
    public double getAmount() { return amount; }
    public Instant getTimestamp() { return timestamp; }
}

class Cash extends Payment {
    public Cash(double amount) { super(amount); }
    public boolean initiateTransaction() {
        timestamp = Instant.now();
        status = PaymentStatus.COMPLETED; // immediate
        return true;
    }
}

class CreditCard extends Payment {
    public CreditCard(double amount) { super(amount); }
    public boolean initiateTransaction() {
        timestamp = Instant.now();
        // fake network delay to simulate remote gateway
        try { Thread.sleep(80); } catch (InterruptedException ignored) {}
        status = PaymentStatus.COMPLETED;
        return true;
    }
}

// ----------------------------- ParkingTicket --------------------------
class ParkingTicket {
    private final int ticketNo;
    private final Instant entryTime;
    private Instant exitTime;
    private double amount;
    private TicketStatus status;
    private final Vehicle vehicle;
    private Payment payment;
    private final int spotId;
    public ParkingTicket(int ticketNo, Vehicle vehicle, int spotId) {
        this.ticketNo = ticketNo;
        this.entryTime = Instant.now();
        this.status = TicketStatus.ISSUED;
        this.vehicle = vehicle;
        this.spotId = spotId;
    }
    public int getTicketNo() { return ticketNo; }
    public Instant getEntryTime() { return entryTime; }
    public void setExit(Instant exit, double amount) {
        this.exitTime = exit; this.amount = amount; this.status = TicketStatus.PAID;
    }
    public Vehicle getVehicle() { return vehicle; }
    public double getAmount() { return amount; }
    public void setPayment(Payment p) { this.payment = p; }
    public Payment getPayment() { return payment; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus s) { this.status = s; }
    public int getSpotId() { return spotId; }
    public Instant getExitTime() { return exitTime; }
}

// ----------------------------- ParkingRate ----------------------------
class ParkingRate {
    // Simple tiered rate: first hour flat, then per hour
    private final double firstHour;
    private final double perHour;
    public ParkingRate(double firstHour, double perHour) {
        this.firstHour = firstHour; this.perHour = perHour;
    }
    public double calculate(Duration duration, Vehicle vehicle, ParkingSpot spot) {
        long minutes = Math.max(1, duration.toMinutes());
        double hours = Math.ceil(minutes / 60.0);
        double base = firstHour + (hours - 1) * perHour;
        // multiply for larger vehicles
        if (vehicle.getVehicleType() == VehicleType.TRUCK) base *= 2.0;
        else if (vehicle.getVehicleType() == VehicleType.VAN) base *= 1.5;
        else if (vehicle.getVehicleType() == VehicleType.MOTORCYCLE) base *= 0.5;
        return base;
    }
}

// ----------------------------- Display Board -------------------------
class DisplayBoard {
    private int id;
    // snapshot of free counts per spot type
    public DisplayBoard(int id) { this.id = id; }
    public void showFreeSlot(Map<SpotType, Integer> freeCounts) {
        System.out.println("DisplayBoard #" + id + " free slots:");
        for (SpotType t : SpotType.values()) {
            int c = freeCounts.getOrDefault(t, 0);
            System.out.printf("  %s : %d%n", t, c);
        }
    }
}

// ----------------------------- ParkingLot (Singleton) ----------------
class ParkingLot {
    private final String name;
    private final Address address;
    private final ParkingRate parkingRate;

    // available queues per spot type (thread-safe)
    private final Map<SpotType, ConcurrentLinkedQueue<ParkingSpot>> availableMap = new EnumMap<>(SpotType.class);
    // occupied mapping: spotId -> ParkingSpot
    private final ConcurrentMap<Integer, ParkingSpot> allSpots = new ConcurrentHashMap<>();
    // tickets: ticketNo -> ParkingTicket
    private final ConcurrentMap<Integer, ParkingTicket> tickets = new ConcurrentHashMap<>();
    // counters
    private final AtomicInteger ticketCounter = new AtomicInteger(1000);
    private final AtomicInteger spotCounter = new AtomicInteger(1);

    // lock for critical allocation steps
    private final ReentrantLock allocationLock = new ReentrantLock();

    // display boards
    private final List<DisplayBoard> displayBoards = new CopyOnWriteArrayList<>();

    // singleton
    private static volatile ParkingLot instance;

    private ParkingLot(String name, Address address, ParkingRate rate) {
        this.name = name; this.address = address; this.parkingRate = rate;
        for (SpotType st : SpotType.values()) availableMap.put(st, new ConcurrentLinkedQueue<>());
    }

    public static ParkingLot getInstance() {
        if (instance == null) {
            synchronized (ParkingLot.class) {
                if (instance == null) {
                    instance = new ParkingLot("Default Lot", new Address(), new ParkingRate(10.0, 5.0));
                }
            }
        }
        return instance;
    }

    // add a parking spot
    public ParkingSpot addSpot(SpotType type) {
        int id = spotCounter.getAndIncrement();
        ParkingSpot spot;
        switch (type) {
            case ACCESSIBLE: spot = new Accessible(id); break;
            case COMPACT: spot = new Compact(id); break;
            case LARGE: spot = new Large(id); break;
            case MOTORCYCLE: spot = new MotorcycleSpot(id); break;
            default: spot = new Compact(id);
        }
        allSpots.put(spot.getId(), spot);
        availableMap.get(type).offer(spot);
        return spot;
    }

    public boolean addDisplayBoard(DisplayBoard b) {
        displayBoards.add(b);
        return true;
    }

    // allocate spot for incoming vehicle (thread-safe)
    public ParkingTicket parkVehicle(Vehicle v) {
        // choose candidate spot types by vehicle type (simple mapping)
        List<SpotType> preferred = new ArrayList<>();
        switch (v.getVehicleType()) {
            case MOTORCYCLE: preferred.add(SpotType.MOTORCYCLE); preferred.add(SpotType.COMPACT); break;
            case CAR: preferred.add(SpotType.COMPACT); preferred.add(SpotType.ACCESSIBLE); break;
            case VAN: preferred.add(SpotType.LARGE); preferred.add(SpotType.COMPACT); break;
            case TRUCK: preferred.add(SpotType.LARGE); break;
        }

        ParkingSpot allocated = null;
        allocationLock.lock(); // ensure allocation is atomic w.r.t others
        try {
            for (SpotType st : preferred) {
                ConcurrentLinkedQueue<ParkingSpot> q = availableMap.get(st);
                ParkingSpot spot;
                while ((spot = q.poll()) != null) {
                    // attempt to occupy
                    if (spot.tryOccupy(v.getLicenseNo())) {
                        allocated = spot;
                        break;
                    } else {
                        // if can't occupy, continue scanning
                    }
                }
                if (allocated != null) break;
            }
            if (allocated == null) {
                // no spot available for this vehicle
                return null;
            }
            int ticketNo = ticketCounter.getAndIncrement();
            ParkingTicket ticket = new ParkingTicket(ticketNo, v, allocated.getId());
            ticket.setStatus(TicketStatus.IN_USE);
            tickets.put(ticketNo, ticket);
            // update displays asynchronously (but safe) - we'll just call update directly
            updateDisplayBoards();
            return ticket;
        } finally {
            allocationLock.unlock();
        }
    }

    // when exiting: calculate fee, process payment, free spot
    public boolean exitVehicle(int ticketNo, Payment payment) {
        ParkingTicket ticket = tickets.get(ticketNo);
        if (ticket == null) return false;

        // ensure only one thread completes the payment for this ticket
        synchronized (ticket) {
            if (ticket.getStatus() == TicketStatus.VALIDATED || ticket.getStatus() == TicketStatus.REFUNDED) {
                return false; // already processed
            }
            Instant now = Instant.now();
            Duration duration = Duration.between(ticket.getEntryTime(), now);
            ParkingSpot spot = allSpots.get(ticket.getSpotId());
            double amount = parkingRate.calculate(duration, ticket.getVehicle(), spot);
            ticket.setExit(now, amount);
            ticket.setPayment(payment);
            boolean paid = payment.initiateTransaction();
            if (!paid || payment.getStatus() != PaymentStatus.COMPLETED) {
                ticket.setStatus(TicketStatus.CANCELED);
                return false;
            }
            ticket.setStatus(TicketStatus.VALIDATED);
            // free the spot
            spot.release();
            availableMap.get(spot.getType()).offer(spot);
            updateDisplayBoards();
            return true;
        }
    }

    public boolean isFullForVehicle(Vehicle v) {
        // quick check: attempt to find any free spot without removing
        List<SpotType> preferred = new ArrayList<>();
        switch (v.getVehicleType()) {
            case MOTORCYCLE: preferred.add(SpotType.MOTORCYCLE); preferred.add(SpotType.COMPACT); break;
            case CAR: preferred.add(SpotType.COMPACT); preferred.add(SpotType.ACCESSIBLE); break;
            case VAN: preferred.add(SpotType.LARGE); preferred.add(SpotType.COMPACT); break;
            case TRUCK: preferred.add(SpotType.LARGE); break;
        }
        for (SpotType st : preferred) {
            ConcurrentLinkedQueue<ParkingSpot> q = availableMap.get(st);
            if (q.peek() != null) return false;
            // else continue
        }
        return true;
    }

    public Map<SpotType, Integer> getFreeCounts() {
        Map<SpotType, Integer> m = new EnumMap<>(SpotType.class);
        for (SpotType st : SpotType.values()) {
            m.put(st, availableMap.get(st).size());
        }
        return m;
    }

    private void updateDisplayBoards() {
        Map<SpotType, Integer> snapshot = getFreeCounts();
        for (DisplayBoard db : displayBoards) {
            db.showFreeSlot(snapshot);
        }
    }

    public Map<Integer, ParkingSpot> getAllSpots() { return Collections.unmodifiableMap(allSpots); }
    public Map<Integer, ParkingTicket> getAllTickets() { return Collections.unmodifiableMap(tickets); }
}

// ----------------------------- Entrance & Exit -----------------------
class Entrance {
    private final int id;
    private final ParkingLot lot = ParkingLot.getInstance();
    public Entrance(int id) { this.id = id; }
    public ParkingTicket getTicket(Vehicle v) {
        if (lot.isFullForVehicle(v)) {
            System.out.println("Entrance #" + id + " : Parking full for vehicle " + v.getLicenseNo());
            return null;
        }
        ParkingTicket t = lot.parkVehicle(v);
        if (t != null)
            System.out.println("Entrance #" + id + " issued ticket #" + t.getTicketNo() + " for " + v.getLicenseNo());
        else
            System.out.println("Entrance #" + id + " failed to allocate spot for " + v.getLicenseNo());
        return t;
    }
}

class Exit {
    private final int id;
    private final ParkingLot lot = ParkingLot.getInstance();
    public Exit(int id) { this.id = id; }
    public void validateTicket(ParkingTicket ticket) {
        if (ticket == null) { System.out.println("Exit #" + id + " : null ticket"); return; }
        System.out.println("Exit #" + id + " validating ticket #" + ticket.getTicketNo() + " for " + ticket.getVehicle().getLicenseNo());
        // create payment (simulate user choosing cash)
        Payment payment = new Cash(0.0); // amount set at processing
        boolean success = lot.exitVehicle(ticket.getTicketNo(), payment);
        if (success) {
            System.out.printf("Exit #%d : Ticket %d validated. Charged: %.2f%n", id, ticket.getTicketNo(), ticket.getAmount());
        } else {
            System.out.printf("Exit #%d : Ticket %d validation failed.%n", id, ticket.getTicketNo());
        }
    }
}

// ----------------------------- Admin & Account -----------------------
abstract class Account {
    protected String userName;
    protected String password;
    protected Person person;
    protected AccountStatus status;

    public abstract boolean resetPassword();
}

class Admin extends Account {
    public Admin(String user) { this.userName = user; this.status = AccountStatus.ACTIVE; }
    public boolean addParkingSpot(ParkingSpot spot) { return true; }
    public boolean addDisplayBoard(DisplayBoard board) { ParkingLot.getInstance().addDisplayBoard(board); return true; }
    public boolean addEntrance(Entrance entrance) { return true; }
    public boolean addExit(Exit exit) { return true; }
    public boolean resetPassword() { this.password = "admin123"; return true; }
}

// ----------------------------- Demo Driver ---------------------------
public class Driver {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("\n====================== PARKING LOT SYSTEM DEMO ======================\n");

        ParkingLot lot = ParkingLot.getInstance();
        // create few spots
        lot.addSpot(SpotType.ACCESSIBLE);
        lot.addSpot(SpotType.COMPACT);
        lot.addSpot(SpotType.LARGE);
        lot.addSpot(SpotType.MOTORCYCLE);

        DisplayBoard board = new DisplayBoard(1);
        lot.addDisplayBoard(board);

        Entrance entrance = new Entrance(1);
        Exit exit = new Exit(1);

        // ----------------- SCENARIO 1: CUSTOMER ENTERS, PARKS -----------------
        System.out.println("\n→→→ SCENARIO 1: Customer enters and parks a car\n");

        Vehicle car = new Car("KA-01-HH-1234");
        System.out.println("-> Car " + car.getLicenseNo() + " arrives at entrance");
        ParkingTicket ticket1 = entrance.getTicket(car);

        System.out.println("-> Updating display board after parking:");
        board.showFreeSlot(lot.getFreeCounts());

        // ----------------- SCENARIO 2: CUSTOMER EXITS AND PAYS -----------------
        System.out.println("\n→→→ SCENARIO 2: Customer exits and pays\n");

        System.out.println("-> Car " + car.getLicenseNo() + " proceeds to exit panel");
        Thread.sleep(1500); // Simulate parking duration (1.5 sec)
        exit.validateTicket(ticket1);

        System.out.println("-> Updating display board after exit:");
        board.showFreeSlot(lot.getFreeCounts());

        // --------- SCENARIO 3: MULTI-THREADED ENTRY -------------------------
        System.out.println("\n→→→ SCENARIO 3: Multiple customers attempt to enter concurrently\n");

        // Create vehicles
        List<Vehicle> vehicles = Arrays.asList(
                new Van("KA-01-HH-9999"),
                new Motorcycle("KA-02-XX-3333"),
                new Truck("KA-04-AA-9998"),
                new Car("DL-09-YY-1234"),
                new Car("UP-01-CC-1001")
        );

        ExecutorService exec = Executors.newFixedThreadPool(4);
        List<Future<ParkingTicket>> futures = new ArrayList<>();
        for (Vehicle v : vehicles) {
            futures.add(exec.submit(() -> entrance.getTicket(v)));
        }

        // collect
        List<ParkingTicket> issued = new ArrayList<>();
        for (Future<ParkingTicket> f : futures) {
            try { ParkingTicket t = f.get(); if (t != null) issued.add(t); } catch (Exception e) { e.printStackTrace(); }
        }

        System.out.println("-> Updating display board after several parkings:");
        board.showFreeSlot(lot.getFreeCounts());

        // Now randomly exit some tickets concurrently
        List<Future<?>> exitFutures = new ArrayList<>();
        for (ParkingTicket t : issued) {
            exitFutures.add(exec.submit(() -> {
                try { Thread.sleep(ThreadLocalRandom.current().nextInt(200, 1200)); } catch (InterruptedException ignored) {}
                Exit ex = new Exit(2);
                ex.validateTicket(t);
            }));
        }

        for (Future<?> f : exitFutures) {
            try { f.get(); } catch (Exception e) { e.printStackTrace(); }
        }

        exec.shutdown();
        exec.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("-> Final display:");
        board.showFreeSlot(lot.getFreeCounts());

        System.out.println("\n====================== END OF DEMONSTRATION ======================\n");
    }
}
