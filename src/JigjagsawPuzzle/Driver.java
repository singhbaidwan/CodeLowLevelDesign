package JigjagsawPuzzle;

import java.util.*;

// ---------------- ENUM ----------------
enum Edge {
    INDENTATION, // inward piece
    EXTRUSION,   // outward piece
    FLAT         // border/flat piece
}

// ---------------- SIDE ----------------
class Side {
    private Edge edge;

    public Side(Edge edge) {
        this.edge = edge;
    }

    public Edge getEdge() {
        return edge;
    }

    public void setEdge(Edge edge) {
        this.edge = edge;
    }

    // checks if two sides match
    public boolean fitsWith(Side other) {
        if (this.edge == Edge.FLAT || other.edge == Edge.FLAT) {
            return false; // flat edges do not fit together
        }
        // indentation matches extrusion
        return (this.edge == Edge.INDENTATION && other.edge == Edge.EXTRUSION) ||
                (this.edge == Edge.EXTRUSION && other.edge == Edge.INDENTATION);
    }
}

// ---------------- PIECE ----------------
class Piece {
    // Sides in order: 0=top, 1=right, 2=bottom, 3=left
    private List<Side> sides = new ArrayList<>(4);

    public Piece(Side top, Side right, Side bottom, Side left) {
        sides.add(top);
        sides.add(right);
        sides.add(bottom);
        sides.add(left);
    }

    public List<Side> getSides() {
        return sides;
    }

    public Side getTop() { return sides.get(0); }
    public Side getRight() { return sides.get(1); }
    public Side getBottom() { return sides.get(2); }
    public Side getLeft() { return sides.get(3); }

    // Corner piece: has exactly 2 flat sides
    public boolean checkCorner() {
        long flats = sides.stream().filter(s -> s.getEdge() == Edge.FLAT).count();
        return flats == 2;
    }

    // Edge piece: has exactly 1 flat side
    public boolean checkEdge() {
        long flats = sides.stream().filter(s -> s.getEdge() == Edge.FLAT).count();
        return flats == 1;
    }

    // Middle piece: has no flat sides
    public boolean checkMiddle() {
        return sides.stream().noneMatch(s -> s.getEdge() == Edge.FLAT);
    }

    @Override
    public String toString() {
        return "Piece{" +
                "top=" + getTop().getEdge() +
                ", right=" + getRight().getEdge() +
                ", bottom=" + getBottom().getEdge() +
                ", left=" + getLeft().getEdge() +
                '}';
    }
}

// ---------------- PUZZLE (Singleton) ----------------
class Puzzle {
    private List<List<Piece>> board;
    private List<Piece> free; // free pieces not yet placed

    private static volatile Puzzle instance = null;

    private Puzzle() {
        this.board = new ArrayList<>();
        this.free = new ArrayList<>();
    }

    // Thread-safe singleton
    public static Puzzle getInstance() {
        if (instance == null) {
            synchronized (Puzzle.class) {
                if (instance == null) {
                    instance = new Puzzle();
                }
            }
        }
        return instance;
    }

    public List<List<Piece>> getBoard() {
        return board;
    }

    public void initializeBoard(int rows, int cols) {
        for (int i = 0; i < rows; i++) {
            List<Piece> row = new ArrayList<>(Collections.nCopies(cols, null));
            board.add(row);
        }
    }

    public void addFreePiece(Piece piece) {
        free.add(piece);
    }

    public List<Piece> getFreePieces() {
        return free;
    }

    public void insertPiece(Piece piece, int row, int column) {
        if (row >= board.size() || column >= board.get(0).size()) {
            throw new IllegalArgumentException("Position out of board range");
        }
        board.get(row).set(column, piece);
        free.remove(piece);
    }

    public void printBoard() {
        System.out.println("Current Puzzle Board:");
        for (List<Piece> row : board) {
            for (Piece p : row) {
                System.out.print((p != null ? "[P]" : "[ ]") + " ");
            }
            System.out.println();
        }
    }
}

// ---------------- PUZZLE SOLVER ----------------
class PuzzleSolver {

    public Puzzle matchPieces(Puzzle puzzle) {
        System.out.println("\n🧩 Starting puzzle solving...");
        List<Piece> freePieces = new ArrayList<>(puzzle.getFreePieces());
        List<List<Piece>> board = puzzle.getBoard();

        // Simple simulation: fill the board row by row
        for (int i = 0; i < board.size(); i++) {
            for (int j = 0; j < board.get(0).size(); j++) {
                if (freePieces.isEmpty()) break;

                Piece piece = freePieces.remove(0);
                puzzle.insertPiece(piece, i, j);
                System.out.println("Placed piece at (" + i + "," + j + "): " + piece);
            }
        }

        System.out.println("✅ Puzzle filled (simulation only).");
        return puzzle;
    }
}

// ---------------- DRIVER / DEMO ----------------
public class Driver {
    public static void main(String[] args) {

        // 1️⃣ Create Puzzle instance (Singleton)
        Puzzle puzzle = Puzzle.getInstance();
        puzzle.initializeBoard(2, 2); // 2x2 puzzle

        // 2️⃣ Create pieces
        Piece cornerPiece = new Piece(
                new Side(Edge.FLAT),
                new Side(Edge.EXTRUSION),
                new Side(Edge.FLAT),
                new Side(Edge.INDENTATION)
        );

        Piece edgePiece = new Piece(
                new Side(Edge.FLAT),
                new Side(Edge.EXTRUSION),
                new Side(Edge.INDENTATION),
                new Side(Edge.EXTRUSION)
        );

        Piece middlePiece = new Piece(
                new Side(Edge.EXTRUSION),
                new Side(Edge.INDENTATION),
                new Side(Edge.EXTRUSION),
                new Side(Edge.INDENTATION)
        );

        Piece corner2 = new Piece(
                new Side(Edge.FLAT),
                new Side(Edge.FLAT),
                new Side(Edge.INDENTATION),
                new Side(Edge.EXTRUSION)
        );

        // 3️⃣ Add pieces to free list
        puzzle.addFreePiece(cornerPiece);
        puzzle.addFreePiece(edgePiece);
        puzzle.addFreePiece(middlePiece);
        puzzle.addFreePiece(corner2);

        // 4️⃣ Solve puzzle
        PuzzleSolver solver = new PuzzleSolver();
        solver.matchPieces(puzzle);

        // 5️⃣ Print board
        puzzle.printBoard();

        // 6️⃣ Check piece types
        System.out.println("\nPiece Type Checks:");
        System.out.println("Corner piece? " + cornerPiece.checkCorner());
        System.out.println("Edge piece? " + edgePiece.checkEdge());
        System.out.println("Middle piece? " + middlePiece.checkMiddle());
    }
}

