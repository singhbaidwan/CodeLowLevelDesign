package AmazonLockerDeliverySystem.Main;
import java.util.*;


enum LockerSize {
    EXTRA_SMALL,
    SMALL,
    MEDIUM,
    LARGE,
    EXTRA_LARGE,
    DOUBLE_EXTRA_LARGE
}

enum LockerState {
    CLOSED,
    BOOKED,
    AVAILABLE
}

class Item{
    private String itemId;
    private int quantity;

    public Item(String itemId, int quantity) {
        this.itemId = itemId;
        this.quantity = quantity;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}

class Order {
    private String orderId;
    private List<Item> items;
    private String deliveryLocation;
    private String customerId;

    public Order(String orderId, String deliveryLocation, String customerId) {
        this.orderId = orderId;
        this.deliveryLocation = deliveryLocation;
        this.customerId = customerId;
        this.items = new ArrayList<>();
    }

    public String getOrderId() { return orderId; }
    public String getDeliveryLocation() { return deliveryLocation; }
    public String getCustomerId() { return customerId; }
    public List<Item> getItems() { return items; }
    public void addItem(Item item) { items.add(item); }
}

class Package{

    private String packageId;
    private double packageSize;
    private Order order;

    public Package(String packageId, double packageSize, Order order) {
        this.packageId = packageId;
        this.packageSize = packageSize;
        this.order = order;
    }

    public String getPackageId() {
        return packageId;
    }

    public double getPackageSize() {
        return packageSize;
    }

    public Order getOrder() {
        return order;
    }

    public void pack(){
        System.out.println(" ↳ [Packaging] Package " + packageId + " packed (size=" + packageSize + ")");
    }
}

class LockerPackage extends Package{
    private int codeValidDays;
    private String lockerId;
    private String code;
    private Date packageDeliveryTime;
    private String deliveryPersonId;

    public LockerPackage(String packageId, double packageSize, Order order, int codeValidDays, String lockerId, String code, Date packageDeliveryTime, String deliveryPersonId) {
        super(packageId, packageSize, order);
        this.codeValidDays = codeValidDays;
        this.lockerId = lockerId;
        this.code = code;
        this.packageDeliveryTime = packageDeliveryTime;
        this.deliveryPersonId = deliveryPersonId;
    }

    public int getCodeValidDays() {
        return codeValidDays;
    }

    public String getLockerId() {
        return lockerId;
    }

    public String getCode() {
        return code;
    }

    public Date getPackageDeliveryTime() {
        return packageDeliveryTime;
    }

    public String getDeliveryPersonId() {
        return deliveryPersonId;
    }
    public boolean isValidCode() {
        long expiryTime = packageDeliveryTime.getTime() + (long) codeValidDays * 24 * 60 * 60 * 1000;
        return new Date().getTime() <= expiryTime;
    }

    public boolean verifyCode(String code){
        return this.isValidCode() && code.equals(this.code);
    }
}

class Notification{
    private String customerId;
    private String lockerId;
    private String orderId;
    private String code;

    public Notification(String customerId, String lockerId, String orderId, String code) {
        this.customerId = customerId;
        this.lockerId = lockerId;
        this.orderId = orderId;
        this.code = code;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getLockerId() {
        return lockerId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCode() {
        return code;
    }
    public void send() {
        System.out.println("[Notification] Sent to customer " + customerId + " for order " + orderId);
    }
}

class Customer{
    private String customerId;
    private String name;
    private String email;
    private String phone;

    public Customer(String customerId, String name, String email, String phone) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public void placeOrder(Order order) {
        System.out.println(" ↳ Customer " + name + " placed order: " + order.getOrderId());
    }


    public void requestReturn(Order order) {
        System.out.println(" ↳ Customer " + name + " requested return for order: " + order.getOrderId());
    }


    public void receiveNotification(Notification notification) {
        System.out.println(" ↳ Customer received notification for order: " + notification.getOrderId() + " with OTP: " + notification.getCode());
    }

    public String getCustomerId() {
        return customerId;
    }
}


class DeliveryPerson{
    private String deliveryPersonId;

    public DeliveryPerson(String deliveryPersonId) {
        this.deliveryPersonId = deliveryPersonId;
    }

    public String getDeliveryPersonId() {
        return deliveryPersonId;
    }

    public void executeTask(LockerTask task) {
        System.out.println(" [DeliveryPerson] Executing task: " + task.getDescription());
        task.execute();
    }
}

class Locker{
    private String lockerId;
    private LockerSize lockerSize;
    private String locationId;
    private LockerState lockerState;
    private LockerPackage currentPackage;

    public Locker(String lockerId, LockerSize lockerSize, String locationId) {
        this.lockerId = lockerId;
        this.lockerSize = lockerSize;
        this.locationId = locationId;
        this.lockerState = LockerState.AVAILABLE;
    }

    public String getLockerId() {
        return lockerId;
    }

    public LockerSize getLockerSize() {
        return lockerSize;
    }

    public String getLocationId() {
        return locationId;
    }

    public LockerState getLockerState() {
        return lockerState;
    }

    public void setLockerState(LockerState lockerState) {
        this.lockerState = lockerState;
    }

    public boolean addPackage(LockerPackage pkg){
        if(lockerState == LockerState.AVAILABLE){
            this.currentPackage = pkg;
            this.lockerState = LockerState.BOOKED;
            System.out.println(" [Locker] Package " + pkg.getPackageId() + " added to Locker " + lockerId);
            return true;
        }
        return false;
    }
    public boolean removePackage(String code) {
        if (currentPackage != null && currentPackage.verifyCode(code)) {
            System.out.println(" [Locker] Package " + currentPackage.getPackageId() + " removed from Locker " + lockerId);
            currentPackage = null;
            lockerState = LockerState.AVAILABLE;
            return true;
        }
        return false;
    }
}


class LockerLocation {
    private String name;
    private List<Locker> lockers;
    private double longitude;
    private double latitude;
    private Date openTime;
    private Date closeTime;

    public LockerLocation(String name, double longitude, double latitude, Date openTime, Date closeTime) {
        this.name = name;
        this.longitude = longitude;
        this.latitude = latitude;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.lockers = new ArrayList<>();
    }

    public String getName() { return name; }
    public List<Locker> getLockers() { return lockers; }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public Date getOpenTime() {
        return openTime;
    }

    public Date getCloseTime() {
        return closeTime;
    }

    public void addLocker(Locker locker) {
        lockers.add(locker);
    }
}


interface TaskAssignmentStrategy {
    Locker assignLocker(List<LockerLocation> locations, LockerSize size);
}

class NearestLockerStrategy implements TaskAssignmentStrategy {
    public Locker assignLocker(List<LockerLocation> locations, LockerSize size) {
        for (LockerLocation loc : locations) {
            for (Locker l : loc.getLockers()) {
                if (l.getLockerState() == LockerState.AVAILABLE && l.getLockerSize() == size) {
                    return l;
                }
            }
        }
        return null;
    }
}


abstract class LockerTask {
    protected Locker locker;
    protected LockerPackage pkg;


    public LockerTask(Locker locker, LockerPackage pkg) {
        this.locker = locker;
        this.pkg = pkg;
    }


    public abstract void execute();
    public abstract String getDescription();
}

class DeliverToLockerTask extends LockerTask {
    public DeliverToLockerTask(Locker locker, LockerPackage pkg) {
        super(locker, pkg);
    }

    public void execute() {
        locker.addPackage(pkg);
    }

    public String getDescription() {
        return "Deliver package to Locker " + locker.getLockerId();
    }
}

class PickupFromLockerTask extends LockerTask {
    private String code;
    public PickupFromLockerTask(Locker locker, LockerPackage pkg, String code) {
        super(locker, pkg);
        this.code = code;
    }
    public void execute() { locker.removePackage(code); }
    public String getDescription() { return "Pickup package from Locker " + locker.getLockerId(); }
}

class ReturnToLockerTask extends LockerTask {
    public ReturnToLockerTask(Locker locker, LockerPackage pkg) { super(locker, pkg); }
    public void execute() { locker.addPackage(pkg); }
    public String getDescription() { return "Return package to Locker " + locker.getLockerId(); }
}

class LockerService{
    private List<LockerLocation> locations;
    private static LockerService lockerService = null;
    private TaskAssignmentStrategy strategy;
    private LockerService() {
        this.locations = new ArrayList<>();
        this.strategy = new NearestLockerStrategy();
    }


    public static LockerService getInstance() {
        if (lockerService == null) {
            lockerService = new LockerService();
        }
        return lockerService;
    }
    public void addLocation(LockerLocation loc) { locations.add(loc); }
    public Locker findLockerById(String lockerId) {
        for (LockerLocation loc : locations) {
            for (Locker l : loc.getLockers()) {
                if (l.getLockerId().equals(lockerId)) return l;
            }
        }
        return null;
    }
    public boolean requestReturn(Order order) {
        return true; // Simplified approval
    }


    public Locker requestLocker(LockerSize size) {
        return strategy.assignLocker(locations, size);
    }


    public boolean verifyOTP(LockerPackage lpkg, String code) {
        return lpkg.verifyCode(code);
    }

}


public class Driver {
    public static void main(String[] args) {
        System.out.println(new String(new char[100]).replace('\0', '═'));
        System.out.println("\t\t\t\t\tAMAZON LOCKER SERVICE SYSTEM");
        System.out.println(new String(new char[100]).replace('\0', '═'));

        LockerService lockerService = LockerService.getInstance();
        LockerLocation loc = new LockerLocation("Downtown", 10.5, 20.8, new Date(), new Date());
        Locker locker1 = new Locker("L1", LockerSize.MEDIUM, loc.getName());
        Locker locker2 = new Locker("L2", LockerSize.LARGE, loc.getName());
        loc.addLocker(locker1);
        loc.addLocker(locker2);
        lockerService.addLocation(loc);
        System.out.println("    → Added LockerLocation: Downtown with Lockers: [L1, L2]\n");

        Customer customer = new Customer("CUST1", "Alice", "alice@example.com", "1234567890");
        DeliveryPerson deliveryGuy = new DeliveryPerson("DEL1");

        // === Scenario 1: Customer Places an Order ===
        System.out.println("1️⃣  SCENARIO 1: Customer Places an Order");
        Order order = new Order("ORD1", loc.getName(), customer.getCustomerId());
        order.addItem(new Item("ITM1", 2));
        customer.placeOrder(order);

        Package pkg = new Package("PKG1", 2.5, order);
        pkg.pack();

        Locker assignedLocker = lockerService.requestLocker(LockerSize.MEDIUM);
        String otp = "123456";
        LockerPackage lpkg = new LockerPackage("PKG1", 2.5, order, 3, assignedLocker.getLockerId(), otp, new Date(), deliveryGuy.toString());

        LockerTask deliveryTask = new DeliverToLockerTask(assignedLocker, lpkg);
        deliveryGuy.executeTask(deliveryTask);

        Notification notification = new Notification(customer.toString(), order.getOrderId(), assignedLocker.getLockerId(), otp);
        notification.send();
        customer.receiveNotification(notification);

        // === Scenario 2: Customer Picks Up the Package ===
        System.out.println("\n2️⃣  SCENARIO 2: Customer Picks Up the Package");
        LockerTask pickupTask = new PickupFromLockerTask(assignedLocker, lpkg, otp);
        pickupTask.execute();

        // === Scenario 3: Customer Initiates a Return ===
        System.out.println("\n3️⃣  SCENARIO 3: Customer Initiates a Return");
        customer.requestReturn(order);
        boolean returnApproved = lockerService.requestReturn(order);

        if (returnApproved) {
            Locker returnLocker = lockerService.requestLocker(LockerSize.MEDIUM);
            String returnOtp = "654321";
            LockerPackage returnPkg = new LockerPackage("PKG1-R", 2.5, order, 3, returnLocker.getLockerId(), returnOtp, new Date(), deliveryGuy.toString());

            Notification returnNotif = new Notification(customer.toString(), order.getOrderId(), returnLocker.getLockerId(), returnOtp);
            returnNotif.send();
            customer.receiveNotification(returnNotif);

            LockerTask returnTask = new ReturnToLockerTask(returnLocker, returnPkg);
            returnTask.execute();

            LockerTask pickupReturnTask = new PickupFromLockerTask(returnLocker, returnPkg, returnOtp);
            deliveryGuy.executeTask(pickupReturnTask);
        }

        System.out.println("\n🏁\t\t\t\t\tSYSTEM DEMO COMPLETE");
        System.out.println(new String(new char[100]).replace('\0', '═'));
    }
}
