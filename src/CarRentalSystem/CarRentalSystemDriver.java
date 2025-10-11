package CarRentalSystem;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.text.SimpleDateFormat;

/* ---------------------------
   Enums
   --------------------------- */
enum MotorcycleType { STANDARD, CRUISER, TOURING, SPORTS, OFF_ROAD, DUAL_PURPOSE }
enum AccountStatus { ACTIVE, CLOSED, CANCELED, BLACKLISTED, BLOCKED }
enum ReservationStatus { ACTIVE, PENDING, CONFIRMED, COMPLETED, CANCELED }
enum PaymentStatus { UNPAID, PENDING, COMPLETED, CANCELED, REFUNDED }
enum VanType { PASSENGER, CARGO }
enum CarType { ECONOMY, COMPACT, INTERMEDIATE, STANDARD, FULL_SIZE, PREMIUM, LUXURY }
enum TruckType { LIGHT_DUTY, MEDIUM_DUTY, HEAVY_DUTY }
enum VehicleLogType { ACCIDENT, FUELING, CLEANING_SERVICE, OIL_CHANGE, REPAIR, OTHER }
enum VehicleStatus { AVAILABLE, RESERVED, RENTED, MAINTENANCE, UNAVAILABLE }

/* ---------------------------
   Address
   --------------------------- */
class Address {
    private String streetAddress;
    private String city;
    private String state;
    private int zipCode;
    private String country;

    public Address(String street, String city, String state, int zip, String country) {
        this.streetAddress = street;
        this.city = city;
        this.state = state;
        this.zipCode = zip;
        this.country = country;
    }
    public String getStreetAddress() { return streetAddress; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public int getZipCode() { return zipCode; }
    public String getCountry() { return country; }
    @Override public String toString(){ return streetAddress + ", " + city + ", " + state + " " + zipCode + ", " + country; }
}

/* ---------------------------
   Person / Account hierarchy
   --------------------------- */
abstract class Person {
    private String name;
    private Address address;
    private String email;
    private String phoneNumber;

    public String getName() { return name; }
    public void setName(String n) { name = n; }
    public Address getAddress() { return address; }
    public void setAddress(Address a) { address = a; }
    public String getEmail() { return email; }
    public void setEmail(String e) { email = e; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String p) { phoneNumber = p; }
}

class DriverPerson extends Person {
    private int driverId;
    public int getDriverId() { return driverId; }
    public void setDriverId(int id) { driverId = id; }
}

/* Account is also a Person */
abstract class Account extends Person {
    private String accountId;
    private String password;
    private AccountStatus status;

    public Account() {}

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    protected void setPassword(String pwd) { this.password = pwd; }
    public String getPassword() { return password; }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }

    public abstract boolean resetPassword();
}

class Customer extends Account {
    private String licenseNumber;
    private Date licenseExpiry;
    private List<VehicleReservation> reservations = new ArrayList<>();

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public Date getLicenseExpiry() { return licenseExpiry; }
    public void setLicenseExpiry(Date licenseExpiry) { this.licenseExpiry = licenseExpiry; }

    public List<VehicleReservation> getReservations() { return reservations; }
    public void addReservation(VehicleReservation r) { reservations.add(r); }

    @Override
    public boolean resetPassword() {
        this.setPassword(UUID.randomUUID().toString());
        return true;
    }
}

class Receptionist extends Account {
    private Date dateJoined;
    private List<Customer> customerList = new ArrayList<>();

    public Date getDateJoined() { return dateJoined; }
    public void setDateJoined(Date d) { this.dateJoined = d; }
    public List<Customer> getCustomerList() { return customerList; }

    public void addCustomer(Customer c) { if(!customerList.contains(c)) customerList.add(c); }
    public boolean removeCustomer(Customer c) { return customerList.remove(c); }
    public Customer findCustomerById(String id) {
        for (Customer c : customerList) if (id.equals(c.getAccountId())) return c;
        return null;
    }

    @Override
    public boolean resetPassword() {
        this.setPassword(UUID.randomUUID().toString());
        return true;
    }
}

/* ---------------------------
   Vehicle & subclasses
   --------------------------- */
abstract class Vehicle {
    private String vehicleId;
    private String licensePlateNumber;
    private int passengerCapacity;
    private VehicleStatus status = VehicleStatus.AVAILABLE;
    private String model;
    private int manufacturingYear;
    private List<VehicleLog> log = new ArrayList<>();

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getLicensePlateNumber() { return licensePlateNumber; }
    public void setLicensePlateNumber(String licensePlateNumber) { this.licensePlateNumber = licensePlateNumber; }

    public int getPassengerCapacity() { return passengerCapacity; }
    public void setPassengerCapacity(int passengerCapacity) { this.passengerCapacity = passengerCapacity; }

    public VehicleStatus getStatus() { return status; }
    public void setStatus(VehicleStatus status) { this.status = status; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getManufacturingYear() { return manufacturingYear; }
    public void setManufacturingYear(int manufacturingYear) { this.manufacturingYear = manufacturingYear; }

    public List<VehicleLog> getLog() { return log; }
    public void addLog(VehicleLog l) { log.add(l); }

    // basic reservation/return operations
    public boolean reserveVehicle() {
        if (status == VehicleStatus.AVAILABLE) {
            status = VehicleStatus.RESERVED;
            return true;
        }
        return false;
    }

    public boolean rentOut() {
        if (status == VehicleStatus.RESERVED || status == VehicleStatus.AVAILABLE) {
            status = VehicleStatus.RENTED;
            return true;
        }
        return false;
    }

    public boolean returnVehicle() {
        if (status == VehicleStatus.RENTED || status == VehicleStatus.RESERVED) {
            status = VehicleStatus.AVAILABLE;
            return true;
        }
        return false;
    }
}

class Car extends Vehicle {
    private CarType carType;
    public CarType getCarType() { return carType; }
    public void setCarType(CarType carType) { this.carType = carType; }
}

class Van extends Vehicle {
    private VanType vanType;
    public VanType getVanType() { return vanType; }
    public void setVanType(VanType vanType) { this.vanType = vanType; }
}

class Truck extends Vehicle {
    private TruckType truckType;
    public TruckType getTruckType() { return truckType; }
    public void setTruckType(TruckType truckType) { this.truckType = truckType; }
}

class Motorcycle extends Vehicle {
    private MotorcycleType motorcycleType;
    public MotorcycleType getMotorcycleType() { return motorcycleType; }
    public void setMotorcycleType(MotorcycleType motorcycleType) { this.motorcycleType = motorcycleType; }
}

/* ---------------------------
   Equipment & Service
   --------------------------- */
abstract class Equipment {
    private int equipmentId;
    private double price;
    public int getEquipmentId() { return equipmentId; }
    public void setEquipmentId(int equipmentId) { this.equipmentId = equipmentId; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}

class Navigation extends Equipment {}
class ChildSeat extends Equipment {}
class SkiRack extends Equipment {}

abstract class Service {
    private int serviceId;
    private double price;
    public int getServiceId() { return serviceId; }
    public void setServiceId(int serviceId) { this.serviceId = serviceId; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}

class DriverService extends Service {
    private int driverId;
    public int getDriverId() { return driverId; }
    public void setDriverId(int driverId) { this.driverId = driverId; }
}

class RoadsideAssistance extends Service {}
class WiFi extends Service {}

/* ---------------------------
   Payment
   --------------------------- */
abstract class Payment {
    private double amount;
    private Date timestamp;
    private PaymentStatus status;

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public abstract boolean makePayment();
}

class CashPayment extends Payment {
    @Override public boolean makePayment() { setStatus(PaymentStatus.COMPLETED); return true; }
}

class CreditCardPayment extends Payment {
    private String nameOnCard;
    private String cardNumber;
    private String billingAddress;
    private int code;
    public String getNameOnCard() { return nameOnCard; }
    public void setNameOnCard(String nameOnCard) { this.nameOnCard = nameOnCard; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    public String getBillingAddress() { return billingAddress; }
    public void setBillingAddress(String billingAddress) { this.billingAddress = billingAddress; }
    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    @Override public boolean makePayment() { setStatus(PaymentStatus.COMPLETED); return true; }
}

/* ---------------------------
   VehicleLog
   --------------------------- */
class VehicleLog {
    private static final AtomicInteger ID_GEN = new AtomicInteger(1);
    private int logId;
    private VehicleLogType logType;
    private String description;
    private Date creationDate;

    public VehicleLog(VehicleLogType type, String description) {
        this.logId = ID_GEN.getAndIncrement();
        this.logType = type;
        this.description = description;
        this.creationDate = new Date();
    }
    public int getLogId() { return logId; }
    public VehicleLogType getLogType() { return logType; }
    public String getDescription() { return description; }
    public Date getCreationDate() { return creationDate; }
}

/* ---------------------------
   VehicleReservation
   --------------------------- */
class VehicleReservation {
    private static final AtomicInteger ID_GEN = new AtomicInteger(1000);
    private int reservationId;
    private String customerId;
    private String vehicleId;
    private Date creationDate;
    private ReservationStatus status;
    private Date dueDate;
    private Date returnDate;
    private String pickupLocation;
    private String returnLocation;
    private List<Equipment> equipments = new ArrayList<>();
    private List<Service> services = new ArrayList<>();
    private Payment payment;

    public VehicleReservation() { this.reservationId = ID_GEN.getAndIncrement(); this.creationDate = new Date(); this.status = ReservationStatus.PENDING; }

    // Getters / Setters
    public int getReservationId() { return reservationId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public Date getCreationDate() { return creationDate; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }
    public Date getReturnDate() { return returnDate; }
    public void setReturnDate(Date returnDate) { this.returnDate = returnDate; }
    public String getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(String pickupLocation) { this.pickupLocation = pickupLocation; }
    public String getReturnLocation() { return returnLocation; }
    public void setReturnLocation(String returnLocation) { this.returnLocation = returnLocation; }
    public List<Equipment> getEquipments() { return equipments; }
    public List<Service> getServices() { return services; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }

    public void addEquipment(Equipment e) { if(e!=null) equipments.add(e); }
    public void addService(Service s) { if(s!=null) services.add(s); }

    public double calculateTotal() {
        double total = 0;
        if (payment != null) total += payment.getAmount();
        for (Equipment e : equipments) total += e.getPrice();
        for (Service s : services) total += s.getPrice();
        return total;
    }
}

/* ---------------------------
   Notification
   --------------------------- */
abstract class Notification {
    private int notificationId;
    private Date createdOn;
    private String content;
    public void setContent(String c) { content = c; this.createdOn = new Date(); this.notificationId = Objects.hash(content, createdOn); }
    public String getContent() { return content; }
    public abstract void sendNotification(Account account);
}

class SmsNotification extends Notification {
    @Override
    public void sendNotification(Account account) {
        System.out.println("SMS to " + account.getName() + ": " + getContent());
    }
}

class EmailNotification extends Notification {
    @Override
    public void sendNotification(Account account) {
        System.out.println("Email to " + account.getName() + ": " + getContent());
    }
}

/* ---------------------------
   ParkingStall, Fine
   --------------------------- */
class ParkingStall {
    private int stallId;
    private String locationIdentifier;
    public ParkingStall(int stallId, String locationIdentifier) { this.stallId = stallId; this.locationIdentifier = locationIdentifier; }
    public int getStallId() { return stallId; }
    public String getLocationIdentifier() { return locationIdentifier; }
}

class Fine {
    private double amount;
    private String reason;
    public void setAmount(double a) { amount = a; }
    public double getAmount() { return amount; }
    public void setReason(String r) { reason = r; }
    public String getReason() { return reason; }
    public double calculateFine() { return amount; }
}

/* ---------------------------
   Search interface & VehicleCatalog
   --------------------------- */
interface Search {
    List<Vehicle> searchByType(String type);
    List<Vehicle> searchByModel(String model);
}

class VehicleCatalog implements Search {
    private HashMap<String, List<Vehicle>> vehicleTypes = new HashMap<>();
    private HashMap<String, List<Vehicle>> vehicleModels = new HashMap<>();

    public void addVehicle(Vehicle v) {
        if (v == null) return;
        String typeKey = v.getClass().getSimpleName().toUpperCase(); // CAR, VAN, TRUCK...
        vehicleTypes.computeIfAbsent(typeKey, k -> new ArrayList<>()).add(v);
        vehicleModels.computeIfAbsent(v.getModel().toUpperCase(), k -> new ArrayList<>()).add(v);
    }

    @Override
    public List<Vehicle> searchByType(String type) {
        if (type == null) return Collections.emptyList();
        return vehicleTypes.getOrDefault(type.toUpperCase(), Collections.emptyList());
    }

    @Override
    public List<Vehicle> searchByModel(String model) {
        if (model == null) return Collections.emptyList();
        return vehicleModels.getOrDefault(model.toUpperCase(), Collections.emptyList());
    }
}

/* ---------------------------
   Branch & System
   --------------------------- */
class CarRentalBranch {
    private String name;
    private Address address;
    private List<ParkingStall> stalls;

    public CarRentalBranch(String name, Address address, List<ParkingStall> stalls) {
        this.name = name; this.address = address; this.stalls = stalls == null ? new ArrayList<>() : stalls;
    }
    public String getName() { return name; }
    public Address getAddress() { return address; }
    public String getLocation() { return address == null ? "" : address.toString(); }
    public List<ParkingStall> getStalls() { return stalls; }
}

class CarRentalSystem {
    private String name;
    private List<CarRentalBranch> branches = new ArrayList<>();
    private static CarRentalSystem system = null;

    private CarRentalSystem() {}

    public static CarRentalSystem getInstance() {
        if (system == null) system = new CarRentalSystem();
        return system;
    }

    public void setName(String name) { this.name = name; }
    public String getName() { return name; }

    public void addNewBranch(CarRentalBranch b) { if(b!=null) branches.add(b); }
    public List<CarRentalBranch> getBranches() { return Collections.unmodifiableList(branches); }
}

/* ---------------------------
   Demo driver
   --------------------------- */
public class CarRentalSystemDriver {
    public static void main(String[] args) {
        System.out.println("=============================================");
        System.out.println("        CAR RENTAL SYSTEM DEMO");
        System.out.println("=============================================\n");

        // 1. Branch Setup
        System.out.println("1. Branch Setup");
        CarRentalSystem rentalSystem = CarRentalSystem.getInstance();
        rentalSystem.setName("Acme Rentals");
        CarRentalBranch branch1 = new CarRentalBranch(
                "Airport Branch",
                new Address("123 Main St", "Seattle", "WA", 98101, "USA"),
                new ArrayList<>());
        rentalSystem.addNewBranch(branch1);
        System.out.println("   -> Branch added: " + branch1.getName() + " (" + branch1.getLocation() + ")\n");

        // 2. Add Vehicles
        System.out.println("2. Adding Vehicles to Inventory");
        Car car1 = new Car();
        car1.setVehicleId("C1001");
        car1.setModel("Toyota Corolla");
        car1.setManufacturingYear(2022);
        car1.setCarType(CarType.ECONOMY);
        car1.setStatus(VehicleStatus.AVAILABLE);

        Van van1 = new Van();
        van1.setVehicleId("V1001");
        van1.setModel("Ford Transit");
        van1.setManufacturingYear(2021);
        van1.setVanType(VanType.PASSENGER);
        van1.setStatus(VehicleStatus.AVAILABLE);

        VehicleCatalog catalog = new VehicleCatalog();
        catalog.addVehicle(car1);
        catalog.addVehicle(van1);

        System.out.println("   -> Vehicles added: ");
        System.out.println("      - " + car1.getModel() + " (ID: " + car1.getVehicleId() + ")");
        System.out.println("      - " + van1.getModel() + " (ID: " + van1.getVehicleId() + ")\n");

        // 3. Customer Registration
        System.out.println("3. Customer Registration & Login");
        Customer customer1 = new Customer();
        customer1.setAccountId("U123");
        customer1.setName("Alice Smith");
        customer1.setEmail("alice@email.com");
        customer1.setLicenseNumber("D1234567");

        Calendar expiryCal = Calendar.getInstance();
        expiryCal.set(2027, Calendar.FEBRUARY, 1);
        customer1.setLicenseExpiry(expiryCal.getTime());
        customer1.setStatus(AccountStatus.ACTIVE);

        System.out.println("   -> [LOGIN] Customer: " + customer1.getName() + " (ID: " + customer1.getAccountId() + ")");
        System.out.println("   -> Driver License #: " + customer1.getLicenseNumber() +
                " (Expires: " + customer1.getLicenseExpiry() + ")\n");

        // 4. Vehicle Search by Customer
        System.out.println("4. Vehicle Search");
        System.out.println("   == Vehicle Inventory Search Results ==");
        List<Vehicle> cars = catalog.searchByType("CAR");
        if (!cars.isEmpty()) {
            System.out.println("   -> Found " + cars.size() + " car(s) in inventory:");
            for (Vehicle v : cars) {
                System.out.println("      -> Model: " + v.getModel() +
                        " | ID: " + v.getVehicleId() +
                        " | Year: " + v.getManufacturingYear() +
                        " | Status: " + v.getStatus());
            }
        } else {
            System.out.println("   -> No cars found in inventory.");
        }
        System.out.println();

        // 5. Make a Reservation
        System.out.println("5. Reservation");
        VehicleReservation reservation = new VehicleReservation();
        reservation.setCustomerId(customer1.getAccountId());
        reservation.setVehicleId(car1.getVehicleId());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setPickupLocation("Airport Branch");
        reservation.setReturnLocation("Airport Branch");

        Calendar dueCal = Calendar.getInstance();
        dueCal.add(Calendar.DAY_OF_MONTH, 3);
        reservation.setDueDate(dueCal.getTime());

        List<VehicleReservation> allReservations = new ArrayList<>();
        allReservations.add(reservation);

        boolean reserved = car1.reserveVehicle();
        System.out.println("   -> Reservation created for " + customer1.getName() +
                " | Vehicle: " + car1.getModel() +
                " | Pickup: " + reservation.getPickupLocation() +
                " | Return: " + reservation.getReturnLocation() +
                " | Due: " + reservation.getDueDate() +
                " | Reserve call result: " + reserved);

        // 6. Add Equipment and Services
        System.out.println("\n6. Add-ons: Equipment & Services");
        ChildSeat seat = new ChildSeat();
        seat.setEquipmentId(10);
        seat.setPrice(15);
        reservation.addEquipment(seat);

        DriverService driverService = new DriverService();
        driverService.setServiceId(20);
        driverService.setPrice(50);
        driverService.setDriverId(222);
        reservation.addService(driverService);

        System.out.println("   -> Equipment added: Child Seat (ID: " + seat.getEquipmentId() + ", Price: $" + seat.getPrice() + ")");
        System.out.println("   -> Service added: Driver (Driver ID: " + driverService.getDriverId() + ", Price: $" + driverService.getPrice() + ")\n");

        // 7. Payment Processing
        System.out.println("7. Payment Processing");
        reservation.setStatus(ReservationStatus.CONFIRMED);
        CreditCardPayment payment = new CreditCardPayment();
        payment.setAmount(200);
        payment.setTimestamp(new Date());
        payment.setStatus(PaymentStatus.PENDING);
        reservation.setPayment(payment);
        System.out.println("   -> Processing payment of $" + payment.getAmount() + " ...");
        boolean paymentSuccess = payment.makePayment();

        if (paymentSuccess) {
            System.out.println("   -> Payment completed successfully for reservation #" + reservation.getReservationId());
        } else {
            System.out.println("   -> Payment failed!");
        }
        System.out.println();

        // 8. Notification (Email)
        System.out.println("8. Notification");
        EmailNotification notify = new EmailNotification();
        notify.setContent("Your reservation is confirmed!");
        notify.sendNotification(customer1);
        System.out.println();

        // 9. Vehicle Pickup
        System.out.println("9. Vehicle Pickup");
        boolean rentedOut = car1.rentOut();
        System.out.println("   -> " + customer1.getName() + " picked up the vehicle: " +
                car1.getModel() + " (" + car1.getVehicleId() + ") | RentOut result: " + rentedOut + "\n");

        // 10. Vehicle Return
        System.out.println("10. Vehicle Return");
        Calendar returnCal = Calendar.getInstance();
        returnCal.add(Calendar.DAY_OF_MONTH, 3); // Returned on due date
        car1.returnVehicle();
        reservation.setReturnDate(returnCal.getTime());
        reservation.setStatus(ReservationStatus.COMPLETED);

        System.out.println("   -> Vehicle returned on: " + reservation.getReturnDate());
        System.out.println("   -> Reservation status is now: " + reservation.getStatus() + "\n");

        // 11. Overdue Fine Check
        System.out.println("11. Fine Calculation");
        Date expectedReturn = reservation.getDueDate();
        Date actualReturn = reservation.getReturnDate();
        if (actualReturn.after(expectedReturn)) {
            Fine fine = new Fine();
            fine.setAmount(100);
            fine.setReason("Late return");
            System.out.println("   -> [FINE] Fine imposed for late return: $" + fine.getAmount());
            Notification fineNotify = new SmsNotification();
            fineNotify.setContent("You have been fined for late return.");
            fineNotify.sendNotification(customer1);
        } else {
            System.out.println("   -> [FINE] No fine. Vehicle was returned on time.");
        }
        System.out.println();

        // 12. Reservation History for Customer
        System.out.println("12. Reservation History for Customer: " + customer1.getName());
        System.out.println("   -------------------------------------");
        for (VehicleReservation r : allReservations) {
            if (r.getCustomerId().equals(customer1.getAccountId())) {
                System.out.println("   -> Reservation ID: " + r.getReservationId() +
                        " | Vehicle: " + r.getVehicleId() +
                        " | Status: " + r.getStatus() +
                        " | Pickup: " + r.getPickupLocation() +
                        " | Returned: " + r.getReturnDate());
            }
        }
        System.out.println("=============================================");
        System.out.println("             END OF DEMO");
        System.out.println("=============================================");
    }
}
