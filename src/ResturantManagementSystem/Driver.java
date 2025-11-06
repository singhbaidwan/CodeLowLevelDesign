package ResturantManagementSystem;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

// ------------------------ ENUMS ------------------------
enum PaymentStatus { UNPAID, DECLINED, PAID }
enum TableStatus { FREE, RESERVED, OCCUPIED }
enum OrderStatus { RECEIVED, PREPARING, COMPLETE }
enum ReservationStatus { REQUESTED, CONFIRMED, CHECKED_IN, CANCELED }

// ------------------------ ADDRESS ------------------------
class Address {
    String zipCode, address, city, state, country;
    Address(String zipCode, String address, String city, String state, String country) {
        this.zipCode = zipCode;
        this.address = address;
        this.city = city;
        this.state = state;
        this.country = country;
    }
}

// ------------------------ PERSONS ------------------------
abstract class Person {
    String name, email, password, phone;
    Address address;
    Person(String name, String email, String password, Address address, String phone) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.address = address;
        this.phone = phone;
    }
    String getName() { return name; }
}

class Customer extends Person {
    Customer(String n, String e, String p, Address a, String ph) { super(n, e, p, a, ph); }
}

class Receptionist extends Person {
    private final Restaurant restaurant;
    Receptionist(String n, String e, String p, Address a, String ph, Restaurant r) { super(n, e, p, a, ph); this.restaurant = r; }

    boolean confirmAndSaveReservation(Reservation res) {
        return restaurant.tryConfirmReservation(res);
    }
}

class Waiter extends Person {
    private final Restaurant restaurant;
    Waiter(String n, String e, String p, Address a, String ph, Restaurant r) { super(n, e, p, a, ph); this.restaurant = r; }

    Bill generateBill(int orderId) { return restaurant.generateBill(orderId); }
}

class Manager extends Person {
    private final Restaurant restaurant;
    Manager(String n, String e, String p, Address a, String ph, Restaurant r) { super(n, e, p, a, ph); this.restaurant = r; }
    Table addTable(int capacity, String location, int seats) { return restaurant.addTable(capacity, location, seats); }
}

// ------------------------ TABLE ------------------------
class Table {
    int tableID, maxCapacity, numberOfSeats;
    String locationIdentifier;
    private TableStatus status;
    private final ReentrantLock lock = new ReentrantLock(true);

    Table(int id, TableStatus s, int max, String loc, int seats) {
        this.tableID = id; this.status = s; this.maxCapacity = max;
        this.locationIdentifier = loc; this.numberOfSeats = seats;
    }

    boolean isFree() { return status == TableStatus.FREE; }
    void setStatus(TableStatus s) { status = s; }
    TableStatus getStatus() { return status; }
    int getTableID() { return tableID; }

    boolean tryReserve() {
        if (lock.tryLock()) {
            try {
                if (status == TableStatus.FREE) {
                    status = TableStatus.RESERVED;
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        }
        return false;
    }
}

// ------------------------ MENU ------------------------
class MenuItem {
    int id;
    String title, description;
    double price;
    MenuItem(int id, String t, String d, double p) {
        this.id = id;
        this.title = t;
        this.description = d;
        this.price = p; }
    double getPrice() { return price; }
}

class MenuSection {
    int id;
    String title, description;
    List<MenuItem> items = new ArrayList<>();
    MenuSection(int id, String t, String d) { this.id = id; this.title = t; this.description = d; }
    void addItem(MenuItem item) { items.add(item); }
}

class Menu {
    int id;
    String title, description;
    List<MenuSection> sections = new ArrayList<>();
    Menu(int id, String t, String d) { this.id = id; this.title = t; this.description = d; }
    void addSection(MenuSection sec) { sections.add(sec); }
}

// ------------------------ ORDER SYSTEM ------------------------
class MealItem {
    int id, quantity;
    MenuItem menuItem;
    MealItem(int id, int qty, MenuItem item) { this.id = id; this.quantity = qty; this.menuItem = item; }
    double getCost() { return menuItem.getPrice() * quantity; }
}

class Meal {
    int id;
    Table table;
    List<MealItem> items = new ArrayList<>();
    Meal(int id, Table t) { this.id = id; this.table = t; }
    void addItem(MealItem mi) { items.add(mi); }
}

class Order {
    int id;
    OrderStatus status;
    Date creationTime;
    List<Meal> meals = new ArrayList<>();
    Table table;
    Waiter waiter;

    Order(int id, OrderStatus s, Date d, Table t, Waiter w) {
        this.id = id; this.status = s; this.creationTime = d; this.table = t; this.waiter = w;
    }

    synchronized void addMeal(Meal m) { meals.add(m); }
    List<Meal> getMeals() { return meals; }
}

// ------------------------ RESERVATION ------------------------
class Reservation {
    int id;
    Date time;
    int peopleCount;
    ReservationStatus status;
    Customer customer;
    Table table;

    Reservation(int id, Date time, int people, ReservationStatus s, Customer c, Table t) {
        this.id = id; this.time = time; this.peopleCount = people;
        this.status = s; this.customer = c; this.table = t;
    }
    void setStatus(ReservationStatus s) { this.status = s; }
}

// ------------------------ BILLING ------------------------
class Bill {
    int id;
    double amount, tax;
    boolean isPaid;
    Bill(int id, double amt, double tax) { this.id = id; this.amount = amt; this.tax = tax; this.isPaid = false; }
}

abstract class Payment {
    int id;
    Date date;
    double amount;
    PaymentStatus status;

    Payment(int id, Date date, double amt, PaymentStatus st) {
        this.id = id; this.date = date; this.amount = amt; this.status = st;
    }

    abstract void processPayment();
    abstract void updateTableStatus(Table t);
}

class CreditCard extends Payment {
    String name;
    CreditCard(int id, Date d, double amt, PaymentStatus s, String n) { super(id, d, amt, s); this.name = n; }
    void processPayment() { this.status = PaymentStatus.PAID; }
    void updateTableStatus(Table t) { t.setStatus(TableStatus.FREE); }
}

// ------------------------ RESTAURANT CORE ------------------------
class Restaurant {
    private final ConcurrentHashMap<Integer, Table> tables = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Order> orders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Reservation> reservations = new ConcurrentHashMap<>();
    private static final AtomicInteger TABLE_ID = new AtomicInteger(100);
    private static final AtomicInteger ORDER_ID = new AtomicInteger(1);
    private static final AtomicInteger RES_ID = new AtomicInteger(1);

    Table addTable(int cap, String loc, int seats) {
        int id = TABLE_ID.getAndIncrement();
        Table t = new Table(id, TableStatus.FREE, cap, loc, seats);
        tables.put(id, t);
        return t;
    }

    boolean tryConfirmReservation(Reservation res) {
        Table t = res.table;
        synchronized (t) {
            if (t.isFree()) {
                t.setStatus(TableStatus.RESERVED);
                res.setStatus(ReservationStatus.CONFIRMED);
                reservations.put(res.id, res);
                return true;
            }
            return false;
        }
    }

    Order createOrder(Table t, Waiter w) {
        int id = ORDER_ID.getAndIncrement();
        Order o = new Order(id, OrderStatus.RECEIVED, new Date(), t, w);
        orders.put(id, o);
        return o;
    }

    Bill generateBill(int orderId) {
        Order o = orders.get(orderId);
        double subtotal = 0;
        for (Meal m : o.getMeals())
            for (MealItem mi : m.items)
                subtotal += mi.getCost();
        double tax = subtotal * 0.1;
        return new Bill(orderId, subtotal, tax);
    }

    Collection<Table> getAllTables() { return tables.values(); }
}

// ------------------------ DRIVER ------------------------
public class Driver {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Restaurant Management System Simulation ===");

        Restaurant restaurant = new Restaurant();
        Address addr = new Address("12345", "Main St", "Springfield", "IL", "USA");

        Manager manager = new Manager("Olivia", "olivia@r.com", "pwd", addr, "555-8888", restaurant);
        Receptionist receptionist = new Receptionist("Emma", "emma@r.com", "pwd", addr, "555-4567", restaurant);
        Waiter waiter = new Waiter("John", "john@r.com", "pwd", addr, "555-9876", restaurant);
        Customer alice = new Customer("Alice", "alice@x.com", "p", addr, "555-1122");
        Customer bob = new Customer("Bob", "bob@x.com", "p", addr, "555-2233");

        Table table = manager.addTable(4, "Window", 4);

        System.out.println("[1] Created table: " + table.getTableID());

        // --- Concurrent Reservations Simulation ---
        System.out.println("\n--- SCENARIO 1: Concurrent Reservations ---");
        ExecutorService exec = Executors.newFixedThreadPool(2);

        Callable<Boolean> task1 = () -> receptionist.confirmAndSaveReservation(
                new Reservation(1, new Date(), 2, ReservationStatus.REQUESTED, alice, table));

        Callable<Boolean> task2 = () -> receptionist.confirmAndSaveReservation(
                new Reservation(2, new Date(), 2, ReservationStatus.REQUESTED, bob, table));

        List<Future<Boolean>> results = exec.invokeAll(Arrays.asList(task1, task2));
        exec.shutdown();

        for (int i = 0; i < results.size(); i++) {
            boolean confirmed = false;
            try {
                confirmed = results.get(i).get();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Customer " + (i + 1) + " reservation confirmed? " + confirmed);
        }

        System.out.println("Table final status: " + table.getStatus());

        // --- Order and Payment Flow ---
        System.out.println("\n--- SCENARIO 2: Order & Payment ---");
        MenuItem pizza = new MenuItem(1, "Pizza", "Cheesy pizza", 12);
        MenuItem pasta = new MenuItem(2, "Pasta", "Creamy Alfredo", 10);

        Order order = restaurant.createOrder(table, waiter);
        Meal meal = new Meal(1, table);
        meal.addItem(new MealItem(1, 1, pizza));
        meal.addItem(new MealItem(2, 1, pasta));
        order.addMeal(meal);

        Bill bill = waiter.generateBill(order.id);
        double total = bill.amount + bill.tax;
        System.out.println("Bill generated: $" + total);

        CreditCard payment = new CreditCard(1, new Date(), total, PaymentStatus.UNPAID, alice.getName());
        payment.processPayment();
        payment.updateTableStatus(table);

        System.out.println("Payment completed. Table status: " + table.getStatus());
        System.out.println("\n=== Simulation Completed ===");
    }
}
