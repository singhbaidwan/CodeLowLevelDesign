import java.util.*;

/**
 * Simple low-level design (LLD) implementation of a Chess game in a single Java file.
 * - Supports pieces: King, Queen, Rook, Bishop, Knight, Pawn
 * - Basic move generation and validation (no en-passant, minimal castling not implemented)
 * - Check detection and checkmate detection (by exhaustion of legal moves)
 * - Move execution, undo (via Move object history), simple CLI driver for demonstration
 *
 * This is intended as an educational LLD; it focuses on clear structure rather than full FIDE completeness.
 */

enum Color { WHITE, BLACK }

enum PieceType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }

class Position {
    public final int r; // 0..7 (rank: 0 = 8th rank if you like, but we keep 0 as board[0])
    public final int c; // 0..7
    public Position(int r, int c) {
        this.r = r; this.c = c;
    }
    public boolean inBounds() { return r >= 0 && r < 8 && c >= 0 && c < 8; }
    @Override public boolean equals(Object o) {
        if (!(o instanceof Position)) return false; Position p = (Position)o; return p.r==r && p.c==c;
    }
    @Override public int hashCode() { return Objects.hash(r,c); }
    @Override public String toString() { return "("+r+","+c+")"; }
}

abstract class Piece {
    public final Color color;
    public final PieceType type;
    public boolean hasMoved = false; // useful for pawns and castling
    public Piece(Color color, PieceType type) { this.color = color; this.type = type; }
    public abstract List<Position> legalMoves(Position from, Board board);
    @Override public String toString(){ return (color==Color.WHITE?"W":"B")+""+type.name().charAt(0); }
}

class King extends Piece {
    King(Color c){ super(c, PieceType.KING); }
    public List<Position> legalMoves(Position from, Board board){
        List<Position> res = new ArrayList<>();
        int[] dr = {-1,-1,-1,0,0,1,1,1};
        int[] dc = {-1,0,1,-1,1,-1,0,1};
        for(int i=0;i<8;i++){ Position p = new Position(from.r+dr[i], from.c+dc[i]);
            if(!p.inBounds()) continue;
            Piece dest = board.get(p);
            if(dest==null || dest.color!=this.color) res.add(p);
        }
        return res;
    }
}

class Queen extends Piece {
    Queen(Color c){ super(c, PieceType.QUEEN); }
    public List<Position> legalMoves(Position from, Board board){
        List<Position> res = new ArrayList<>();
        res.addAll(Board.rayMoves(from, board, -1, 0, this.color));
        res.addAll(Board.rayMoves(from, board, 1, 0, this.color));
        res.addAll(Board.rayMoves(from, board, 0, -1, this.color));
        res.addAll(Board.rayMoves(from, board, 0, 1, this.color));
        res.addAll(Board.rayMoves(from, board, -1, -1, this.color));
        res.addAll(Board.rayMoves(from, board, -1, 1, this.color));
        res.addAll(Board.rayMoves(from, board, 1, -1, this.color));
        res.addAll(Board.rayMoves(from, board, 1, 1, this.color));
        return res;
    }
}

class Rook extends Piece {
    Rook(Color c){ super(c, PieceType.ROOK); }
    public List<Position> legalMoves(Position from, Board board){
        List<Position> res = new ArrayList<>();
        res.addAll(Board.rayMoves(from, board, -1, 0, this.color));
        res.addAll(Board.rayMoves(from, board, 1, 0, this.color));
        res.addAll(Board.rayMoves(from, board, 0, -1, this.color));
        res.addAll(Board.rayMoves(from, board, 0, 1, this.color));
        return res;
    }
}

class Bishop extends Piece {
    Bishop(Color c){ super(c, PieceType.BISHOP); }
    public List<Position> legalMoves(Position from, Board board){
        List<Position> res = new ArrayList<>();
        res.addAll(Board.rayMoves(from, board, -1, -1, this.color));
        res.addAll(Board.rayMoves(from, board, -1, 1, this.color));
        res.addAll(Board.rayMoves(from, board, 1, -1, this.color));
        res.addAll(Board.rayMoves(from, board, 1, 1, this.color));
        return res;
    }
}

class Knight extends Piece {
    Knight(Color c){ super(c, PieceType.KNIGHT); }
    public List<Position> legalMoves(Position from, Board board){
        List<Position> res = new ArrayList<>();
        int[] dr = {-2,-2,-1,-1,1,1,2,2};
        int[] dc = {-1,1,-2,2,-2,2,-1,1};
        for(int i=0;i<8;i++){ Position p = new Position(from.r+dr[i], from.c+dc[i]);
            if(!p.inBounds()) continue;
            Piece dest = board.get(p);
            if(dest==null || dest.color!=this.color) res.add(p);
        }
        return res;
    }
}

class Pawn extends Piece {
    Pawn(Color c){ super(c, PieceType.PAWN); }
    public List<Position> legalMoves(Position from, Board board){
        List<Position> res = new ArrayList<>();
        int dir = (this.color==Color.WHITE)? -1 : 1; // white moves up (r-1), black down (r+1)
        // forward 1
        Position one = new Position(from.r+dir, from.c);
        if(one.inBounds() && board.get(one)==null) res.add(one);
        // forward 2 if hasn't moved
        Position two = new Position(from.r+2*dir, from.c);
        if(!this.hasMoved && one.inBounds() && two.inBounds() && board.get(one)==null && board.get(two)==null) res.add(two);
        // captures
        Position capL = new Position(from.r+dir, from.c-1);
        Position capR = new Position(from.r+dir, from.c+1);
        if(capL.inBounds()){ Piece p=board.get(capL); if(p!=null && p.color!=this.color) res.add(capL); }
        if(capR.inBounds()){ Piece p=board.get(capR); if(p!=null && p.color!=this.color) res.add(capR); }
        // Note: en-passant and promotions are not implemented fully here (promotion can be added at move execution)
        return res;
    }
}

class Board {
    private final Piece[][] board = new Piece[8][8];

    public Board() {}

    public Piece get(Position p){ return board[p.r][p.c]; }
    public void set(Position p, Piece piece){ board[p.r][p.c] = piece; }

    public Position findKing(Color color){
        for(int r=0;r<8;r++) for(int c=0;c<8;c++){ Piece p=board[r][c]; if(p instanceof King && p.color==color) return new Position(r,c); }
        return null;
    }

    public static List<Position> rayMoves(Position from, Board board, int dr, int dc, Color color){
        List<Position> res = new ArrayList<>();
        int r = from.r + dr, c = from.c + dc;
        while(r>=0 && r<8 && c>=0 && c<8){ Piece p = board.get(new Position(r,c));
            if(p==null){ res.add(new Position(r,c)); }
            else { if(p.color!=color) res.add(new Position(r,c)); break; }
            r+=dr; c+=dc;
        }
        return res;
    }

    public Board copy(){ Board nb = new Board(); for(int r=0;r<8;r++) for(int c=0;c<8;c++) nb.board[r][c]=this.board[r][c]; return nb; }

    public void prettyPrint(){ System.out.println("  +--------------------------------+\n  | a b c d e f g h |");
        for(int r=0;r<8;r++){
            System.out.printf("%d |", 8-r);
            for(int c=0;c<8;c++){
                Piece p = board[r][c];
                if(p==null) System.out.print(" ."); else System.out.print(" "+pToChar(p));
            }
            System.out.printf(" |\n");
        }
        System.out.println("  +--------------------------------+");
    }
    private String pToChar(Piece p){
        String s="?"; switch(p.type){ case KING: s="K"; break; case QUEEN: s="Q"; break; case ROOK: s="R"; break; case BISHOP: s="B"; break; case KNIGHT: s="N"; break; case PAWN: s="P"; break; }
        return (p.color==Color.WHITE?"w":"b")+s;
    }
}

class Move {
    public final Position from; public final Position to; public final Piece moved; public final Piece captured; public final boolean wasFirstMove;
    public Move(Position from, Position to, Piece moved, Piece captured, boolean wasFirstMove){ this.from=from; this.to=to; this.moved=moved; this.captured=captured; this.wasFirstMove=wasFirstMove; }
}

class Game {
    public final Board board = new Board();
    public Color turn = Color.WHITE;
    public final Deque<Move> history = new ArrayDeque<>();

    public Game(){ setupStartPosition(); }

    public void setupStartPosition(){
        // clear
        for(int r=0;r<8;r++) for(int c=0;c<8;c++) board.set(new Position(r,c), null);
        // pawns
        for(int c=0;c<8;c++){ board.set(new Position(1,c), new Pawn(Color.BLACK)); board.set(new Position(6,c), new Pawn(Color.WHITE)); }
        // rooks
        board.set(new Position(0,0), new Rook(Color.BLACK)); board.set(new Position(0,7), new Rook(Color.BLACK));
        board.set(new Position(7,0), new Rook(Color.WHITE)); board.set(new Position(7,7), new Rook(Color.WHITE));
        // knights
        board.set(new Position(0,1), new Knight(Color.BLACK)); board.set(new Position(0,6), new Knight(Color.BLACK));
        board.set(new Position(7,1), new Knight(Color.WHITE)); board.set(new Position(7,6), new Knight(Color.WHITE));
        // bishops
        board.set(new Position(0,2), new Bishop(Color.BLACK)); board.set(new Position(0,5), new Bishop(Color.BLACK));
        board.set(new Position(7,2), new Bishop(Color.WHITE)); board.set(new Position(7,5), new Bishop(Color.WHITE));
        // queens
        board.set(new Position(0,3), new Queen(Color.BLACK)); board.set(new Position(7,3), new Queen(Color.WHITE));
        // kings
        board.set(new Position(0,4), new King(Color.BLACK)); board.set(new Position(7,4), new King(Color.WHITE));
    }

    // generate all pseudo-legal moves for color (not filtering checks)
    public Map<Position, List<Position>> pseudoLegalMoves(Color color){
        Map<Position, List<Position>> map = new HashMap<>();
        for(int r=0;r<8;r++) for(int c=0;c<8;c++){ Position p = new Position(r,c); Piece pc = board.get(p); if(pc!=null && pc.color==color){ map.put(p, pc.legalMoves(p, board)); } }
        return map;
    }

    // generate legal moves (filtering out moves that leave king in check)
    public Map<Position, List<Position>> legalMoves(Color color){
        Map<Position, List<Position>> pseudo = pseudoLegalMoves(color);
        Map<Position, List<Position>> legal = new HashMap<>();
        for(Map.Entry<Position,List<Position>> e: pseudo.entrySet()){
            Position from = e.getKey();
            List<Position> val = new ArrayList<>();
            for(Position to : e.getValue()){
                if(isLegalMove(from,to,color)) val.add(to);
            }
            if(!val.isEmpty()) legal.put(from,val);
        }
        return legal;
    }

    public boolean isLegalMove(Position from, Position to, Color color){
        Piece p = board.get(from);
        if(p==null || p.color!=color) return false;
        // perform move on copy
        Board copy = board.copy();
        Piece captured = copy.get(to);
        copy.set(to, p);
        copy.set(from, null);
        // find king position for color
        Position kingPos = copy.findKing(color);
        if(kingPos==null) return false; // should not happen
        // is king attacked?
        return !isSquareAttacked(copy, kingPos, opposite(color));
    }

    public boolean makeMove(Position from, Position to){
        Piece p = board.get(from);
        if(p==null || p.color!=turn) return false;
        if(!p.legalMoves(from, board).contains(to)) return false; // basic
        if(!isLegalMove(from,to,turn)) return false;
        Piece captured = board.get(to);
        boolean wasFirst = !p.hasMoved;
        board.set(to, p);
        board.set(from, null);
        p.hasMoved = true;
        history.push(new Move(from,to,p,captured,wasFirst));
        turn = opposite(turn);
        return true;
    }

    public boolean undo(){ if(history.isEmpty()) return false; Move m = history.pop(); board.set(m.from, m.moved); board.set(m.to, m.captured); m.moved.hasMoved = !m.wasFirstMove; turn = opposite(turn); return true; }

    public static Color opposite(Color c){ return c==Color.WHITE?Color.BLACK:Color.WHITE; }

    // check detection
    public boolean inCheck(Color color){ Position king = board.findKing(color); if(king==null) return false; return isSquareAttacked(board, king, opposite(color)); }

    // naive checkmate: in check and no legal moves
    public boolean isCheckmate(Color color){ if(!inCheck(color)) return false; Map<Position,List<Position>> moves = legalMoves(color); return moves.isEmpty(); }

    // is a square attacked by attackerColor
    public static boolean isSquareAttacked(Board board, Position square, Color attackerColor){
        // iterate all pieces of attackerColor and see if any pseudoLegal move reaches square
        for(int r=0;r<8;r++) for(int c=0;c<8;c++){ Position p = new Position(r,c); Piece pc = board.get(p); if(pc!=null && pc.color==attackerColor){ List<Position> attacks = pc.legalMoves(p, board); for(Position targ : attacks) if(targ.equals(square)) return true; } }
        return false;
    }

    // simple display
    public void printBoard(){ board.prettyPrint(); System.out.println("Turn: "+turn+ (inCheck(turn)?" (in CHECK)":"")); }
}

// Simple CLI demo driver
public class ChessDemo {
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        Game g = new Game();
        System.out.println("Simple Chess LLD Demo. Enter moves like 'e2 e4' or 'exit'");
        g.printBoard();
        while(true){ System.out.print("> "); String line = sc.nextLine().trim(); if(line.equalsIgnoreCase("exit")) break; if(line.isEmpty()) continue;
            String[] parts = line.split("\\s+"); if(parts.length<2){ System.out.println("Enter two squares: from to"); continue; }
            Position from = algebraicToPos(parts[0]); Position to = algebraicToPos(parts[1]); if(from==null||to==null){ System.out.println("Invalid squares. Use a1..h8"); continue; }
            boolean ok = g.makeMove(from,to);
            if(!ok) System.out.println("Illegal move");
            g.printBoard();
            if(g.isCheckmate(g.turn)){ System.out.println(g.turn+" is checkmated. Game over."); break; }
        }
        sc.close();
    }

    public static Position algebraicToPos(String alg){ if(alg.length()!=2) return null; char f=alg.charAt(0); char r=alg.charAt(1); int c = f - 'a'; int row = 8 - (r - '0'); if(c<0||c>7||row<0||row>7) return null; return new Position(row,c); }
}
