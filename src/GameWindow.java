import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeSupport;

import static java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment;
import static javax.swing.SwingUtilities.invokeLater;

public final class GameWindow extends JFrame {
    public static final int height = getLocalGraphicsEnvironment().getMaximumWindowBounds().height;
    public static final int width = getLocalGraphicsEnvironment().getMaximumWindowBounds().width;
    private static final int[] bestScore = {-1, -1};

    // field in Game and field in GameWindow work independently
    // but both describe common entity (field) and must be synchronised
    private static final GameLayout.SelectedChip[][] field = new GameLayout.SelectedChip[8][8];
    // create empty pcs
    public static final PropertyChangeSupport pcs = new PropertyChangeSupport(new Object());

    public GameWindow() {
        // fullscreen
        setUndecorated(true);
        getLocalGraphicsEnvironment().getDefaultScreenDevice().setFullScreenWindow(this);

        setTitle("Reversi");
        setIconImage((new ImageIcon("icon.jpg")).getImage());

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // start game
        // generate start menu
        pcs.addPropertyChangeListener("startGame", e -> {
            remove(getContentPane());
            JPanel menuPanel = new StartMenuLayout();
            setContentPane(menuPanel);
            repaint();
            revalidate();
        });

        // get result from start menu (choose an opponent)
        // then generate color menu (if needed)
        pcs.addPropertyChangeListener("opponent", e -> {
            // new value must have type Game.Opponent
            Game.setOpponent((Game.Opponent) e.getNewValue());

            if (e.getNewValue() == Game.Opponent.PLAYER) {
                pcs.firePropertyChange("playerFirst", null, true);
            } else {
                remove(getContentPane());
                setContentPane(new ColorMenuLayout());
                revalidate();
            }
        });

        // get result from color menu (choose color for player)
        // generate game field and start game
        pcs.addPropertyChangeListener("playerFirst", e -> {
            remove(getContentPane());
            setContentPane(new GameLayout());
            revalidate();

            // new value must have type boolean
            invokeLater(() -> Game.startGame((Boolean) e.getNewValue()));
        });

        // game listener, fires in Game.step and Game.startGame
        // get possible moves, show and wait player to click on of them
        pcs.addPropertyChangeListener("nextMove", e -> {
            var moves = (Game.ColoredCells) e.getNewValue();
            nextMove(moves.color, moves.cells);
        });

        // game listener, fires in GameWindow.nextMove
        // give chosen position to Game (so it can generate move result)
        pcs.addPropertyChangeListener("chipSelected", e -> {
            Game.setMove((Game.Position) e.getNewValue());
            invokeLater(() -> {
                if (Game.getOpponent() != Game.Opponent.PLAYER) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                Game.step();
            });
        });

        // game listener, fires in Game.setMove
        // put new chip on board and recolor dependent chips
        // start next step
        pcs.addPropertyChangeListener("setMove", e -> {
            var changedCells = (Game.ColoredCells) e.getNewValue();
            setMove(changedCells.color, changedCells.cells);
            GameLayout.updateScore();
        });

        pcs.addPropertyChangeListener("deleteMove", e -> {
            pcs.firePropertyChange("clear", null, null);
            var changedCells = (Game.ColoredCells) e.getNewValue();
            deleteMove(changedCells.color, changedCells.cells);
            GameLayout.updateScore();
        });

        // game listener, fires in Game.step
        // finishes this game (not the session)
        pcs.addPropertyChangeListener("finishGame", e -> {
            // remove game listeners (including this)
            GameLayout.addFinishButtons();
        });

        pcs.firePropertyChange("startGame", null, null);
    }

    private static class StartMenuLayout extends SimpleComponent.Panel {
        public StartMenuLayout() {
            add(new SimpleComponent.Label(
                    "Реверси",
                    40, width / 2, height / 2 - 300
            ));
            add(new SimpleComponent.Button(
                    "Два игрока",
                    30, width / 2, height / 2 - 100,
                    e -> pcs.firePropertyChange("opponent", null, Game.Opponent.PLAYER)
            ));
            add(new SimpleComponent.Button(
                    "Новичок",
                    30, width / 2, height / 2,
                    e -> pcs.firePropertyChange("opponent", null, Game.Opponent.BEGINNER)
            ));
            add(new SimpleComponent.Button(
                    "Профессионал",
                    30, width / 2, height / 2 + 100,
                    e -> pcs.firePropertyChange("opponent", null, Game.Opponent.PROFESSIONAL)
            ));
            if (bestScore[0] >= 0) {
                add(new SimpleComponent.Label(
                        "Лучший счёт  " + bestScore[0] + ":" + bestScore[1],
                        30, width / 2, height / 2 + 300
                ));
            } else {
                add(new SimpleComponent.Label(
                        "Лучший счёт  -",
                        30, width / 2, height / 2 + 300
                ));
            }
        }
    }

    private static class ColorMenuLayout extends SimpleComponent.Panel {
        public ColorMenuLayout () {
            add(new SimpleComponent.Button(
                    "Белые",
                    30, width / 2, height / 2 - 75,
                    e -> pcs.firePropertyChange("playerFirst", null, true)
            ));
            add(new SimpleComponent.Button(
                    "Чёрные",
                    30, width / 2, height / 2 + 75,
                    e -> pcs.firePropertyChange("playerFirst", null, false)
            ));
        }
    }

    private static class GameLayout extends SimpleComponent.Panel {
        // cell size in pixels
        private static final int cellSize = 50;
        private static final int boundX = width / 2 - cellSize * 4;
        private static final int boundY = height / 2 - cellSize * 4;
        public static JLabel scoreLabel;

        public GameLayout() {

            add(new Board(boundX, boundY, cellSize * 8, cellSize * 8));


            add(field[3][3] = new SelectedChip(Game.CellColor.WHITE, 3, 3));
            add(field[4][3] = new SelectedChip(Game.CellColor.BLACK, 3, 4));
            add(field[3][4] = new SelectedChip(Game.CellColor.BLACK, 4, 3));
            add(field[4][4] = new SelectedChip(Game.CellColor.WHITE, 4, 4));

            // initialise with long text (so size is big enough)
            add(scoreLabel = new SimpleComponent.Label("00:00", 30, width / 2, height / 2 - 300));
            scoreLabel.setText("2:2");

            add(new SimpleComponent.Button("Step back", 30, 300, height / 2, e -> Game.stepBack()));

        }

        public static void updateScore() {
            int[] score = Game.getScore();
            scoreLabel.setText(score[0] + ":" + score[1]);
        }

        public static void addFinishButtons() {
            scoreLabel.getParent().add(new SimpleComponent.Button("Сыграть ещё", 30, width / 2 - 100, height / 2 + 300, e -> {
                int[] score = Game.getScore();
                if ((Game.playerColor == Game.CellColor.WHITE && score[0] > bestScore[0]) ||
                        (Game.playerColor == Game.CellColor.BLACK && score[1] > bestScore[1])) {
                    bestScore[0] = score[0];
                    bestScore[1] = score[1];
                }
                pcs.firePropertyChange("startGame", null, null);
            }));
            scoreLabel.getParent().add(new SimpleComponent.Button("Закончить", 30, width / 2 + 100, height / 2 + 300,
                    e -> SwingUtilities.getWindowAncestor(scoreLabel).dispose()));
        }

        private static class Board extends JComponent {
            private final int width, height;

            Board(int x, int y, int width, int height) {
                this.width = width;
                this.height = height;
                setBounds(x, y, width, height);
            }

            public void paint(Graphics g) {
                for (int i = 1; i < 8; ++i) {
                    g.drawLine(cellSize * i, 0, cellSize * i, height);
                    g.drawLine(0, cellSize * i, width, cellSize * i);
                }
            }
        }
        private static class SelectedChip extends JLabel {
            public SelectedChip(Game.CellColor color, int r, int c) {
                setBounds(boundX + r * cellSize, boundY + c * cellSize, cellSize, cellSize);
                setColor(color);
            }

            public void setColor(Game.CellColor color) {
                if (color == Game.CellColor.WHITE) {
                    setIcon(new ImageIcon("./chip_white.png"));
                } else {
                    setIcon(new ImageIcon("./chip_black.png"));
                }
            }
        }
        private static class UnselectedChip extends JButton {
            public UnselectedChip(Game.CellColor color, int r, int c, ActionListener action) {
                // +-1 because button boundary repaints grid of the board
                setBounds(boundX + r * cellSize + 1, boundY + c * cellSize + 1, cellSize - 1, cellSize - 1);
                if (color == Game.CellColor.WHITE) {
                    setIcon(new ImageIcon("./chip_white_unselected.png"));
                } else {
                    setIcon(new ImageIcon("./chip_black_unselected.png"));
                }
                addActionListener(action);

                setBackground(Color.WHITE);
                setBorderPainted(false);
                setFocusPainted(false);
                setContentAreaFilled(false);
            }
        }
    }

    public void nextMove(Game.CellColor color, Game.Position[] moves) {
        JButton[] chips = new JButton[moves.length];
        for (int i = 0; moves[i] != null; ++i) {
            // to use in lambda
            int _i = i;
            chips[i] = new GameLayout.UnselectedChip(color, moves[i].x, moves[i].y,
                    e -> pcs.firePropertyChange("selected", null, moves[_i]));
            add(chips[i]);
        }
        repaint();
        pcs.addPropertyChangeListener("selected", e -> {
            // remove temporary chips and listeners
            pcs.firePropertyChange("clear", null, null);
            // fire to main body (GameWindow)
            pcs.firePropertyChange("chipSelected", null, e.getNewValue());
        });
        pcs.addPropertyChangeListener("clear", e -> {
            // remove listeners
            pcs.removePropertyChangeListener("clear", pcs.getPropertyChangeListeners("clear")[0]);
            pcs.removePropertyChangeListener("selected", pcs.getPropertyChangeListeners("selected")[0]);

            for (int i = 0; chips[i] != null; ++i) {
                remove(chips[i]);
            }
            repaint();
        });
    }

    // one of positions in changed must be empty (it's position of the move)
    public void setMove(Game.CellColor color, Game.Position[] changedCells) {
        Game.Position pos = changedCells[0];
        add(field[pos.y][pos.x] = new GameLayout.SelectedChip(color, pos.x, pos.y));
        for (int i = 1; changedCells[i] != null; ++i) {
            pos = changedCells[i];
            field[pos.y][pos.x].setColor(color);
        }
        repaint();
    }

    // color is opposite to that used in setMove
    public void deleteMove(Game.CellColor color, Game.Position[] changedCells) {
        Game.Position pos = changedCells[0];
        remove(field[pos.y][pos.x]);
        field[pos.y][pos.x] = null;
        for (int i = 1; changedCells[i] != null; ++i) {
            pos = changedCells[i];
            field[pos.y][pos.x].setColor(color);
        }
        repaint();
    }
}