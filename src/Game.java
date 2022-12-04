import java.util.Arrays;

import static javax.swing.SwingUtilities.invokeLater;


public class Game {
    public static void main(String[] args) {
        var ex = new GameWindow();
        invokeLater(() -> ex.setVisible(true));
    }

    enum Opponent {PLAYER, BEGINNER, PROFESSIONAL}
    enum CellColor {COLORLESS, WHITE, BLACK}

    private static final Position[] directions = {
            new Position(0, 1),
            new Position(1, 1),
            new Position(1, 0),
            new Position(1, -1),
            new Position(0, -1),
            new Position(-1, -1),
            new Position(-1, 0),
            new Position(-1, 1),
    };

    // contains field and boundary of colorless cells (1 cell on each side)
    // field in Game and field in GameWindow work independently
    // but both describe common entity (field) and must be synchronised
    private static final CellColor[][] field = new CellColor[10][10];
    // there can't be more than half possible moves or changed cells, at least I think so
    public static Position[] possibleMoves = new Position[40];
    public static Position[] changedCells = new Position[40];

    // players that can make a move (first is white)
    public static boolean[] canMove = {true, true};

    public static final Position[][] previousMoves = new Position[64][40];
    public static int stepNum = 0;
    private static CellColor curColor;
    private static Opponent opponent;
    public static CellColor playerColor;

    public static void setOpponent(Opponent newOpponent) {
        opponent = newOpponent;
    }
    public static Opponent getOpponent() {
        return opponent;
    }
    public static void startGame(boolean playerFirst) {
        // only for score
        if (playerFirst) {
            playerColor = CellColor.WHITE;
        } else {
            playerColor = CellColor.BLACK;
        }

        // clear field
        for (var row: field) {
            Arrays.fill(row, CellColor.COLORLESS);
        }
        // start with white
        curColor = CellColor.BLACK;

        // initial position
        field[4][4] = CellColor.WHITE;
        field[4][5] = CellColor.BLACK;
        field[5][4] = CellColor.BLACK;
        field[5][5] = CellColor.WHITE;

        if (playerFirst) {
            changeColor();
            updatePossibleMoves();
            playerStep();
        } else {
            step();
        }
    }

    // if opponent is player - show possible moves
    // else set a move, change color and show possible moves
    public static void step() {
        if (opponent != Opponent.PLAYER) {
            changeColor();
            updatePossibleMoves();
            if (possibleMoves[0] != null) {
                canMove[curColor.ordinal() - 1] = true;
                if (opponent == Opponent.BEGINNER) {
                    setMove(simpleMove());
                } else {
                    setMove(smartMove());
                }
            } else {
                canMove[curColor.ordinal() - 1] = false;
                if (!canMove[oppositeColor().ordinal() - 1]) {
                    GameWindow.pcs.firePropertyChange("finishGame", null, null);
                    return;
                }
            }
        }
        changeColor();
        updatePossibleMoves();
        playerStep();
    }

    public static void playerStep() {
        // finish game if there are no moves
        if (possibleMoves[0] == null) {
            canMove[curColor.ordinal() - 1] = false;
            if (!canMove[oppositeColor().ordinal() - 1]) {
                GameWindow.pcs.firePropertyChange("finishGame", null, null);
            } else {
                step();
            }
        } else {
            canMove[curColor.ordinal() - 1] = true;
            GameWindow.pcs.firePropertyChange("nextMove", null,
                    new ColoredCells(curColor, possibleMoves));
        }
    }
    // fired from GameWindow when move is chosen
    public static void setMove(Position pos) {
        safeSetMove(pos);

        GameWindow.pcs.firePropertyChange("setMove", null,
                new ColoredCells(curColor, changedCells));
    }

    public static void safeSetMove(Position pos) {
        updateChangedCells(pos);
        for (int i = 0; changedCells[i] != null; ++i) {
            field[changedCells[i].y + 1][changedCells[i].x + 1] = curColor;
        }
        previousMoves[stepNum] = changedCells.clone();
        ++stepNum;
    }

    public static void stepBack() {
        if (stepNum == 0 || stepNum == 1 && opponent != Opponent.PLAYER) {
            return;
        }
        changeColor();
        deleteMove();
        if (opponent != Opponent.PLAYER) {
            changeColor();
            deleteMove();
        }
        updatePossibleMoves();
        playerStep();
    }

    public static void deleteMove() {
        safeDeleteMove();
        GameWindow.pcs.firePropertyChange("deleteMove", null,
                new ColoredCells(oppositeColor(), changedCells));
    }

    public static void safeDeleteMove() {
        --stepNum;
        changedCells = previousMoves[stepNum];
        field[changedCells[0].y + 1][changedCells[0].x + 1] = CellColor.COLORLESS;
        for (int i = 1; changedCells[i] != null; ++i) {
            field[changedCells[i].y + 1][changedCells[i].x + 1] = oppositeColor();
        }
    }

    public static Position simpleMove() {
        Position bestMove = possibleMoves[0];
        int maxScore = 0;
        int score;
        for (int i = 0; possibleMoves[i] != null; ++i) {
            Position move = possibleMoves[i];
            score = getScore(move);
            if (score > maxScore) {
                maxScore = score;
                bestMove = move;
            }
        }
        return bestMove;
    }

    public static Position smartMove() {
        Position bestMove = possibleMoves[0];
        int maxScore = -100;
        int score1, score2;
        for (int i = 0; possibleMoves[i] != null; ++i) {
            Position move = possibleMoves[i];
            score1 = getScore(move);
            safeSetMove(move);
            changeColor();
            updatePossibleMoves();
            for (int j = 0; possibleMoves[j] != null; ++j) {
                Position move2 = possibleMoves[j];
                score2 = getScore(move2);
                if (score1 - score2 > maxScore) {
                    maxScore = score1 - score2;
                    bestMove = move;
                }
            }
            changeColor();
            safeDeleteMove();
            updatePossibleMoves();
        }
        return bestMove;
    }

    public static int getScore(Position move) {
        updateChangedCells(move);
        int score = 0;
        for (int cellInd = 0; changedCells[cellInd] != null; ++cellInd) {
            Position cell = changedCells[cellInd];
            score += 1;
            if (cell.x == 0 || cell.x == 7 || cell.y == 0 || cell.y == 7) {
                score += 1;
            }
        }
        if (move.x == 0 || move.x == 7) {
            score += 0.4;
        }
        if (move.y == 0 || move.y == 7) {
            score += 0.4;
        }
        return score;
    }

    public static void updatePossibleMoves() {
        CellColor oppColor = oppositeColor();
        int numPossibleMoves = 0;
        for (int x = 1; x < 9; ++x) {
            for (int y = 1; y < 9; ++y) {
                if (field[y][x] == CellColor.COLORLESS) {
                    for (Position dir: directions) {
                        if (field[y + dir.y][x + dir.x] == oppColor) {
                            int x1 = x + 2 * dir.x, y1 = y + 2 * dir.y;
                            while (field[y1][x1] == oppColor) {
                                x1 += dir.x;
                                y1 += dir.y;
                            }
                            if (field[y1][x1] == curColor) {
                                possibleMoves[numPossibleMoves] = new Position(x - 1, y - 1);
                                ++numPossibleMoves;
                                break;
                            }
                        }
                    }
                }
            }
        }
        possibleMoves[numPossibleMoves] = null;
    }

    // returns array of cells that will reverse after move on pos
    public static void updateChangedCells(Position pos) {
        CellColor oppColor = oppositeColor();
        changedCells[0] = pos;
        int numChangedCells = 1;
        pos.x += 1;
        pos.y += 1;
        for (Position dir: directions) {
            if (field[pos.y + dir.y][pos.x + dir.x] == oppColor) {
                int x1 = pos.x + 2 * dir.x, y1 = pos.y + 2 * dir.y;
                while (field[y1][x1] == oppColor) {
                    x1 += dir.x;
                    y1 += dir.y;
                }
                if (field[y1][x1] == curColor) {
                    x1 -= dir.x;
                    y1 -= dir.y;
                    while (x1 != pos.x || y1 != pos.y) {
                        changedCells[numChangedCells] = new Position(x1 - 1, y1 - 1);
                        ++numChangedCells;
                        x1 -= dir.x;
                        y1 -= dir.y;
                    }
                }
            }
        }
        pos.x -= 1;
        pos.y -= 1;
        changedCells[numChangedCells] = null;
    }

    private static void changeColor() {
        curColor = oppositeColor();
    }

    private static CellColor oppositeColor() {
        return (curColor == CellColor.WHITE ? CellColor.BLACK : CellColor.WHITE);
    }

    public static int[] getScore() {
        int[] score = new int[2];
        for (int r = 1; r < 9; ++r) {
            for (int c = 1; c < 9; ++c) {
                if (field[r][c] == CellColor.WHITE) {
                    ++score[0];
                } else if (field[r][c] == CellColor.BLACK) {
                    ++score[1];
                }
            }
        }
        return score;
    }

    public static class Position {
        public int x, y;
        public Position (int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class ColoredCells {
        public CellColor color;
        public Position[] cells;
        public ColoredCells(CellColor color, Position[] cells) {
            this.color = color;
            this.cells = cells;
        }
    }
}
