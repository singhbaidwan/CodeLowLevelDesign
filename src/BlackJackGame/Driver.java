package BlackJackGame;

import java.util.*;

enum Suit{
    HEART,
    SPADE,
    CLUB,
    DIAMOND
}

enum AccountStatus{
    ACTIVE,CLOSED,CANCELLED,BLACKLISTED,NONE
}

class Person{
    private String name;
    private String streetAddress;
    private String state;
    private String city;
    private String country;

    public Person(String name) {
        this.name = name;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public String getState() {
        return state;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public String getName() {
        return name;
    }
}

class Card{
    private Suit suit;
    private int faceValue;

    public Card(Suit suit, int faceValue) {
        this.suit = suit;
        this.faceValue = faceValue;
    }

    public void setSuit(Suit suit) {
        this.suit = suit;
    }

    public void setFaceValue(int faceValue) {
        this.faceValue = faceValue;
    }

    public Suit getSuit() {
        return suit;
    }

    public int getFaceValue() {
        return faceValue;
    }

    public int getValue(){
        if(faceValue >= 2 && faceValue <= 10) return faceValue;
        if(faceValue >= 11 && faceValue <=13) return 10;
        return 11;
    }

    @Override
    public String toString() {
        String fv;
        switch (faceValue) {
            case 1: fv = "A"; break;
            case 11: fv = "J"; break;
            case 12: fv = "Q"; break;
            case 13: fv = "K"; break;
            default: fv = String.valueOf(faceValue);
        }
        return fv + "-" + suit.toString().charAt(0);
    }
}

class Deck{
    private List<Card> cards;

    public Deck() {
        cards =  new ArrayList<>(52);
        for(Suit s: Suit.values()){
            for(int i = 1; i<=13 ; i++){
                cards.add(new Card(s,i));
            }
        }
    }

    public List<Card> getCards() {
        return cards;
    }
}

class Shoe{
    private LinkedList<Card> cards = new LinkedList<>();

    public Shoe(int numberOfDecks) {
        for(int i = 0 ; i < numberOfDecks; i++){
            Deck d = new Deck();
            cards.addAll(d.getCards());
        }
        shuffle();
    }
    public void shuffle(){
        Collections.shuffle(cards,new Random());
    }
    public Card dealCard(){
        if(cards.isEmpty()) return null;
        return cards.pollFirst();
    }

    public LinkedList<Card> getCards() {
        return cards;
    }
}

class Hand{
    private List<Card> cards  = new ArrayList<>();
    public Hand(Card card1, Card card2){
        cards.add(card1);
        cards.add(card2);
    }
    public void addCard(Card card){
        cards.add(card);
    }

    public List<Card> getCards() {
        return cards;
    }

    public int getScore(){
        int total = 0;
        int aceCount = 0;
        for(Card c : cards){
            if(c.getFaceValue() == 1){
                aceCount+=1;
                total+=11;
            } else {
                total += c.getValue();
            }
        }
        while(total > 21 && aceCount > 0){
            total -= 10;
            aceCount-=1;
        }
        return total;
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Card c : cards) sb.append(c.toString()).append(" ");
        sb.append("(score=").append(getScore()).append(")");
        return sb.toString();
    }
}


abstract class Player{
    protected String id;
    protected String password;
    protected  double balance;
    protected  AccountStatus accountStatus;
    protected  Person person;
    protected  Hand hand;

    public Player(String id, String password, double balance, Person person) {
        this.id = id;
        this.password = password;
        this.balance = balance;
        this.person = person;
        this.accountStatus = AccountStatus.ACTIVE;
    }

    public Hand getHand() {
        return hand;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void setHand(Hand hand) {
        this.hand = hand;
    }

    public abstract boolean resetPassword();
}

class BlackjackPlayer extends Player{
    private double bet;

    public BlackjackPlayer(String id, String password, double balance, Person person) {
        super(id, password, balance, person);
    }

    public void placeBet(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Bet must be positive");
        if (amount > balance) throw new IllegalArgumentException("Insufficient balance");
        bet = amount;
        balance -= amount;
    }

    public double getBet() {
        return bet;
    }

    public double payout(double multiplier){
        double payoutAmount = bet * multiplier;
        balance += payoutAmount;
        bet = 0;
        return payoutAmount;
    }
    @Override
    public boolean resetPassword() {
        this.password = "reset123";
        return true;
    }
}

class BlackjackController {
    public boolean validateAction(String action) {
        return "hit".equalsIgnoreCase(action) || "stand".equalsIgnoreCase(action);
    }
}


class BlackjackGameView {
    public void showHands(Hand player, Hand dealer, boolean hideDealerHole) {
        System.out.println("Player: " + player.toString());
        if (hideDealerHole) {
            List<Card> dealerCards = dealer.getCards();
            if (!dealerCards.isEmpty()) {
                System.out.println("Dealer: " + dealerCards.get(0).toString() + " [HIDDEN]");
            }
        } else {
            System.out.println("Dealer: " + dealer.toString());
        }
    }

    public void showResult(String result, double payout) {
        System.out.println("Result: " + result + " | Payout: " + payout);
    }

    public void announceBet(double amount, double balanceAfterBet) {
        System.out.println("Player bet: $" + amount + " | Balance after bet: $" + balanceAfterBet);
    }

    public void announcePlayerAction(String action) {
        System.out.println("Player action: " + action);
    }

    public void announceForfeit(double balance) {
        System.out.println("Player forfeited. Balance: $" + balance);
    }
}


class Dealer extends Player {
    public Dealer(String id, String password, double balance, Person person) {
        super(id, password, balance, person);
    }

    @Override
    public boolean resetPassword() { return false; }
}


class BlackjackGame {
    private BlackjackPlayer player;
    private Dealer dealer;
    private Shoe shoe;
    private BlackjackController controller;
    public BlackjackGameView view;
    private boolean hideDealerHole;

    public BlackjackGame(BlackjackPlayer player, Dealer dealer, int numDecks) {
        this.player = player;
        this.dealer = dealer;
        this.shoe = new Shoe(numDecks);
        this.controller = new BlackjackController();
        this.view = new BlackjackGameView();
    }

    public void start() {
        Card p1 = shoe.dealCard();
        Card p2 = shoe.dealCard();
        Card d1 = shoe.dealCard();
        Card d2 = shoe.dealCard();
        player.setHand(new Hand(p1, p2));
        dealer.setHand(new Hand(d1, d2));
        hideDealerHole = true;
        view.showHands(player.getHand(), dealer.getHand(), hideDealerHole);

        int pScore = player.getHand().getScore();
        int dScore = dealer.getHand().getScore();
        if (pScore == 21 && dScore != 21) {
            player.payout(2.5);
            view.showResult("Blackjack! Player wins.", player.getBet() * 2.5);
        } else if (pScore == 21 && dScore == 21) {
            player.payout(1.0);
            view.showResult("Both blackjack: Push.", player.getBet());
        }
    }

    public void playAction(String action) {
        if (!controller.validateAction(action)) {
            System.out.println("Invalid action: " + action);
            return;
        }
        view.announcePlayerAction(action);
        if ("hit".equalsIgnoreCase(action)) {
            hit();
        } else if ("stand".equalsIgnoreCase(action)) {
            stand();
        }
    }

    private void hit() {
        Card c = shoe.dealCard();
        player.getHand().addCard(c);
        view.showHands(player.getHand(), dealer.getHand(), true);
        int score = player.getHand().getScore();
        if (score > 21) {
            view.showResult("Player busts.", 0);
        }
    }

    private void stand() {
        hideDealerHole = false;
        view.showHands(player.getHand(), dealer.getHand(), hideDealerHole);
        while (dealer.getHand().getScore() < 17) {
            Card c = shoe.dealCard();
            dealer.getHand().addCard(c);
            view.showHands(player.getHand(), dealer.getHand(), false);
        }
        compareAndSettle();
    }

    private void compareAndSettle() {
        int pScore = player.getHand().getScore();
        int dScore = dealer.getHand().getScore();
        double bet = player.getBet();
        if (pScore > 21) {
            view.showResult("Player busted. Dealer wins.", 0);
        } else if (dScore > 21) {
            player.payout(2.0);
            view.showResult("Dealer busted. Player wins.", bet * 2.0);
        } else if (pScore > dScore) {
            player.payout(2.0);
            view.showResult("Player wins.", bet * 2.0);
        } else if (pScore < dScore) {
            view.showResult("Dealer wins.", 0);
        } else {
            player.payout(1.0);
            view.showResult("Push (tie).", bet);
        }
    }
}

public class Driver {
    public static void main(String[] args) {
        Person p = new Person("Alice");
        BlackjackPlayer player = new BlackjackPlayer("player1", "pwd", 1000.0, p);
        Dealer dealer = new Dealer("dealer", "pwd", 0.0, new Person("House"));
        BlackjackGame game = new BlackjackGame(player, dealer, 4);

        System.out.println("\n--- Scenario 1: Player gets Blackjack (if luck allows) ---");
        System.out.println("Initial balance $" + player.getBalance());
        try {
            player.placeBet(100);
            game.view.announceBet(100, player.getBalance());
            game.start();
            game.playAction("stand");
        } catch (IllegalArgumentException ex) { System.out.println(ex.getMessage()); }

        System.out.println("\n--- Scenario 2: Player bust ---");
        try {
            player.placeBet(50);
            game.view.announceBet(50, player.getBalance());
            game.start();
            game.playAction("hit");
            game.playAction("hit");
        } catch (IllegalArgumentException ex) { System.out.println(ex.getMessage()); }

        System.out.println("\n--- Scenario 3: Dealer busts ---");
        try {
            player.placeBet(75);
            game.view.announceBet(75, player.getBalance());
            game.start();
            game.playAction("stand");
        } catch (IllegalArgumentException ex) { System.out.println(ex.getMessage()); }

        System.out.println("\n--- Scenario 4: Push (tie) ---");
        try {
            player.placeBet(100);
            game.view.announceBet(100, player.getBalance());
            game.start();
            game.playAction("stand");
        } catch (IllegalArgumentException ex) { System.out.println(ex.getMessage()); }

        System.out.println("\n--- Scenario 5: Player stands and wins ---");
        try {
            player.placeBet(60);
            game.view.announceBet(60, player.getBalance());
            game.start();
            game.playAction("stand");
        } catch (IllegalArgumentException ex) { System.out.println(ex.getMessage()); }

        System.out.println("\n--- Scenario 6: Forfeit ---");
        try {
            player.placeBet(80);
            game.view.announceBet(80, player.getBalance());
            game.view.announceForfeit(player.getBalance());
            player.payout(-1);
        } catch (IllegalArgumentException ex) { System.out.println(ex.getMessage()); }

        System.out.println("\nFinal balance: $" + player.getBalance());
    }
}
