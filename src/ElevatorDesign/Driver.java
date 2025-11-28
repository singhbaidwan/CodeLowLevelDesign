package ElevatorDesign;

// ElevatorSystemDemo.java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

// ----------------------------- Enums & Buttons ------------------------
 enum Direction { UP, DOWN, IDLE }

 enum ElevatorState { IDLE, UP, DOWN, MAINTENANCE }

 enum DoorState { OPEN, CLOSED }

abstract class Button {
    protected final AtomicBoolean pressed = new AtomicBoolean(false);
    public void pressDown() { pressed.set(true); }
    public void reset() { pressed.set(false); }
    public abstract boolean isPressed();
}

class DoorButton extends Button {
    @Override public boolean isPressed() { return pressed.get(); }
}

class HallButton extends Button {
    private final Direction direction;
    public HallButton(Direction dir) { this.direction = dir; }
    public Direction getDirection() { return direction; }
    @Override public boolean isPressed() { return pressed.get(); }
}

class ElevatorButton extends Button {
    private final int destinationFloor;
    public ElevatorButton(int floor) { this.destinationFloor = floor; }
    public int getDestinationFloor() { return destinationFloor; }
    @Override public boolean isPressed() { return pressed.get(); }
}

class EmergencyButton extends Button {
    public boolean getPressed() { return pressed.get(); }
    public void setPressed(boolean val) { pressed.set(val); }
    @Override public boolean isPressed() { return pressed.get(); }
}

// ----------------------------- Panels & Display -----------------------
class ElevatorPanel {
    private final List<ElevatorButton> floorButtons;
    private final DoorButton openButton = new DoorButton();
    private final DoorButton closeButton = new DoorButton();
    private final EmergencyButton emergencyButton = new EmergencyButton();

    public ElevatorPanel(int numFloors) {
        floorButtons = new ArrayList<>(numFloors);
        for (int i = 0; i < numFloors; i++) floorButtons.add(new ElevatorButton(i));
    }

    public List<ElevatorButton> getFloorButtons() { return floorButtons; }
    public DoorButton getOpenButton() { return openButton; }
    public DoorButton getCloseButton() { return closeButton; }
    public EmergencyButton getEmergencyButton() { return emergencyButton; }

    public void enterEmergency() { emergencyButton.setPressed(true); }
    public void exitEmergency() { emergencyButton.setPressed(false); }
}

class HallPanel {
    private final HallButton up;
    private final HallButton down;

    public HallPanel(int floorNumber, int topFloor) {
        up = (floorNumber < topFloor - 1) ? new HallButton(Direction.UP) : null;
        down = (floorNumber > 0) ? new HallButton(Direction.DOWN) : null;
    }
    public HallButton getUpButton() { return up; }
    public HallButton getDownButton() { return down; }
}

class Display {
    private int floor;
    private int load;
    private Direction direction;
    private ElevatorState state;
    private boolean maintenance;
    private boolean overloaded;

    public synchronized void update(int f, Direction d, int load, ElevatorState s, boolean overloaded, boolean maintenance) {
        this.floor = f;
        this.direction = d;
        this.load = load;
        this.state = s;
        this.overloaded = overloaded;
        this.maintenance = maintenance;
    }

    public synchronized void showElevatorDisplay(int carId) {
        System.out.printf("Display[Car %d] Floor:%d Dir:%s State:%s Load:%dkg Overloaded:%s Maintenance:%s%n",
                carId+1, floor, direction, state, load, overloaded, maintenance);
    }
}

// ----------------------------- Door ----------------------------------
class Door {
    private volatile DoorState state = DoorState.CLOSED;
    private final Lock lock = new ReentrantLock();

    public void open() {
        lock.lock();
        try {
            state = DoorState.OPEN;
        } finally { lock.unlock(); }
    }

    public void close() {
        lock.lock();
        try {
            state = DoorState.CLOSED;
        } finally { lock.unlock(); }
    }

    public boolean isOpen() { return state == DoorState.OPEN; }
    public DoorState getState() { return state; }
}

// ----------------------------- Floor ---------------------------------
class Floor {
    private final int floorNumber;
    private final List<HallPanel> panels;
    private final Display display;

    public Floor(int floorNumber, int numPanels, int numDisplaysPerFloor) {
        this.floorNumber = floorNumber;
        this.panels = new ArrayList<>(numPanels);
        for (int i = 0; i < numPanels; i++) panels.add(new HallPanel(floorNumber, numDisplaysPerFloor));
        this.display = new Display();
    }

    public int getFloorNumber() { return floorNumber; }
    public List<HallPanel> getPanels() { return panels; }
    public HallPanel getPanel(int index) { return panels.get(index); }
    public Display getDisplay() { return display; }
}

// ----------------------------- Elevator Car --------------------------
class ElevatorCar {
    private int id;
    private final AtomicInteger currentFloor = new AtomicInteger(0);
    private volatile ElevatorState state = ElevatorState.IDLE;
    private final Door door = new Door();
    private final Display display = new Display();
    private final ElevatorPanel panel;
    private final Queue<Integer> requestQueue = new ConcurrentLinkedQueue<>();
    private final Lock stateLock = new ReentrantLock();
    private final AtomicInteger loadKg = new AtomicInteger(0);
    private volatile boolean overloaded = false;
    private volatile boolean maintenance = false;

    // executor to run move tasks
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("ElevatorCar-" + id + "-thread");
        return t;
    });

    public ElevatorCar(int id, int numFloors) {
        this.id = id;
        this.panel = new ElevatorPanel(numFloors);
    }

    public int getId() { return id; }
    public int getCurrentFloor() { return currentFloor.get(); }
    public ElevatorState getState() { return state; }
    public ElevatorPanel getPanel() { return panel; }
    public boolean isInMaintenance() { return maintenance; }
    public boolean isOverloaded() { return overloaded; }
    public Display getDisplay() { return display; }
    public Door getDoor() { return door; }

    public void registerRequest(int floor) {
        if (maintenance) return;
        requestQueue.offer(floor);
    }

    // asynchronous move invocation, safe to call repeatedly
    public void move() {
        // If maintenance or overloaded, do not move
        if (maintenance) return;

        exec.submit(() -> {
            stateLock.lock();
            try {
                Integer target = requestQueue.poll();
                if (target == null) {
                    state = ElevatorState.IDLE;
                    updateDisplay();
                    return;
                }

                int start = currentFloor.get();
                if (target == start) {
                    // open door, wait, close
                    state = ElevatorState.IDLE;
                    door.open();
                    updateDisplay();
                    sleepMillis(300);
                    door.close();
                    updateDisplay();
                    // continue to next
                    move();
                    return;
                }

                state = (target > start) ? ElevatorState.UP : ElevatorState.DOWN;
                updateDisplay();
                while (currentFloor.get() != target) {
                    if (maintenance || overloaded) break; // abort moving
                    // simulate moving one floor at a time
                    if (state == ElevatorState.UP) currentFloor.incrementAndGet();
                    else if (state == ElevatorState.DOWN) currentFloor.decrementAndGet();
                    updateDisplay();
                    sleepMillis(200); // travel time per floor
                }

                // arrived
                door.open();
                updateDisplay();
                sleepMillis(300); // passenger exchange
                door.close();
                updateDisplay();

                // continue serving queued requests
                move();
            } finally {
                stateLock.unlock();
            }
        });
    }

    public void stop() {
        stateLock.lock();
        try {
            // set to idle, but don't clear requests
            state = ElevatorState.IDLE;
            updateDisplay();
        } finally { stateLock.unlock(); }
    }

    public void enterMaintenance() {
        stateLock.lock();
        try {
            maintenance = true;
            state = ElevatorState.MAINTENANCE;
            // clear pending requests for safety
            requestQueue.clear();
            door.open();
            updateDisplay();
        } finally { stateLock.unlock(); }
    }

    public void exitMaintenance() {
        stateLock.lock();
        try {
            maintenance = false;
            state = ElevatorState.IDLE;
            door.close();
            updateDisplay();
        } finally { stateLock.unlock(); }
    }

    public void emergencyStop() {
        stateLock.lock();
        try {
            maintenance = true; // treat emergency as a maintenance-like safe stop
            state = ElevatorState.MAINTENANCE;
            requestQueue.clear();
            door.open();
            updateDisplay();
        } finally { stateLock.unlock(); }
    }

    public void addLoad(int kg) {
        int val = loadKg.addAndGet(kg);
        overloaded = val > 1000; // arbitrary capacity threshold
        updateDisplay();
    }

    public void removeLoad(int kg) {
        int val = loadKg.addAndGet(-kg);
        if (val < 0) loadKg.set(0);
        overloaded = loadKg.get() > 1000;
        updateDisplay();
    }

    private void updateDisplay() {
        Direction dir = Direction.IDLE;
        if (state == ElevatorState.UP) dir = Direction.UP;
        if (state == ElevatorState.DOWN) dir = Direction.DOWN;
        display.update(currentFloor.get(), dir, loadKg.get(), state, overloaded, maintenance);
    }

    private void sleepMillis(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    public void shutdown() {
        exec.shutdownNow();
    }
}

// ----------------------------- Building & System ---------------------
class Building {
    private final List<Floor> floors;
    private final List<ElevatorCar> cars;

    public Building(int numFloors, int numCars, int numPanels, int numDisplaysPerFloor) {
        floors = new ArrayList<>(numFloors);
        for (int f = 0; f < numFloors; f++) floors.add(new Floor(f, numPanels, numFloors));
        cars = new ArrayList<>(numCars);
        for (int c = 0; c < numCars; c++) cars.add(new ElevatorCar(c, numFloors));
    }

    public List<Floor> getFloors() { return Collections.unmodifiableList(floors); }
    public List<ElevatorCar> getCars() { return Collections.unmodifiableList(cars); }
}

class ElevatorSystem {
    private static volatile ElevatorSystem system;
    private final Building building;
    private final int floors;
    private final int cars;
    private final BlockingQueue<HallCall> hallCallQueue = new LinkedBlockingQueue<>();
    private final ExecutorService dispatchExec = Executors.newSingleThreadExecutor();

    private ElevatorSystem(int floors, int cars, int numPanels, int numDisplaysPerFloor) {
        this.floors = floors;
        this.cars = cars;
        this.building = new Building(floors, cars, numPanels, numDisplaysPerFloor);
        // Start dispatcher thread
        dispatchExec.submit(this::dispatcherLoop);
    }

    public static ElevatorSystem getInstance(int floors, int cars, int numPanels, int numDisplaysPerFloor) {
        if (system == null) {
            synchronized (ElevatorSystem.class) {
                if (system == null) system = new ElevatorSystem(floors, cars, numPanels, numDisplaysPerFloor);
            }
        }
        return system;
    }

    public List<ElevatorCar> getCars() { return building.getCars(); }
    public Building getBuilding() { return building; }

    // external call (user presses hall button)
    public void callElevator(int floorNum, Direction dir) {
        if (floorNum < 0 || floorNum >= floors) return;
        HallCall call = new HallCall(floorNum, dir);
        hallCallQueue.offer(call);
        System.out.printf("Received hall call at floor %d (%s)%n", floorNum, dir);
    }

    // returns nearest idle car or null
    public ElevatorCar getNearestIdleCar(int floor) {
        ElevatorCar best = null;
        int minDist = Integer.MAX_VALUE;
        for (ElevatorCar car : building.getCars()) {
            if (car.isInMaintenance() || car.isOverloaded()) continue;
            if (car.getState() == ElevatorState.IDLE) {
                int dist = Math.abs(car.getCurrentFloor() - floor);
                if (dist < minDist) { minDist = dist; best = car; }
            }
        }
        return best;
    }

    // continuously processes hall calls and assigns them
    private void dispatcherLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                HallCall call = hallCallQueue.take(); // blocks
                // find best car
                ElevatorCar chosen = chooseCarForCall(call);
                if (chosen == null) {
                    // re-queue after short wait (no available car now)
                    Thread.sleep(200);
                    hallCallQueue.offer(call);
                    continue;
                }
                System.out.printf("Dispatcher: assigning floor %d (%s) to Elevator %d%n",
                        call.floor, call.dir, chosen.getId()+1);
                chosen.registerRequest(call.floor);
                chosen.move(); // trigger asynchronous move
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // simple allocation strategy: prefer idle nearest, otherwise car moving toward call
    private ElevatorCar chooseCarForCall(HallCall call) {
        ElevatorCar best = null;
        int bestScore = Integer.MAX_VALUE;
        for (ElevatorCar car : building.getCars()) {
            if (car.isInMaintenance() || car.isOverloaded()) continue;
            int dist = Math.abs(car.getCurrentFloor() - call.floor);
            int score = dist;
            // idle preferred
            if (car.getState() == ElevatorState.IDLE) score -= 5;
            // if car moving same direction and will pass floor, prefer it
            if ((car.getState() == ElevatorState.UP && call.dir == Direction.UP && car.getCurrentFloor() <= call.floor) ||
                    (car.getState() == ElevatorState.DOWN && call.dir == Direction.DOWN && car.getCurrentFloor() >= call.floor)) {
                score -= 3;
            }
            if (score < bestScore) { bestScore = score; best = car; }
        }
        return best;
    }

    // helper monitoring & dispatcher methods
    public void dispatcher() {
        // manually wake dispatcher by attempting to poll & re-offer all current queued calls
        // (Not strictly necessary because dispatcherLoop runs in background.)
        System.out.println("Dispatcher: manual trigger (no-op).");
    }

    public void monitoring() {
        System.out.println("=== System Monitoring ===");
        int idx = 0;
        for (ElevatorCar car : building.getCars()) {
            System.out.printf("Elevator %d ► Floor: %d | State: %s | Maintenance: %s | Overloaded: %s%n",
                    car.getId()+1, car.getCurrentFloor(), car.getState(), car.isInMaintenance(), car.isOverloaded());
            car.getDisplay().showElevatorDisplay(car.getId());
            idx++;
        }
        System.out.println("=========================");
    }

    // Graceful shutdown for demos
    public void shutdown() {
        dispatchExec.shutdownNow();
        for (ElevatorCar car : building.getCars()) car.shutdown();
    }

    // internal class to represent hall call
    private static class HallCall {
        final int floor;
        final Direction dir;
        HallCall(int f, Direction d) { this.floor = f; this.dir = d; }
    }
}

// ----------------------------- Demo Driver ---------------------------

public class Driver {
    public static void main(String[] args) {
        int numFloors = 13;
        int numCars = 3;
        int numPanels = 1;    // Number of HallPanels per floor
        int numDisplays = 3;  // Number of Displays per floor

        ElevatorSystem system = ElevatorSystem.getInstance(numFloors, numCars, numPanels, numDisplays);

        // SCENARIO 1
        System.out.println("=== Scenario 1: Elevator 3 in maintenance, passenger calls elevator from floor 7 ===\n");
        system.monitoring();
        System.out.println();

        ElevatorCar car3 = system.getCars().get(2);
        car3.enterMaintenance();
        System.out.println();
        system.monitoring();
        System.out.println();

        runCall(system, 7, Direction.UP);

        car3.exitMaintenance();
        System.out.println("\n--- Resetting maintenance for all elevators ---\n");
        system.monitoring();
        System.out.println();

        System.out.println("=== Scenario 2: Random positions, passenger calls elevator from ground (0) to top (12) ===");

        Random rand = new Random();
        for (ElevatorCar car : system.getCars()) {
            int randomFloor = rand.nextInt(numFloors);
            System.out.printf("\n== Setting random position for Elevator %d ==", car.getId()+1);
            System.out.printf("\n→ Teleporting Elevator %d to floor %d%n", car.getId()+1, randomFloor);
            car.registerRequest(randomFloor);
            car.move();
        }

        // let the cars move a bit
        sleepMillis(800);

        System.out.println("\nElevator positions after random repositioning:");
        for (ElevatorCar car : system.getCars()) {
            System.out.printf("Elevator %d ► Floor: %d | State: %s%n",
                    car.getId()+1, car.getCurrentFloor(), car.getState());
        }
        System.out.println();

        runCall(system, 0, Direction.UP);

        // allow some time for moves to complete in demo
        sleepMillis(2000);

        system.monitoring();

        // shutdown demo threads
        system.shutdown();
    }

    private static void runCall(ElevatorSystem system, int floor, Direction dir) {
        System.out.printf("Passenger calls lift on floor %d (%s)%n", floor, dir);
        ElevatorCar nearest = system.getNearestIdleCar(floor);
        if (nearest == null) {
            System.out.println("No idle elevator available right now. Dispatcher will queue the call.");
        } else {
            System.out.printf("→ Nearest idle elevator is %d at floor %d.%n",
                    nearest.getId()+1, nearest.getCurrentFloor());
        }
        system.callElevator(floor, dir);
        system.dispatcher();
        System.out.println("\n[Status after dispatch attempt]");
        system.monitoring();
        System.out.println(new String(new char[60]).replace('\0', '-'));
    }

    private static void sleepMillis(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}

