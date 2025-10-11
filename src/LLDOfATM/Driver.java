package LLDOfATM;

import java.util.*;

// ================= ENUMS =================
enum ATMStatus {
    Idle,
    HasCard,
    SelectionOption,
    Withdraw,
    TransferMoney,
    BalanceInquiry,
    ChangePIN
}

enum TransactionType {
    BalanceInquiry,
    CashWithdrawal,
    FundsTransfer,
    ChangePIN,
    Cancel
}

// ================= USER & BANK MODELS =================
class User {
    private ATMCard card;
    private BankAccount account;

    public User(ATMCard card, BankAccount account) {
        this.card = card;
        this.account = account;
    }

    public ATMCard getCard() {
        return card;
    }

    public BankAccount getAccount() {
        return account;
    }
}

class ATMCard {
    private String cardNumber;
    private String customerName;
    private String cardExpiryDate;
    private int pin;

    public ATMCard(String cardNumber, String customerName, String cardExpiryDate, int pin) {
        this.cardNumber = cardNumber;
        this.customerName = customerName;
        this.cardExpiryDate = cardExpiryDate;
        this.pin = pin;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public int getPin() {
        return pin;
    }

    public void setPin(int newPin) {
        this.pin = newPin;
    }
}

class Bank {
    private String name;
    private String bankCode;

    public Bank(String name, String bankCode) {
        this.name = name;
        this.bankCode = bankCode;
    }

    public String getBankCode() {
        return bankCode;
    }
}

// ================= BANK ACCOUNTS =================
abstract class BankAccount {
    protected int accountNumber;
    protected double totalBalance;
    protected double availableBalance;

    public BankAccount(int accountNumber, double balance) {
        this.accountNumber = accountNumber;
        this.totalBalance = balance;
        this.availableBalance = balance;
    }

    public double getAvailableBalance() {
        return availableBalance;
    }

    public boolean withdraw(double amount) {
        if (amount <= availableBalance) {
            availableBalance -= amount;
            return true;
        }
        return false;
    }

    public boolean transfer(BankAccount toAccount, double amount) {
        if (amount <= availableBalance) {
            availableBalance -= amount;
            toAccount.availableBalance += amount;
            return true;
        }
        return false;
    }

    public abstract double getWithdrawLimit();
}

class SavingAccount extends BankAccount {
    public SavingAccount(int accountNumber, double balance) {
        super(accountNumber, balance);
    }

    @Override
    public double getWithdrawLimit() {
        return 1000.0;
    }
}

class CurrentAccount extends BankAccount {
    public CurrentAccount(int accountNumber, double balance) {
        super(accountNumber, balance);
    }

    @Override
    public double getWithdrawLimit() {
        return 5000.0;
    }
}

// ================= HARDWARE COMPONENTS =================
class CardReader {
    public boolean readCard(ATMCard card) {
        return card != null;
    }
}

class CashDispenser {
    public boolean dispenseCash(int amount) {
        System.out.println("Dispensing cash: $" + amount);
        return true;
    }
}

class Keypad {
    public String getInput() {
        return "";
    }
}

class Screen {
    public void showMessage(String message) {
        System.out.println(message);
    }
}

class Printer {
    public void printReceipt(String details) {
        System.out.println("Receipt: " + details);
    }
}

// ================= STATE PATTERN =================
abstract class ATMState {
    public void insertCard(ATM atm, ATMCard card) {}
    public void authenticatePin(ATM atm, ATMCard card, int pin) {}
    public void selectOperation(ATM atm, TransactionType tType) {}
    public void cashWithdrawal(ATM atm, ATMCard card, double amount) {}
    public void displayBalance(ATM atm, ATMCard card) {}
    public void transferMoney(ATM atm, ATMCard card, BankAccount toAccount, double amount) {}
    public void changePin(ATM atm, ATMCard card, int newPin) {}
    public void cancelTransaction(ATM atm) {}
    public void returnCard(ATM atm) {}
    public void exit(ATM atm) {}
}

// ===== IdleState =====
class IdleState extends ATMState {
    @Override
    public void insertCard(ATM atm, ATMCard card) {
        if (atm.getCardReader().readCard(card)) {
            atm.setAtmStatus(ATMStatus.HasCard);
            atm.setCurrentATMState(new HasCardState());
            atm.getScreen().showMessage("Card inserted successfully. Please enter your PIN.");
        } else {
            atm.getScreen().showMessage("Failed to read card. Try again.");
        }
    }
}

// ===== HasCardState =====
class HasCardState extends ATMState {
    @Override
    public void authenticatePin(ATM atm, ATMCard card, int pin) {
        if (card.getPin() == pin) {
            atm.getScreen().showMessage("PIN verified successfully.");
            atm.setAtmStatus(ATMStatus.SelectionOption);
            atm.setCurrentATMState(new SelectionOptionState());
        } else {
            atm.getScreen().showMessage("Incorrect PIN. Please try again.");
        }
    }
}

// ===== SelectionOptionState =====
class SelectionOptionState extends ATMState {
    @Override
    public void selectOperation(ATM atm, TransactionType tType) {
        switch (tType) {
            case BalanceInquiry:
                atm.setCurrentATMState(new BalanceInquiryState());
                atm.setAtmStatus(ATMStatus.BalanceInquiry);
                break;
            case CashWithdrawal:
                atm.setCurrentATMState(new CashWithdrawalState());
                atm.setAtmStatus(ATMStatus.Withdraw);
                break;
            case FundsTransfer:
                atm.setCurrentATMState(new TransferMoneyState());
                atm.setAtmStatus(ATMStatus.TransferMoney);
                break;
            case ChangePIN:
                atm.setCurrentATMState(new ChangePinState());
                atm.setAtmStatus(ATMStatus.ChangePIN);
                break;
            case Cancel:
                atm.getScreen().showMessage("Transaction cancelled.");
                atm.setCurrentATMState(new IdleState());
                atm.setAtmStatus(ATMStatus.Idle);
                atm.returnCard();
                break;
        }
    }
}

// ===== BalanceInquiryState =====
class BalanceInquiryState extends ATMState {
    @Override
    public void displayBalance(ATM atm, ATMCard card) {
        double balance = atm.getActiveUser().getAccount().getAvailableBalance();
        atm.getScreen().showMessage("Available Balance: $" + balance);
        atm.getPrinter().printReceipt("Balance Inquiry: $" + balance);
    }

    @Override
    public void returnCard(ATM atm) {
        atm.returnCard();
    }
}

// ===== CashWithdrawalState =====
class CashWithdrawalState extends ATMState {
    @Override
    public void cashWithdrawal(ATM atm, ATMCard card, double amount) {
        BankAccount account = atm.getActiveUser().getAccount();
        double limit = account.getWithdrawLimit();
        if (amount > limit) {
            atm.getScreen().showMessage("Amount exceeds withdraw limit of $" + limit);
            return;
        }
        if (account.withdraw(amount)) {
            atm.getCashDispenser().dispenseCash((int) amount);
            atm.getPrinter().printReceipt("Withdrawn: $" + amount);
            atm.getScreen().showMessage("Withdrawal successful.");
        } else {
            atm.getScreen().showMessage("Insufficient balance.");
        }
    }

    @Override
    public void returnCard(ATM atm) {
        atm.returnCard();
    }
}

// ===== TransferMoneyState =====
class TransferMoneyState extends ATMState {
    @Override
    public void transferMoney(ATM atm, ATMCard card, BankAccount toAccount, double amount) {
        BankAccount fromAccount = atm.getActiveUser().getAccount();
        if (fromAccount.transfer(toAccount, amount)) {
            atm.getPrinter().printReceipt("Transferred $" + amount + " successfully.");
            atm.getScreen().showMessage("Transfer successful.");
        } else {
            atm.getScreen().showMessage("Transfer failed. Insufficient balance.");
        }
    }

    @Override
    public void returnCard(ATM atm) {
        atm.returnCard();
    }
}

// ===== ChangePinState =====
class ChangePinState extends ATMState {
    @Override
    public void changePin(ATM atm, ATMCard card, int newPin) {
        card.setPin(newPin);
        atm.getScreen().showMessage("PIN changed successfully to: " + newPin);
        atm.getPrinter().printReceipt("PIN changed successfully.");
    }

    @Override
    public void returnCard(ATM atm) {
        atm.returnCard();
    }
}

// ================= ATM CLASS =================
class ATM {
    private static ATM atmObject = new ATM();
    private ATMState currentATMState;
    private ATMStatus atmStatus;
    private int atmBalance;
    private int noOfHundredDollarBills;
    private int noOfFiftyDollarBills;
    private int noOfTenDollarBills;

    private CardReader cardReader;
    private CashDispenser cashDispenser;
    private Keypad keypad;
    private Screen screen;
    private Printer printer;

    private User activeUser;

    private ATM() {
        this.cardReader = new CardReader();
        this.cashDispenser = new CashDispenser();
        this.keypad = new Keypad();
        this.screen = new Screen();
        this.printer = new Printer();
        this.currentATMState = new IdleState();
        this.atmStatus = ATMStatus.Idle;
    }

    public static ATM getInstance() {
        return atmObject;
    }

    public void initializeATM(int atmBalance, int noOfHundredDollarBills, int noOfFiftyDollarBills, int noOfTenDollarBills) {
        this.atmBalance = atmBalance;
        this.noOfHundredDollarBills = noOfHundredDollarBills;
        this.noOfFiftyDollarBills = noOfFiftyDollarBills;
        this.noOfTenDollarBills = noOfTenDollarBills;
    }

    public ATMState getCurrentATMState() {
        return currentATMState;
    }

    public void setCurrentATMState(ATMState state) {
        this.currentATMState = state;
    }

    public void setAtmStatus(ATMStatus status) {
        this.atmStatus = status;
    }

    public ATMStatus getAtmStatus() {
        return atmStatus;
    }

    public CardReader getCardReader() {
        return cardReader;
    }

    public CashDispenser getCashDispenser() {
        return cashDispenser;
    }

    public Screen getScreen() {
        return screen;
    }

    public Printer getPrinter() {
        return printer;
    }

    public void setActiveUser(User user) {
        this.activeUser = user;
    }

    public User getActiveUser() {
        return activeUser;
    }

    public void returnCard() {
        screen.showMessage("Returning card...");
        setAtmStatus(ATMStatus.Idle);
        setCurrentATMState(new IdleState());
    }
}

// ================= DRIVER (Simulation) =================
public class Driver {
    public static void main(String[] args) {
        Bank bank = new Bank("Sample Bank", "SB001");

        BankAccount acc1 = new SavingAccount(1001, 1200.0);
        BankAccount acc2 = new CurrentAccount(1002, 8000.0);
        ATMCard card1 = new ATMCard("123456", "Alice", "12/27", 1111);
        ATMCard card2 = new ATMCard("654321", "Bob", "08/26", 2222);

        User user1 = new User(card1, acc1);
        User user2 = new User(card2, acc2);

        ATM atm = ATM.getInstance();
        atm.initializeATM(20000, 100, 40, 50);

        System.out.println("=== Scenario 1: Alice - Balance Inquiry ===");
        atm.setActiveUser(user1);
        atm.getCurrentATMState().insertCard(atm, user1.getCard());
        atm.getCurrentATMState().authenticatePin(atm, user1.getCard(), 1111);
        atm.getCurrentATMState().selectOperation(atm, TransactionType.BalanceInquiry);
        atm.getCurrentATMState().displayBalance(atm, user1.getCard());
        atm.getCurrentATMState().returnCard(atm);

        System.out.println("\n=== Scenario 2: Alice - Withdraw $500 ===");
        atm.setActiveUser(user1);
        atm.getCurrentATMState().insertCard(atm, user1.getCard());
        atm.getCurrentATMState().authenticatePin(atm, user1.getCard(), 1111);
        atm.getCurrentATMState().selectOperation(atm, TransactionType.CashWithdrawal);
        atm.getCurrentATMState().cashWithdrawal(atm, user1.getCard(), 500.0);
        atm.getCurrentATMState().returnCard(atm);

        System.out.println("\n=== Scenario 3: Bob - Transfer $1000 to Alice ===");
        atm.setActiveUser(user2);
        atm.getCurrentATMState().insertCard(atm, user2.getCard());
        atm.getCurrentATMState().authenticatePin(atm, user2.getCard(), 2222);
        atm.getCurrentATMState().selectOperation(atm, TransactionType.FundsTransfer);
        atm.getCurrentATMState().transferMoney(atm, user2.getCard(), acc1, 1000.0);
        atm.getCurrentATMState().returnCard(atm);

        System.out.println("\n=== Scenario 4: Bob - Change PIN ===");
        atm.setActiveUser(user2);
        atm.getCurrentATMState().insertCard(atm, user2.getCard());
        atm.getCurrentATMState().authenticatePin(atm, user2.getCard(), 2222);
        atm.getCurrentATMState().selectOperation(atm, TransactionType.ChangePIN);
        atm.getCurrentATMState().changePin(atm, user2.getCard(), 9999);
        atm.getCurrentATMState().returnCard(atm);

        System.out.println("\n=== Scenario 5: Alice - Cancel transaction after PIN ===");
        atm.setActiveUser(user1);
        atm.getCurrentATMState().insertCard(atm, user1.getCard());
        atm.getCurrentATMState().authenticatePin(atm, user1.getCard(), 1111);
        atm.getCurrentATMState().selectOperation(atm, TransactionType.Cancel);
    }
}
