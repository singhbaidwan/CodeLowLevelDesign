package StockBrokerageSystem;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

// ---------------- ENUMERATIONS ----------------
enum OrderStatus { OPEN, FILLED, PARTIALLY_FILLED, CANCELED }
enum TimeEnforcementType { GOOD_TILL_CANCELED, FILL_OR_KILL, IMMEDIATE_OR_CANCEL, ON_THE_OPEN, ON_THE_CLOSE }
enum AccountStatus { ACTIVE, CLOSED, CANCELED, BLACKLISTED, NONE }
enum ErrorCode { SUCCESS, INSUFFICIENT_FUNDS, INVALID_STOCK, ORDER_REJECTED }

// ---------------- BASIC ENTITIES ----------------
class Address {
    private int zipCode;
    private String address, city, state, country;
    public Address() {}
    public Address(int z, String a, String c, String s, String co) {
        zipCode=z; address=a; city=c; state=s; country=co;
    }
}

// ---------------- STOCK ----------------
class Stock {
    private String symbol;
    private double price;

    public Stock() {}
    public Stock(String s, double p) { symbol=s; price=p; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String s) { symbol=s; }
    public double getPrice() { return price; }
    public void setPrice(double p) { price=p; }
}

class Watchlist {
    private String name;
    private final List<Stock> stocks = new CopyOnWriteArrayList<>();
    public Watchlist(String n) { name=n; }
    public void addStock(Stock s){ stocks.add(s); }
}

// ---------------- SEARCH + INVENTORY ----------------
interface Search {
    Stock searchSymbol(String symbol);
}

class StockInventory implements Search {
    private final String inventoryName = "DefaultInventory";
    private Date lastUpdate = new Date();
    private final ConcurrentHashMap<String, Stock> stocks = new ConcurrentHashMap<>();

    public StockInventory() {
        stocks.put("AAPL", new Stock("AAPL", 180.50));
        stocks.put("GOOG", new Stock("GOOG", 150.25));
        stocks.put("TSLA", new Stock("TSLA", 220.10));
    }

    @Override
    public Stock searchSymbol(String symbol) {
        return stocks.get(symbol);
    }

    public boolean deductStock(String symbol, double quantity) {
        // simulate lock per stock
        return stocks.containsKey(symbol);
    }

    public boolean sendOrderDetails(Order order) {
        System.out.println("[INVENTORY] Order details sent for " + order.getOrderNumber());
        return true;
    }
}

// ---------------- POSITIONS & LOTS ----------------
class StockPosition {
    private String symbol;
    private double quantity;
}

class StockLot {
    private String lotNumber;
    private Order buyingOrder;
    public double getBuyingPrice() { return 100.0; }
}

// ---------------- ORDER COMPONENTS ----------------
class OrderPart {
    private double price;
    private double quantity;
    private Date executedAt;
    public OrderPart(double p, double q) { price=p; quantity=q; executedAt=new Date(); }
    public double getPrice(){return price;}
    public double getQuantity(){return quantity;}
}

// ---------------- ABSTRACT ORDER ----------------
abstract class Order {
    private String orderNumber;
    protected boolean isBuyOrder;
    private OrderStatus status = OrderStatus.OPEN;
    private TimeEnforcementType timeEnforcement;
    private Date creationTime;
    private final ConcurrentHashMap<Integer, OrderPart> parts = new ConcurrentHashMap<>();

    // concurrency lock
    private final Lock lock = new ReentrantLock();

    public void setOrderNumber(String n){orderNumber=n;}
    public String getOrderNumber(){return orderNumber;}
    public void setIsBuyOrder(boolean b){isBuyOrder=b;}
    public boolean getIsBuyOrder(){return isBuyOrder;}
    public void setStatus(OrderStatus s){status=s;}
    public OrderStatus getStatus(){return status;}
    public void setTimeEnforcement(TimeEnforcementType t){timeEnforcement=t;}
    public TimeEnforcementType getTimeEnforcement(){return timeEnforcement;}
    public void setCreationTime(Date d){creationTime=d;}
    public Date getCreationTime(){return creationTime;}

    public boolean saveInDatabase() {
        // simulate database persistence
        return true;
    }

    public void addOrderParts(OrderPart part) {
        lock.lock();
        try {
            parts.put(parts.size() + 1, part);
        } finally { lock.unlock(); }
    }

    public abstract boolean execute();
}

class LimitOrder extends Order {
    private double priceLimit;
    public void setPriceLimit(double p){priceLimit=p;}
    public double getPriceLimit(){return priceLimit;}
    @Override
    public boolean execute() {
        System.out.println("[EXECUTE] Limit order executed at $" + priceLimit);
        return true;
    }
}

class StopLimitOrder extends Order {
    private double priceLimit;
    public void setPriceLimit(double p){priceLimit=p;}
    public double getPriceLimit(){return priceLimit;}
    @Override
    public boolean execute() { return true; }
}

class StopLossOrder extends Order {
    private double priceLimit;
    public void setPriceLimit(double p){priceLimit=p;}
    public double getPriceLimit(){return priceLimit;}
    @Override
    public boolean execute() { return true; }
}

class MarketOrder extends Order {
    private double marketPrice;
    public void setMarketPrice(double p){marketPrice=p;}
    public double getMarketPrice(){return marketPrice;}
    @Override
    public boolean execute() { return true; }
}

// ---------------- TRANSFER MONEY ----------------
abstract class TransferMoney {
    protected int id;
    protected Date creationDate;
    protected int fromAccount;
    protected int toAccount;

    public abstract boolean initiateTransaction();
}

class ElectronicBank extends TransferMoney {
    private String bankName;
    @Override
    public boolean initiateTransaction() {
        System.out.println("[BANK TRANSFER] Initiated from " + fromAccount + " to " + toAccount);
        return true;
    }
}

class Wire extends TransferMoney {
    private int wire;
    @Override
    public boolean initiateTransaction() {
        System.out.println("[WIRE] Initiated wire " + wire);
        return true;
    }
}

class Check extends TransferMoney {
    private String checkNumber;
    @Override
    public boolean initiateTransaction() {
        System.out.println("[CHECK] Check no " + checkNumber + " processed");
        return true;
    }
}

class DepositMoney {
    private int transactionId;
    public boolean initiateTransaction(){return true;}
}

class WithdrawMoney {
    private int transactionId;
    public boolean initiateTransaction(){return true;}
}

// ---------------- NOTIFICATIONS ----------------
abstract class Notification {
    private String notificationId;
    private Date creationDate;
    private String content;

    public void setCreationDate(Date d){creationDate=d;}
    public void setContent(String c){content=c;}
    public String getContent(){return content;}

    public abstract boolean sendNotification();
}

class SmsNotification extends Notification {
    private int phoneNumber;
    public void setPhoneNumber(int n){phoneNumber=n;}
    @Override
    public boolean sendNotification() {
        System.out.println("[SMS] Sent to " + phoneNumber + ": " + getContent());
        return true;
    }
}

class EmailNotification extends Notification {
    private String email;
    public void setEmail(String e){email=e;}
    public String getEmail(){return email;}
    @Override
    public boolean sendNotification() {
        System.out.println("[EMAIL] Sent to " + email + ": " + getContent());
        return true;
    }
}

// ---------------- STOCK EXCHANGE (Singleton, thread-safe) ----------------
class StockExchange {
    private static volatile StockExchange instance;
    private static final Lock lock = new ReentrantLock();
    private final ExecutorService orderExecutor = Executors.newFixedThreadPool(4);

    private StockExchange(){}

    public static StockExchange getInstance() {
        if (instance == null) {
            lock.lock();
            try {
                if (instance == null) instance = new StockExchange();
            } finally { lock.unlock(); }
        }
        return instance;
    }

    public boolean placeOrder(Order order) {
        System.out.println("[EXCHANGE] Placing order " + order.getOrderNumber());
        // submit async
        Future<Boolean> result = orderExecutor.submit(order::execute);
        try {
            return result.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean acknowledge(Order order) {
        System.out.println("[EXCHANGE] Acknowledging order " + order.getOrderNumber());
        order.setStatus(OrderStatus.FILLED);
        return true;
    }
}

// ---------------- MEMBER ----------------
class Member {
    private static final AtomicInteger MEMBER_ID_GEN = new AtomicInteger(1);
    private int id;
    private String name;
    private double availableFundsForTrading;
    private final List<String> selectedStocks = Collections.synchronizedList(new ArrayList<>());

    public Member() { id = MEMBER_ID_GEN.getAndIncrement(); }
    public void setName(String n){name=n;}
    public String getName(){return name;}
    public void setAvailableFundsForTrading(double f){availableFundsForTrading=f;}
    public double getAvailableFundsForTrading(){return availableFundsForTrading;}
    public void selectStock(String symbol){
        synchronized (selectedStocks){selectedStocks.add(symbol);}
    }
}

// ---------------- DRIVER / MAIN ----------------
public class Driver {
    public static void main(String[] args) {
        System.out.println("========== ONLINE STOCK BROKERAGE SYSTEM ==========");

        // --------------------------------------------------
        // SYSTEM INITIALIZATION
        // --------------------------------------------------
        System.out.println("\n--- SYSTEM INITIALIZATION ---");
        StockInventory inventory = new StockInventory();
        StockExchange exchange = StockExchange.getInstance();

        Member member = new Member();
        member.setName("Alice");
        member.setAvailableFundsForTrading(10000.0);
        System.out.println("[INFO] Member Initialized: " + member.getName());
        System.out.println("[INFO] Available Funds: $" + member.getAvailableFundsForTrading());

        // --------------------------------------------------
        // SCENARIO 1: SEARCHING AND SELECTING A STOCK
        // --------------------------------------------------
        System.out.println("\n--- SCENARIO 1: SEARCHING AND SELECTING A STOCK ---");
        String symbolToSearch = "AAPL";
        System.out.println("[STEP] Searching for stock with symbol: " + symbolToSearch);
        Stock stock = inventory.searchSymbol(symbolToSearch);

        if (stock == null) {
            System.out.println("[WARNING] Stock not found. Creating mock data.");
            stock = new Stock(symbolToSearch, 180.50);
        }

        System.out.println("[RESULT] Stock Found: " + stock.getSymbol() + " @ $" + stock.getPrice());
        member.selectStock(stock.getSymbol());
        double quantity = 10;
        System.out.println("[ACTION] Selected Quantity: " + quantity);

        // --------------------------------------------------
        // SCENARIO 2: PLACING A LIMIT ORDER
        // --------------------------------------------------
        System.out.println("\n--- SCENARIO 2: PLACING A LIMIT ORDER ---");
        LimitOrder order = new LimitOrder();
        order.setIsBuyOrder(true);
        order.setOrderNumber("ORD123");
        order.setPriceLimit(stock.getPrice() - 5);
        order.setTimeEnforcement(TimeEnforcementType.GOOD_TILL_CANCELED);
        order.setCreationTime(new Date());

        System.out.println("[ORDER] Type: Limit Order");
        System.out.println("[ORDER] Number: " + order.getOrderNumber());
        System.out.println("[ORDER] Buy: " + order.getIsBuyOrder());
        System.out.println("[ORDER] Limit: $" + order.getPriceLimit());
        System.out.println("[ORDER] Time Enforcement: " + order.getTimeEnforcement());
        System.out.println("[ORDER] Created: " + order.getCreationTime());

        boolean orderPlaced = exchange.placeOrder(order);
        inventory.sendOrderDetails(order);
        System.out.println(orderPlaced ? "[SUCCESS] Order placed successfully." : "[FAILED] Order placement failed.");

        boolean ack = exchange.acknowledge(order);
        System.out.println("[EXCHANGE] Order Acknowledged: " + ack);

        // --------------------------------------------------
        // SCENARIO 3: SENDING ORDER NOTIFICATION
        // --------------------------------------------------
        System.out.println("\n--- SCENARIO 3: SENDING ORDER NOTIFICATION ---");
        EmailNotification notification = new EmailNotification();
        notification.setCreationDate(new Date());
        notification.setContent("Your order " + order.getOrderNumber() + " has been received.");
        notification.setEmail("alice@example.com");
        boolean sent = notification.sendNotification();
        System.out.println("[NOTIFICATION] Email sent to " + notification.getEmail() + ": " + sent);

        // --------------------------------------------------
        System.out.println("\n===================================================");
        System.out.println("   Thank you for trading with us, " + member.getName());
        System.out.println("===================================================");
    }
}
