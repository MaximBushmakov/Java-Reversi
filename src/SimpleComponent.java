import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

public class SimpleComponent {
    private static final Font simpleFont;

    static {
        try {
            simpleFont = Font.createFont(Font.TRUETYPE_FONT, new File("./Caveat.ttf"));
        } catch (FontFormatException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Label extends JLabel {
        public Label(String text, int size, int x, int y) {
            setText(text);
            setFont(simpleFont.deriveFont(Font.BOLD, size));
            // this font has some problems with positioning so add some space
            int width = getPreferredSize().width + 10,
                    height = getPreferredSize().height;
            setBounds(x - width / 2, y - height / 2, width, height);
            setBackground(Color.WHITE);
        }
    }

    public static class Button extends JButton {
        public Button(String text, int size, int x, int y, ActionListener action) {
            setText(text);
            setFont(simpleFont.deriveFont(Font.PLAIN, size));
            // need some additional space for font to show properly
            int width = getPreferredSize().width + 10,
                    height = getPreferredSize().height;
            setBounds(x - width / 2, y - height / 2, width, height);
            setBackground(Color.WHITE);
            addActionListener(action);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
        }
    }

    // only for GameWindow (needs its width)
    public static class Panel extends JPanel {
        public Panel() {
            setLayout(null);
            setOpaque(true);

            setBackground(Color.WHITE);

            add(new SimpleComponent.Button(
                    "Выход",
                    30,  GameWindow.width - 100, 50,
                    // there must be only one window
                    e -> {
                        SwingUtilities.getWindowAncestor(this).dispose();
                        System.exit(0);
                    }
            ));
        }
    }
}
