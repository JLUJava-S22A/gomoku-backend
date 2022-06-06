import java.util.List;

public class GameObject {
    transient String history;
    int nextMove = 1; // 1: first player (black), 0: second player (white)
    int chessboard[];
}
