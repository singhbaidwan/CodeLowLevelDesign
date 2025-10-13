package Chess_Easy;


import java.util.*;

// ===== ENUMS =====
 enum GameStatus {
    ACTIVE,
    WHITE_WIN,
    BLACK_WIN,
    DRAW,
    STALEMATE,
    RESIGNATION,
    FORFEIT
}

 enum MoveType {
    NORMAL,
    CASTLING,
    EN_PASSANT,
    PROMOTION
}

enum AccountStatus {
    ACTIVE,
    CLOSED,
    CANCELED,
    BLACKLISTED,
    NONE
}

// ===== BASIC ENTITIES =====
class Box {
    private int x, y;
    private Piece piece;

    public Box(int x, int y, Piece piece) {
        this.x = x;
        this.y = y;
        this.piece = piece;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public Piece getPiece() { return piece; }
    public void setPiece(Piece piece) { this.piece = piece; }
}

class Chessboard {
    private Box[][] boxes = new Box[8][8];

    public Chessboard() {
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 8; j++)
                boxes[i][j] = new Box(i, j, null);
    }

    public void resetBoard() {
        // clear board
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 8; j++)
                boxes[i][j].setPiece(null);

        // Place pawns
        for (int j = 0; j < 8; j++) {
            boxes[1][j].setPiece(new Pawn(true));
            boxes[6][j].setPiece(new Pawn(false));
        }

        // Place other pieces
        Piece[] whitePieces = {
                new Rook(true), new Knight(true), new Bishop(true),
                new Queen(true), new King(true), new Bishop(true),
                new Knight(true), new Rook(true)
        };
        Piece[] blackPieces = {
                new Rook(false), new Knight(false), new Bishop(false),
                new Queen(false), new King(false), new Bishop(false),
                new Knight(false), new Rook(false)
        };

        for (int j = 0; j < 8; j++) {
            boxes[0][j].setPiece(whitePieces[j]);
            boxes[7][j].setPiece(blackPieces[j]);
        }
    }

    public Box getBox(int x, int y) { return boxes[x][y]; }
    public Box[][] getBoxes() { return boxes; }
}

// ===== PIECE CLASSES =====
abstract class Piece {
    private boolean white;
    private boolean killed = false;

    public Piece(boolean white) {
        this.white = white;
    }

    public boolean isWhite() { return white; }
    public boolean isKilled() { return killed; }
    public void setKilled(boolean killed) { this.killed = killed; }

    public abstract String getSymbol();
}

class King extends Piece {
    public King(boolean white) { super(white); }
    public String getSymbol() { return isWhite() ? "K" : "k"; }
}

class Queen extends Piece {
    public Queen(boolean white) { super(white); }
    public String getSymbol() { return isWhite() ? "Q" : "q"; }
}

class Rook extends Piece {
    public Rook(boolean white) { super(white); }
    public String getSymbol() { return isWhite() ? "R" : "r"; }
}

class Bishop extends Piece {
    public Bishop(boolean white) { super(white); }
    public String getSymbol() { return isWhite() ? "B" : "b"; }
}

class Knight extends Piece {
    public Knight(boolean white) { super(white); }
    public String getSymbol() { return isWhite() ? "N" : "n"; }
}

class Pawn extends Piece {
    public Pawn(boolean white) { super(white); }
    public String getSymbol() { return isWhite() ? "P" : "p"; }
}

// ===== MOVE AND PLAYER =====
class Move {
    private Box start;
    private Box end;
    private Piece pieceMoved;
    private Piece pieceKilled;
    private Player player;
    private MoveType moveType;

    public Move(Box start, Box end, Piece pieceMoved, Piece pieceKilled, Player player, MoveType moveType) {
        this.start = start;
        this.end = end;
        this.pieceMoved = pieceMoved;
        this.pieceKilled = pieceKilled;
        this.player = player;
        this.moveType = moveType;
    }

    public String toString() {
        return player.getName() + " moved " + pieceMoved.getSymbol() +
                " from (" + (char)('a' + start.getY()) + (start.getX() + 1) + ") to (" +
                (char)('a' + end.getY()) + (end.getX() + 1) + ")";
    }
}

class Player {
    private String name;
    private boolean whiteSide;

    public Player(String name, boolean whiteSide) {
        this.name = name;
        this.whiteSide = whiteSide;
    }

    public String getName() { return name; }
    public boolean isWhiteSide() { return whiteSide; }

    @Override
    public String toString() {
        return name + (whiteSide ? " (White)" : " (Black)");
    }
}

// ===== CONTROLLER & VIEW =====
class ChessMoveController {
    public boolean validateMove(Piece piece, Chessboard board, Box start, Box end) {
        // Basic validation (no same-color capture)
        if (piece == null) return false;
        if (end.getPiece() != null && end.getPiece().isWhite() == piece.isWhite()) {
            return false;
        }
        // (You can later extend this with actual chess rules)
        return true;
    }
}

class ChessGameView {
    public void showBoard(Chessboard board) {
        System.out.println("\nCurrent Board:");
        Box[][] boxes = board.getBoxes();
        for (int i = 7; i >= 0; i--) {
            System.out.print((i + 1) + " ");
            for (int j = 0; j < 8; j++) {
                Piece p = boxes[i][j].getPiece();
                System.out.print((p != null ? p.getSymbol() : ".") + " ");
            }
            System.out.println();
        }
        System.out.println("  a b c d e f g h\n");
    }
}

// ===== MAIN GAME CLASS =====
class ChessGame {
    private static int gameCounter = 1;
    private String gameId;
    private Player[] players;
    private Chessboard board;
    private Player currentTurn;
    private GameStatus status;
    private List<Move> moveHistory;
    private ChessMoveController controller;
    private ChessGameView view;

    public ChessGame(Player white, Player black) {
        this.gameId = "GAME-" + (gameCounter++);
        this.players = new Player[] { white, black };
        this.board = new Chessboard();
        this.currentTurn = white;
        this.status = GameStatus.ACTIVE;
        this.moveHistory = new ArrayList<>();
        this.controller = new ChessMoveController();
        this.view = new ChessGameView();
    }

    public String getGameId() { return gameId; }
    public Player[] getPlayers() { return players; }
    public Chessboard getBoard() { return board; }
    public Player getCurrentTurn() { return currentTurn; }
    public GameStatus getStatus() { return status; }
    public List<Move> getMoveHistory() { return moveHistory; }
    public ChessMoveController getController() { return controller; }
    public ChessGameView getView() { return view; }

    public void setCurrentTurn(Player player) { this.currentTurn = player; }
    public void setStatus(GameStatus status) { this.status = status; }
}

// ===== DRIVER =====
public class Driver {
    public static void main(String[] args) {
        Player white = new Player("Alice", true);
        Player black = new Player("Bob", false);
        ChessGame game = new ChessGame(white, black);

        System.out.println("Game ID: " + game.getGameId());
        game.getBoard().resetBoard();
        game.getView().showBoard(game.getBoard());

        // Example moves (no validation of legality yet)
        playMove(game, white, "e2", "e4");
        playMove(game, black, "e7", "e5");
        playMove(game, white, "d1", "h5");
        playMove(game, black, "b8", "c6");
        playMove(game, white, "f1", "c4");
        playMove(game, black, "g8", "f6");
        playMove(game, white, "h5", "f7"); // checkmate-like move

        // End game
        game.setStatus(GameStatus.WHITE_WIN);
        System.out.println("\nBob (Black) resigns! Alice (White) wins!");

        System.out.println("\nMove log:");
        for (Move m : game.getMoveHistory()) {
            System.out.println("  " + m);
        }
    }

    static void playMove(ChessGame game, Player player, String from, String to) {
        int sx = from.charAt(1) - '1', sy = from.charAt(0) - 'a';
        int ex = to.charAt(1) - '1', ey = to.charAt(0) - 'a';
        Box start = game.getBoard().getBox(sx, sy);
        Box end = game.getBoard().getBox(ex, ey);
        Piece piece = start.getPiece();

        if (piece == null) {
            System.out.println("\nNo piece at source (" + from + ") for " + player + "!");
            return;
        }
        if (piece.isWhite() != player.isWhiteSide()) {
            System.out.println("\nNot your piece (" + from + ") for " + player + "!");
            return;
        }
        if (!game.getController().validateMove(piece, game.getBoard(), start, end)) {
            System.out.println("\nInvalid move for " + piece.getSymbol() + " from " + from + " to " + to + "!");
            return;
        }

        Piece captured = end.getPiece();
        end.setPiece(piece);
        start.setPiece(null);

        Move move = new Move(start, end, piece, captured, player, MoveType.NORMAL);
        game.getMoveHistory().add(move);

        System.out.println("\n" + player + " moves " + piece.getSymbol() + " from " + from + " to " + to +
                (captured != null ? " capturing " + captured.getSymbol() : ""));
        game.getView().showBoard(game.getBoard());
    }
}

