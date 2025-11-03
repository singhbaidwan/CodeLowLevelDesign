package Amazon;


// Amazon Online Shopping System - Single-file demo
// Thread-safe and optimized skeleton implementation for demo / testing.

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

// -------------------- Enums --------------------
enum OrderStatus {
    UNSHIPPED,
    PENDING,
    SHIPPED,
    CONFIRMED,
    CANCELED,
    REFUNDED
}

enum AccountStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED
}

enum ShipmentStatus {
    PENDING,
    SHIPPED,
    DELIVERED,
    ON_HOLD
}

enum PaymentStatus {
    CONFIRMED,
    DECLINED,
    PENDING,
    REFUNDED
}

// -------------------- Address --------------------
class Address {
    private int zipCode;
    private String address;
    private String city;
    private String state;
    private String country;

    public Address(int zipCode, String address, String city, String state, String country) {
        this.zipCode = zipCode;
        this.address = address;
        this.city = city;
        this.state = state;
        this.country = country;
    }

    public String getFullAddress() {
        return String.format("%s, %s, %s - %d, %s", address, city, state, zipCode, country);
    }
}

// -------------------- Account --------------------
class Account {
    private String userName;
    private String password;
    private String name;
    private final List<Address> shippingAddress = new CopyOnWriteArrayList<>();
    private volatile AccountStatus status;
    private String email;
    private String phone;

    private final List<CreditCardInfo> creditCards = new CopyOnWriteArrayList<>();
    private final List<String> bankAccounts = new CopyOnWriteArrayList<>();

    public Account() {}

    public synchronized void setStatus(AccountStatus status) { this.status = status; }
    public synchronized AccountStatus getStatus() { return status; }

    public void addShippingAddress(Address a) { shippingAddress.add(a); }
    public Address getShippingAddress() { return shippingAddress.isEmpty() ? null : shippingAddress.get(0); }

    public synchronized boolean resetPassword() {
        if (this.userName == null) return false;
        this.password = UUID.randomUUID().toString().substring(0, 8);
        return true;
    }

    // Minimal setters used in demo
    public void setUserName(String u) { this.userName = u; }
    public void setPassword(String p) { this.password = p; }
    public void setEmail(String e) { this.email = e; }
}

// Small helper struct to hold credit card info on Account (demo only, not secure)
class CreditCardInfo {
    private final String cardNumber;
    private final String nameOnCard;
    private final int cvv;
    public CreditCardInfo(String cardNumber, String nameOnCard, int cvv) {
        this.cardNumber = cardNumber; this.nameOnCard = nameOnCard; this.cvv = cvv;
    }
}

// -------------------- Admin --------------------
class Admin {
    private final Account account;
    public Admin(Account account) { this.account = account; }

    public boolean blockUser(Account account) {
        if (account == null) return false;
        account.setStatus(AccountStatus.BLOCKED);
        return true;
    }

    // category operations are stubs for demo
    public boolean addNewProductCategory(ProductCategory category) { return category != null; }
    public boolean modifyProductCategory(ProductCategory category) { return category != null; }
    public boolean deleteProductCategory(ProductCategory category) { return category != null; }
}

// -------------------- Product / Category / Review --------------------
class Product {
    private final String productId;
    private final String name;
    private final String description;
    private final byte[] image;
    private volatile double price;
    private final ProductCategory category;
    private final List<ProductReview> reviews = new CopyOnWriteArrayList<>();
    private final AtomicInteger availableItemCount;
    private final Account account; // owner (optional)

    public Product(String productId, String name, String description, double price, ProductCategory category) {
        this(productId, name, description, null, price, category, 0, null);
    }

    public Product(String productId, String name, String description, byte[] image, double price, ProductCategory category, int available, Account account) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.image = image;
        this.price = price;
        this.category = category;
        this.availableItemCount = new AtomicInteger(available);
        this.account = account;
    }

    public String getProductId() { return productId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public ProductCategory getCategory() { return category; }

    public int getAvailableCount() { return availableItemCount.get(); }

    // atomically decrement inventory by quantity; return new count or -1 if insufficient
    public int decrementAvailableCount(int quantity) {
        while (true) {
            int current = availableItemCount.get();
            if (current < quantity) return -1;
            int next = current - quantity;
            if (availableItemCount.compareAndSet(current, next)) return next;
        }
    }

    public int updateAvailableCount(int newCount) { availableItemCount.set(newCount); return availableItemCount.get(); }

    public synchronized boolean updatePrice(double newPrice) {
        if (newPrice < 0) return false;
        this.price = newPrice;
        return true;
    }

    public void addReview(ProductReview r) { if (r != null) reviews.add(r); }
}

class ProductCategory {
    private final String name;
    private final String description;
    private final List<Product> products = new CopyOnWriteArrayList<>();

    public ProductCategory(String name, String description) { this.name = name; this.description = description; }
    public String getName() { return name; }
    public void addProduct(Product p) { products.add(p); }
    public List<Product> getProducts() { return Collections.unmodifiableList(products); }
}

class ProductReview {
    private final int rating;
    private final String review;
    private final byte[] image;
    private final AuthenticatedUser user;

    public ProductReview(int rating, String review, AuthenticatedUser user) {
        this.rating = rating; this.review = review; this.user = user; this.image = null;
    }
}

// -------------------- CartItem / ShoppingCart --------------------
class CartItem {
    private final Product product;
    private final AtomicInteger quantity;
    private volatile double price; // snapshot price

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = new AtomicInteger(quantity);
        this.price = product.getPrice() * quantity;
    }

    public Product getProduct() { return product; }
    public int getQuantity() { return quantity.get(); }
    public double getPrice() { return price; }

    public boolean updateQuantity(int qty) {
        if (qty <= 0) return false;
        quantity.set(qty);
        price = product.getPrice() * qty;
        return true;
    }

    public boolean increaseQuantity(int delta) {
        if (delta <= 0) return false;
        quantity.addAndGet(delta);
        price = product.getPrice() * quantity.get();
        return true;
    }
}

class ShoppingCart {
    private final Lock lock = new ReentrantLock();
    private final List<CartItem> items = new CopyOnWriteArrayList<>();

    public boolean addItem(Product product, int qty) {
        if (product == null || qty <= 0) return false;
        lock.lock();
        try {
            for (CartItem ci : items) {
                if (ci.getProduct().getProductId().equals(product.getProductId())) {
                    ci.increaseQuantity(qty);
                    return true;
                }
            }
            CartItem newItem = new CartItem(product, qty);
            items.add(newItem);
            return true;
        } finally { lock.unlock(); }
    }

    public boolean removeItem(Product product) {
        if (product == null) return false;
        lock.lock();
        try { return items.removeIf(ci -> ci.getProduct().getProductId().equals(product.getProductId())); }
        finally { lock.unlock(); }
    }

    public List<CartItem> getItems() { return Collections.unmodifiableList(new ArrayList<>(items)); }

    public boolean checkout() {
        lock.lock();
        try {
            if (!verify()) return false;
            items.clear();
            return true;
        } finally { lock.unlock(); }
    }

    public boolean verify() {
        for (CartItem ci : items) {
            Product p = ci.getProduct();
            if (p.getAvailableCount() < ci.getQuantity()) return false;
        }
        return true;
    }

    public double getTotalPrice() {
        double sum = 0.0;
        for (CartItem ci : items) sum += ci.getPrice();
        return sum;
    }
}

// -------------------- Customer, Guest, AuthenticatedUser --------------------
abstract class Customer {
    protected final ShoppingCart cart = new ShoppingCart();
    public ShoppingCart getShoppingCart() { return cart; }
    public abstract List<Product> searchproduct(String name);
}

class Guest extends Customer {
    public Account registerAccount(String username, String password, String email) {
        Account acc = new Account();
        acc.setUserName(username);
        acc.setPassword(password);
        acc.setEmail(email);
        acc.setStatus(AccountStatus.ACTIVE);
        System.out.println("Registered user: " + username);
        return acc;
    }

    @Override public List<Product> searchproduct(String name) { return Collections.emptyList(); }
}

class AuthenticatedUser extends Customer {
    private Account account;
    private Order order;

    public AuthenticatedUser() {}
    public void setAccount(Account account) { this.account = account; }

    public boolean addItem(Product product, int qty) {
        if (account != null && account.getStatus() == AccountStatus.BLOCKED) {
            System.out.println("Account blocked. Cannot add.");
            return false;
        }
        return cart.addItem(product, qty);
    }

    // simplified placeOrder used by demo: place using one item
    public OrderStatus placeOrder(CartItem item, double price) {
        Order newOrder = new Order(UUID.randomUUID().toString(), OrderStatus.PENDING, this);
        this.order = newOrder;
        boolean verified = newOrder.verify(item);
        if (!verified) {
            newOrder.setStatus(OrderStatus.CANCELED);
            return OrderStatus.CANCELED;
        }
        newOrder.setStatus(OrderStatus.CONFIRMED);
        return OrderStatus.CONFIRMED;
    }

    @Override public List<Product> searchproduct(String name) { return Collections.emptyList(); }
}

// -------------------- Order --------------------
class Order {
    private final String orderNumber;
    private final AuthenticatedUser customer;
    private final Date orderDate;
    private volatile OrderStatus status;
    private final ShoppingCart shoppingCart;

    public Order(String orderNumber, OrderStatus status) { this(orderNumber, status, null); }

    public Order(String orderNumber, OrderStatus status, AuthenticatedUser user) {
        this.orderNumber = orderNumber;
        this.orderDate = new Date();
        this.status = status;
        this.customer = user;
        this.shoppingCart = (user == null) ? new ShoppingCart() : user.getShoppingCart();
    }

    public synchronized boolean sendForShipment() {
        if (status != OrderStatus.CONFIRMED && status != OrderStatus.PENDING) return false;
        status = OrderStatus.SHIPPED;
        return true;
    }

    public synchronized PaymentStatus makePayment(Payment payment) {
        if (payment == null) return PaymentStatus.DECLINED;
        PaymentStatus ps = payment.makePayment();
        if (ps == PaymentStatus.CONFIRMED) this.status = OrderStatus.CONFIRMED;
        else this.status = OrderStatus.PENDING;
        return ps;
    }

    public synchronized boolean verify(CartItem item) {
        if (item == null) return shoppingCart.verify();
        Product p = item.getProduct();
        if (p == null) return false;
        int qty = item.getQuantity();
        int after = p.decrementAvailableCount(qty);
        return after >= 0;
    }

    public void setStatus(OrderStatus status) { this.status = status; }
    public OrderStatus getStatus() { return status; }
    public String getOrderNumber() { return orderNumber; }
}

// -------------------- Shipment --------------------
class Shipment {
    private final String shipmentNumber;
    private final Date shipmentDate;
    private final Date estimatedArrival;
    private final String shipmentMethod;
    private ShipmentStatus status;

    public Shipment(String shipmentNumber, String shipmentMethod, Date estimatedArrival, ShipmentStatus status) {
        this.shipmentNumber = shipmentNumber;
        this.shipmentDate = new Date();
        this.estimatedArrival = estimatedArrival;
        this.shipmentMethod = shipmentMethod;
        this.status = status;
    }
}

// -------------------- Payment --------------------
abstract class Payment {
    protected double amount;
    protected Date timestamp;
    protected PaymentStatus status;

    public Payment(double amount) { this.amount = amount; this.timestamp = new Date(); this.status = PaymentStatus.PENDING; }
    public abstract PaymentStatus makePayment();
}

class CreditCard extends Payment {
    private final String nameOnCard;
    private final String cardNumber;
    private final String billingAddress;
    private final int code;

    public CreditCard(double amount, String nameOnCard, String cardNumber, String billingAddress, int code) {
        super(amount);
        this.nameOnCard = nameOnCard; this.cardNumber = cardNumber; this.billingAddress = billingAddress; this.code = code;
    }

    @Override public PaymentStatus makePayment() {
        if (cardNumber == null || cardNumber.length() < 12) { this.status = PaymentStatus.DECLINED; return status; }
        this.status = PaymentStatus.CONFIRMED; return status;
    }
}

class ElectronicBankTransfer extends Payment {
    private final String bankName;
    private final String routingNumber;
    private final String accountNumber;
    private final String billingAddress;

    public ElectronicBankTransfer(double amount, String bankName, String routingNumber, String accountNumber, String billingAddress) {
        super(amount);
        this.bankName = bankName; this.routingNumber = routingNumber; this.accountNumber = accountNumber; this.billingAddress = billingAddress;
    }

    @Override public PaymentStatus makePayment() {
        if (accountNumber == null || accountNumber.length() < 6) { this.status = PaymentStatus.DECLINED; return status; }
        this.status = PaymentStatus.CONFIRMED; return status;
    }
}

class Cash extends Payment {
    private final String billingAddress;
    public Cash(double amount, String billingAddress) { super(amount); this.billingAddress = billingAddress; }
    @Override public PaymentStatus makePayment() { this.status = PaymentStatus.CONFIRMED; return status; }
}

// -------------------- Notification --------------------
abstract class Notification {
    protected int notificationId;
    protected Date createdOn;
    protected String content;

    public Notification(String content) { this.notificationId = new Random().nextInt(100000); this.createdOn = new Date(); this.content = content; }
    public abstract boolean sendNotification(Account account);
}

// -------------------- Search service --------------------
class Search {
    private final ConcurrentMap<String, CopyOnWriteArrayList<Product>> products = new ConcurrentHashMap<>();

    public void setProducts(Map<String, List<Product>> productsMap) {
        if (productsMap == null) return;
        for (Map.Entry<String, List<Product>> e : productsMap.entrySet()) {
            products.put(e.getKey().toLowerCase(), new CopyOnWriteArrayList<>(e.getValue()));
        }
    }

    public List<Product> searchProductsByName(String name) {
        if (name == null) return Collections.emptyList();
        String lower = name.toLowerCase();
        List<Product> result = new ArrayList<>();
        for (CopyOnWriteArrayList<Product> list : products.values()) {
            for (Product p : list) {
                if (p.getName().toLowerCase().contains(lower) || p.getDescription().toLowerCase().contains(lower)) result.add(p);
            }
        }
        return result;
    }

    public List<Product> searchProductsByCategory(String category) {
        if (category == null) return Collections.emptyList();
        List<Product> list = products.get(category.toLowerCase());
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    public Product getProductDetails(String productId) {
        if (productId == null) return null;
        for (CopyOnWriteArrayList<Product> list : products.values()) {
            for (Product p : list) if (p.getProductId().equals(productId)) return p;
        }
        return null;
    }
}

// -------------------- Driver (demo) --------------------
public class Driver {
    public static void main(String[] args) {
        System.out.println("========== Welcome to the Amazon Online Shopping System ==========");

        // Scenario 1: Guest Browses and Registers
        System.out.println(" ==== Scenario 1: Guest Browses and Registers ====");
        Guest guest = new Guest();
        System.out.println("🧑 Guest browses the platform...");
        guest.registerAccount("john_doe", "password123", "john@example.com");

        // Scenario 2: Authenticated User Adds Items to Cart and Places an Order
        System.out.println(" ==== Scenario 2: Authenticated User Adds Items to Cart and Places an Order ====");

        Account johnAccount = new Account();
        johnAccount.setStatus(AccountStatus.ACTIVE);
        AuthenticatedUser john = new AuthenticatedUser();
        john.setAccount(johnAccount);

        Search search = new Search();
        ProductCategory electronics = new ProductCategory("Electronics", "Devices and gadgets");
        Product laptop = new Product("P001", "Gaming Laptop", "Powerful 16GB RAM laptop", null, 1500.00, electronics, 5, null);
        Product mouse = new Product("P002", "Wireless Mouse", "Ergonomic wireless mouse", null, 25.00, electronics, 10, null);

        electronics.addProduct(laptop);
        electronics.addProduct(mouse);

        List<Product> electronicsList = new ArrayList<>();
        electronicsList.add(laptop);
        electronicsList.add(mouse);
        Map<String, List<Product>> productMap = new HashMap<>();
        productMap.put("electronics", electronicsList);
        search.setProducts(productMap);

        System.out.println("📦 Products available in 'Electronics':");
        for (Product p : search.searchProductsByCategory("electronics")) {
            System.out.println(" - " + p.getName() + ": $" + p.getPrice());
        }

        System.out.println("🛒 User adds items to shopping cart:");
        john.addItem(laptop, 1);
        john.addItem(mouse, 2);

        System.out.println("🧾 Shopping Cart Summary:");
        ShoppingCart cart = john.getShoppingCart();
        for (CartItem item : cart.getItems()) {
            System.out.println(" - " + item.getProduct().getName() + " x " + item.getQuantity() + " = $" + item.getPrice());
        }
        System.out.println("Total: $" + cart.getTotalPrice());

        if (cart.verify()) System.out.println("✅ Cart verified. Proceeding to place order...");
        CartItem itemToOrder = cart.getItems().get(0);
        OrderStatus status = john.placeOrder(itemToOrder, itemToOrder.getPrice());

        System.out.println(" ==== Scenario 3: Order Payment with Credit Card ====");
        Payment payment = new CreditCard(itemToOrder.getPrice(), "John Doe", "1234567812345678", "123 Street, NY", 123);

        Order order = new Order("ORD123", status);
        PaymentStatus paymentStatus = order.makePayment(payment);

        Shipment shipment = new Shipment("SHIP123", "FedEx", new Date(System.currentTimeMillis() + 3 * 86400000), ShipmentStatus.PENDING);

        System.out.println("💳 Payment Status: " + paymentStatus);
        System.out.println("📑 Order Status: " + status);

        System.out.println("🎉 Thank you for shopping with us!");
    }
}

