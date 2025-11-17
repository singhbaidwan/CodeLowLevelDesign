package CricBuzz;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;

/*
 * Single-file runnable Java program: Cricinfo System Simulation
 * - Thread-safe implementations for contest operations
 * - Demo includes concurrent commentary and concurrent points updates
 *
 * Compile: javac Driver.java
 * Run:     java Driver
 */

/* ----------------------------- Enums & Address ---------------------------- */

class Address {
    private String streetAddress;
    private String city;
    private String state;
    private String country;
    private int zipCode;

    public Address() {}

    public Address(int zipCode, String streetAddress, String city, String state, String country) {
        this.zipCode = zipCode;
        this.streetAddress = streetAddress;
        this.city = city;
        this.state = state;
        this.country = country;
    }

    // getters & setters
    public void setStreetAddress(String s) { streetAddress = s; }
    public void setCity(String c) { city = c; }
    public void setState(String s) { state = s; }
    public void setCountry(String c) { country = c; }
    public void setZipCode(int z) { zipCode = z; }
    public String getCity() { return city; }
    public String getCountry() { return country; }

    @Override
    public String toString() {
        return String.format("%s, %s, %s - %d, %s", streetAddress, city, state, zipCode, country);
    }
}

enum MatchResult { LIVE, BAT_FIRST_WIN, FIELD_FIRST_WIN, DRAW, CANCELED }
enum UmpireType { FIELD, RESERVED, THIRD_UMPIRE }
enum WicketType { BOLD, CAUGHT, STUMPED, RUN_OUT, LBW, RETIRED_HURT, HIT_WICKET, OBSTRUCTING, HANDLING }
enum BallType { NORMAL, WIDE, NO_BALL, WICKET }
enum RunType { NORMAL, FOUR, SIX, LEG_BYE, BYE, NO_BALL, OVERTHROW }
enum PlayingPosition { BATTING, BOWLING, ALL_ROUNDER }
enum MatchType { T20, ODI, TEST }

/* ----------------------------- Basic Entities ----------------------------- */

class Player {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private final int id;
    private String name;
    private int age;
    private int countryCode;

    public Player() { this.id = NEXT_ID.getAndIncrement(); }
    public Player(String name, int age, int country) { this(); this.name = name; this.age = age; this.countryCode = country; }

    public String getName() { return name; }
    public void setName(String n) { name = n; }
    public void setAge(int a) { age = a; }
    public void setCountry(int c) { countryCode = c; }
    public int getId() { return id; }

    @Override
    public String toString() { return name + " (id:" + id + ")"; }
}

class Coach {
    private String name;
    private int age;
    private int country;
    private final List<Team> teams = new CopyOnWriteArrayList<>();

    public Coach(String name, int age, int country) { this.name = name; this.age = age; this.country = country; }

    public void addTeam(Team t) { teams.add(t); }
}

class Umpire {
    private static final AtomicInteger NEXT = new AtomicInteger(1);
    private final int id;
    private String name;
    private int age;
    private int country;

    public Umpire() { id = NEXT.getAndIncrement(); }
    public Umpire(String name, int age, int country) { this(); this.name = name; this.age = age; this.country = country; }

    public String getName() { return name; }
    public void setName(String n) { name = n; }
    public void setAge(int a) { age = a; }
    public void setCountry(int c) { country = c; }

    public boolean assignMatch(Match match) {
        // simple assignment; match will handle thread-safety
        return match.assignUmpire(this);
    }
}

/* ----------------------------- Events: Run / Wicket / Ball ---------------- */

class Run {
    private int totalRuns;
    private RunType type;
    private Player scoredBy;

    public Run() {}
    public void setTotalRuns(int r) { totalRuns = r; }
    public void setType(RunType t) { type = t; }
    public void setScoredBy(Player p) { scoredBy = p; }
    public int getTotalRuns() { return totalRuns; }
    public RunType getType() { return type; }
    public Player getScoredBy() { return scoredBy; }
}

class Wicket {
    private WicketType type;
    private Player playerOut;
    private Player balledBy;
    private Player caughtBy;
    private Player runoutBy;
    private Player stumpedBy;

    public void setType(WicketType t) { type = t; }
    public void setPlayerOut(Player p) { playerOut = p; }
    public void setBalledBy(Player p) { balledBy = p; }
    public void setCaughtBy(Player p) { caughtBy = p; }
}

class Commentary {
    private String text;
    private Date createdAt;
    private Commentator commentator;

    public Commentary() { createdAt = new Date(); }
    public void setText(String t) { text = t; }
    public void setCommentator(Commentator c) { commentator = c; }
    public String getText() { return text; }
    public Commentator getCommentator() { return commentator; }
    public Date getCreatedAt() { return createdAt; }
}

/* Ball is mutable and may receive commentary concurrently */
class Ball {
    private Player balledBy;
    private Player playedBy;
    private BallType type;
    private final List<Run> runs = new ArrayList<>();
    private Wicket wicket;
    private final List<Commentary> commentaries = new ArrayList<>();
    private final Lock lock = new ReentrantLock();

    public Ball() {}

    public void setBalledBy(Player p) { balledBy = p; }
    public void setPlayedBy(Player p) { playedBy = p; }
    public void setType(BallType t) { type = t; }
    public void setRuns(List<Run> rs) {
        lock.lock();
        try {
            runs.clear();
            if (rs != null) runs.addAll(rs);
        } finally { lock.unlock(); }
    }
    public void setWicket(Wicket w) { wicket = w; }

    public boolean addCommentary(Commentary commentary) {
        if (commentary == null) return false;
        lock.lock();
        try {
            commentaries.add(commentary);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public List<Commentary> getCommentaries() {
        lock.lock();
        try {
            return new ArrayList<>(commentaries);
        } finally { lock.unlock(); }
    }

    public Player getBalledBy() { return balledBy; }
    public Player getPlayedBy() { return playedBy; }
    public BallType getType() { return type; }
    public List<Run> getRuns() {
        lock.lock();
        try { return new ArrayList<>(runs); } finally { lock.unlock(); }
    }
}

/* Over composes multiple balls; supports concurrent ball additions */
class Over {
    private final int number;
    private Player bowler;
    private int totalScore;
    private final List<Ball> balls = new ArrayList<>();
    private final Lock lock = new ReentrantLock();

    public Over(int number) { this.number = number; }

    public boolean addBall(Ball ball) {
        if (ball == null) return false;
        lock.lock();
        try {
            balls.add(ball);
            for (Run r : ball.getRuns()) totalScore += r.getTotalRuns();
            return true;
        } finally { lock.unlock(); }
    }

    public int getTotalScore() { return totalScore; }
}

/* Innings composed of Overs, supports concurrent addition of overs */
class Innings {
    private Playing11 bowling;
    private Playing11 batting;
    private Date startTime;
    private Date endTime;
    private int totalScores;
    private int totalWickets;
    private final List<Over> overs = new ArrayList<>();
    private final Lock lock = new ReentrantLock();

    public Innings() {}

    public boolean addOver(Over over) {
        if (over == null) return false;
        lock.lock();
        try {
            overs.add(over);
            totalScores += over.getTotalScore();
            return true;
        } finally { lock.unlock(); }
    }

    public void setBatting(Playing11 p) { batting = p; }
}

/* ----------------------------- Teams & Squads ----------------------------- */

class Playing11 {
    private final List<Player> players = new CopyOnWriteArrayList<>();

    public boolean addPlayer(Player player) {
        if (player == null) return false;
        players.add(player);
        return true;
    }

    public List<Player> getPlayers() { return new ArrayList<>(players); }
}

class TournamentSquad {
    private final List<Player> players = new ArrayList<>();
    private Tournament tournament;
    private final List<TournamentStat> stats = new ArrayList<>();

    public boolean addPlayer(Player player) {
        if (player == null) return false;
        players.add(player);
        return true;
    }

    public void setPlayers(List<Player> p) {
        players.clear();
        if (p != null) players.addAll(p);
    }

    public List<Player> getPlayers() { return new ArrayList<>(players); }
}

class Team {
    private String name;
    private final List<Player> players = new CopyOnWriteArrayList<>();
    private Coach coach;
    private final List<News> news = new CopyOnWriteArrayList<>();
    private TeamStat stats;

    public Team() {}
    public Team(String name) { this.name = name; }

    public boolean addPlayer(Player p) {
        if (p == null) return false;
        players.add(p);
        return true;
    }

    public boolean addNews(News n) {
        if (n == null) return false;
        news.add(n);
        return true;
    }
}

/* ----------------------------- Stadium / Commentator / News -------------- */

class Stadium {
    private String name;
    private Address location;
    private int maxCapacity;

    public void setName(String n) { name = n; }
    public void setLocation(Address a) { location = a; }
    public void setMaxCapacity(int c) { maxCapacity = c; }
    public String getName() { return name; }

    public boolean assignMatch(Match match) {
        if (match == null) return false;
        return match.assignStadium(this);
    }
}

class Commentator {
    private String name;
    public void setName(String n) { name = n; }
    public String getName() { return name; }

    public boolean assignMatch(Match match) {
        if (match == null) return false;
        return match.assignCommentator(this);
    }
}

class News {
    private Date date;
    private String text;
    private byte[] image;
    private Team team;

    public void setDate(Date d) { date = d; }
    public void setText(String t) { text = t; }
    public void setImage(byte[] b) { image = b; }
    public void setTeam(Team t) { team = t; }
}

/* ----------------------------- Stats ------------------------------------- */

abstract class Stat {
    public abstract boolean updateStats();
}

class PlayerStat extends Stat {
    private int ranking;
    private int bestScore;
    private int bestWicketCount;
    private int totalMatchesPlayed;
    private int total100s;
    private int totalHattricks;

    @Override
    public boolean updateStats() { return true; }
}

class MatchStat extends Stat {
    private double winPercentage;
    private Player topBatsman;
    private Player topBowler;

    @Override
    public boolean updateStats() { return true; }
}

class TeamStat extends Stat {
    private int totalSixes;
    private int totalFours;
    private int totalReviews;

    @Override
    public boolean updateStats() { return true; }
}

class TournamentStat {
    // placeholder for tournament-level stats
}

/* ----------------------------- Points Table --------------------------------
   - Thread-safe: ConcurrentHashMap for team points; lock for atomic updates
---------------------------------------------------------------------------- */

class PointsTable {
    private final ConcurrentHashMap<String, Float> teamPoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MatchResult> matchResults = new ConcurrentHashMap<>();
    private Tournament tournament;
    private volatile Date lastUpdated;
    private final Lock lock = new ReentrantLock();

    public void setTournament(Tournament t) { tournament = t; }

    public Map<String, Float> getTeamPoints() { return teamPoints; }
    public Map<String, MatchResult> getMatchResults() { return matchResults; }

    public void setTeamPoints(Map<String, Float> copy) {
        teamPoints.clear();
        teamPoints.putAll(copy);
    }

    public void setMatchResults(Map<String, MatchResult> m) {
        matchResults.clear();
        matchResults.putAll(m);
    }

    public void updatePoints(String team, float delta) {
        lock.lock();
        try {
            teamPoints.merge(team, delta, Float::sum);
            lastUpdated = new Date();
        } finally { lock.unlock(); }
    }

    public void setLastUpdated(Date d) { lastUpdated = d; }
    public Date getLastUpdated() { return lastUpdated; }
}

/* ----------------------------- Match & Subclasses ------------------------- */

abstract class Match {
    protected Date startTime;
    protected MatchResult result = MatchResult.LIVE;
    protected int totalOvers;
    protected final List<Playing11> teams = new CopyOnWriteArrayList<>();
    protected final List<Innings> innings = new CopyOnWriteArrayList<>();
    protected Playing11 tossWin;
    protected final ConcurrentHashMap<Umpire, UmpireType> umpires = new ConcurrentHashMap<>();
    protected Stadium stadium;
    protected final List<Commentator> commentators = new CopyOnWriteArrayList<>();
    protected final List<MatchStat> stats = new CopyOnWriteArrayList<>();
    private final Lock lock = new ReentrantLock();

    public abstract boolean assignStadium(Stadium stadium);
//    public abstract boolean assignUmpire(Umpire umpire);

    public boolean addTeam(Playing11 team) {
        if (team == null) return false;
        teams.add(team);
        return true;
    }

    public boolean assignCommentator(Commentator commentator) {
        if (commentator == null) return false;
        commentators.add(commentator);
        return true;
    }

    public boolean setStartTime(Date d) { startTime = d; return true; }
    public Date getStartTime() { return startTime; }
    public void setTotalOvers(int o) { totalOvers = o; }
    public int getTotalOvers() { return totalOvers; }

    // default implementation
    public boolean assignUmpire(Umpire u, UmpireType type) {
        if (u == null) return false;
        umpires.put(u, type);
        return true;
    }

    // used by Admin/Umpire
    protected boolean addUmpireInternal(Umpire u) {
        return assignUmpire(u);
    }

    // wrapper for remote calls (thread-safe)
    public boolean assignUmpire(Umpire ump) {
        if (ump == null) return false;
        return assignUmpire(ump, UmpireType.FIELD);
    }

    public boolean assignStadiumInternal(Stadium s) {
        if (s == null) return false;
        lock.lock();
        try {
            stadium = s;
            return true;
        } finally {
            lock.unlock();
        }
    }
}

class T20 extends Match {
    @Override
    public boolean assignStadium(Stadium stadium) {
        return assignStadiumInternal(stadium);
    }

    @Override
    public boolean assignUmpire(Umpire umpire) {
        return assignUmpire(umpire, UmpireType.FIELD);
    }
}

class ODI extends Match {
    @Override
    public boolean assignStadium(Stadium stadium) { return assignStadiumInternal(stadium); }
    @Override
    public boolean assignUmpire(Umpire umpire) { return assignUmpire(umpire, UmpireType.FIELD); }
}

class TestMatch extends Match {
    @Override
    public boolean assignStadium(Stadium stadium) { return assignStadiumInternal(stadium); }
    @Override
    public boolean assignUmpire(Umpire umpire) { return assignUmpire(umpire, UmpireType.FIELD); }
}

/* ----------------------------- Tournament -------------------------------- */

class Tournament {
    private Date startDate;
    private final List<TournamentSquad> teams = new CopyOnWriteArrayList<>();
    private final List<Match> matches = new CopyOnWriteArrayList<>();
    private PointsTable points;

    public void setStartDate(Date d) { startDate = d; }
    public Date getStartDate() { return startDate; }

    public void setPoints(PointsTable p) { points = p; p.setTournament(this); }
    public PointsTable getPoints() { return points; }

    public void setTeams(List<TournamentSquad> t) {
        teams.clear();
        if (t != null) teams.addAll(t);
    }

    public void setMatches(List<Match> m) {
        matches.clear();
        if (m != null) matches.addAll(m);
    }

    public boolean addTeam(TournamentSquad team) {
        if (team == null) return false;
        teams.add(team);
        return true;
    }

    public boolean addMatch(Match match) {
        if (match == null) return false;
        matches.add(match);
        return true;
    }
}

/* ----------------------------- Admin ------------------------------------- */

class Admin {
    private final List<Player> players = new CopyOnWriteArrayList<>();
    private final List<Team> teams = new CopyOnWriteArrayList<>();
    private final List<Match> matches = new CopyOnWriteArrayList<>();
    private final List<Tournament> tournaments = new CopyOnWriteArrayList<>();
    private final List<Stat> stats = new CopyOnWriteArrayList<>();
    private final List<News> newsList = new CopyOnWriteArrayList<>();
    private final Lock lock = new ReentrantLock();

    public boolean addPlayer(Player player) { if (player == null) return false; players.add(player); return true; }
    public boolean addTeam(Team team) { if (team == null) return false; teams.add(team); return true; }
    public boolean addMatch(Match match) { if (match == null) return false; matches.add(match); return true; }
    public boolean addTournament(Tournament tournament) { if (tournament == null) return false; tournaments.add(tournament); return true; }
    public boolean addStats(Stat stat) { if (stat == null) return false; stats.add(stat); return true; }
    public boolean addNews(News news) { if (news == null) return false; newsList.add(news); return true; }

    public boolean assignStadiumToMatch(Stadium stadium, Match match) {
        if (stadium == null || match == null) return false;
        return match.assignStadium(stadium);
    }

    public boolean assignUmpireToMatch(Umpire umpire, Match match) {
        if (umpire == null || match == null) return false;
        return match.assignUmpire(umpire);
    }

    public boolean assignCommentatorToMatch(Commentator commentator, Match match) {
        if (commentator == null || match == null) return false;
        return match.assignCommentator(commentator);
    }

    public Match createMatch(MatchType type) {
        Match m;
        switch (type) {
            case T20: m = new T20(); m.setTotalOvers(20); break;
            case ODI: m = new ODI(); m.setTotalOvers(50); break;
            case TEST: m = new TestMatch(); m.setTotalOvers(90); break;
            default: m = new T20(); m.setTotalOvers(20);
        }
        addMatch(m);
        return m;
    }
}

/* ----------------------------- Driver Demo -------------------------------- */

public class Driver {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("🏏 === Welcome to Thread-safe Cricinfo Simulation ===\n");

        // Initialize tournament & points table
        Tournament tournament = new Tournament();
        tournament.setStartDate(new Date());
        tournament.setMatches(new ArrayList<>());
        tournament.setTeams(new ArrayList<>());

        PointsTable pointsTable = new PointsTable();
        pointsTable.setTeamPoints(new HashMap<>()); // set initial map
        pointsTable.setMatchResults(new HashMap<>());
        pointsTable.setTournament(tournament);
        pointsTable.setLastUpdated(new Date());
        tournament.setPoints(pointsTable);

        Admin admin = new Admin();

        System.out.println("✅ Tournament created. Start date: " + tournament.getStartDate());

        // Create match
        System.out.println("\n🎯 Creating match (T20) and assigning stadium/umpire/commentator...");
        Match match = admin.createMatch(MatchType.T20);
        match.setStartTime(new Date());
        match.setTotalOvers(20);
        System.out.println("Match created (T20): Start: " + match.getStartTime());

        Stadium stadium = new Stadium();
        stadium.setName("Wankhede Stadium");
        Address address = new Address();
        address.setCity("Mumbai");
        address.setCountry("India");
        stadium.setLocation(address);

        admin.assignStadiumToMatch(stadium, match);
        System.out.println("Assigned stadium: " + stadium.getName() + ", " + address.getCity());

        Umpire umpire = new Umpire("Aleem Dar", 50, 92);
        admin.assignUmpireToMatch(umpire, match);
        System.out.println("Assigned umpire: " + umpire.getName());

        Commentator commentator = new Commentator();
        commentator.setName("Harsha Bhogle");
        admin.assignCommentatorToMatch(commentator, match);
        System.out.println("Assigned commentator: " + commentator.getName());

        tournament.addMatch(match);

        // Add players and teams
        Player p1 = new Player("Virat Kohli", 35, 91);
        Player p2 = new Player("Mitchell Starc", 34, 61);

        Playing11 team1 = new Playing11();
        team1.addPlayer(p1);

        Playing11 team2 = new Playing11();
        team2.addPlayer(p2);

        match.addTeam(team1);
        match.addTeam(team2);

        TournamentSquad squad1 = new TournamentSquad();
        squad1.setPlayers(List.of(p1));
        tournament.addTeam(squad1);

        TournamentSquad squad2 = new TournamentSquad();
        squad2.setPlayers(List.of(p2));
        tournament.addTeam(squad2);

        System.out.println("✅ Teams and players added.");

        // Simulate ball and concurrent commentary
        System.out.println("\n🏃 Simulating ball delivery and concurrent commentary...");
        Ball ball = new Ball();
        ball.setBalledBy(p2);
        ball.setPlayedBy(p1);
        ball.setType(BallType.NORMAL);

        Run run = new Run();
        run.setTotalRuns(4);
        run.setType(RunType.FOUR);
        run.setScoredBy(p1);
        ball.setRuns(List.of(run));

        Commentary c1 = new Commentary();
        c1.setText("Kohli punches it through the covers for a boundary!");
        c1.setCommentator(commentator);

        // create multiple commentators (threads) adding commentary concurrently
        Runnable commentaryTask = () -> {
            Commentary c = new Commentary();
            c.setText("Live reaction at " + Thread.currentThread().getName());
            c.setCommentator(commentator);
            boolean ok = ball.addCommentary(c);
            System.out.println(Thread.currentThread().getName() + " added commentary: " + ok);
        };

        int threads = 5;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) ex.submit(commentaryTask);

        // Add an explicit commentary too
        ball.addCommentary(c1);

        ex.shutdown();
        ex.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println("Ball commentaries count: " + ball.getCommentaries().size());
        for (Commentary cc : ball.getCommentaries()) {
            System.out.println(" - " + cc.getText() + " (by " + (cc.getCommentator()!=null?cc.getCommentator().getName():"unknown") + ")");
        }

        // Update points table concurrently to show thread-safety
        System.out.println("\n📊 Simulating concurrent points updates...");
        pointsTable.getTeamPoints().put("Team 1", 2.0f);
        pointsTable.getTeamPoints().put("Team 2", 1.0f);

        Runnable pointsUpdater = () -> {
            for (int i = 0; i < 5; i++) {
                pointsTable.updatePoints("Team 1", 1.0f);
                try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            }
        };

        Runnable pointsUpdater2 = () -> {
            for (int i = 0; i < 3; i++) {
                pointsTable.updatePoints("Team 2", 2.0f);
                try { Thread.sleep(15); } catch (InterruptedException ignored) {}
            }
        };

        ExecutorService ptsEx = Executors.newFixedThreadPool(2);
        ptsEx.submit(pointsUpdater);
        ptsEx.submit(pointsUpdater2);

        ptsEx.shutdown();
        ptsEx.awaitTermination(2, TimeUnit.SECONDS);

        System.out.println("Final Points Table:");
        for (Map.Entry<String, Float> e : pointsTable.getTeamPoints().entrySet()) {
            System.out.println(" - " + e.getKey() + ": " + e.getValue() + " pts");
        }
        System.out.println("Points Last Updated: " + pointsTable.getLastUpdated());

        System.out.println("\n🏁 Simulation complete.");
    }
}

