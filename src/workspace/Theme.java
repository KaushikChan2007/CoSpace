package workspace;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Look & feel: white, soft greys and soft black only. No pure #000 on pure #FFF, so contrast stays gentle. */
public final class Theme {
    private Theme() {}

    public static final Color BG    = new Color(0xF5F5F5);
    public static final Color PANEL = Color.WHITE;
    public static final Color LINE  = new Color(0xE2E2E2);
    public static final Color SOFT  = new Color(0xEDEDED);
    public static final Color MUTED = new Color(0x848484);
    public static final Color INK   = new Color(0x363636);
    public static final Color DARK  = new Color(0x2E2E2E);

    private static String pick(String... names) {
        Set<String> have = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : names) if (have.contains(n)) return n;
        return names[names.length - 1];
    }

    // Plain system fonts (no trendy/"AI" typefaces)
    public static final String FAMILY = pick("Segoe UI", "Helvetica Neue", "Calibri", "Arial", Font.DIALOG);
    public static final Font UI    = new Font(FAMILY, Font.PLAIN, 13);
    public static final Font BOLD  = new Font(FAMILY, Font.BOLD, 13);
    public static final Font H1    = new Font(FAMILY, Font.BOLD, 22);
    public static final Font H2    = new Font(FAMILY, Font.BOLD, 15);
    public static final Font SMALL = new Font(FAMILY, Font.PLAIN, 11);
    public static final Font CODE  = new Font(pick("Consolas", "Menlo", "DejaVu Sans Mono", Font.MONOSPACED), Font.PLAIN, 13);

    public static void install() {
        for (Object k : Collections.list(UIManager.getDefaults().keys()))
            if (k.toString().endsWith(".font")) UIManager.put(k, new FontUIResource(UI));
        UIManager.put("Panel.background", PANEL);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", INK);
        UIManager.put("TextField.foreground", INK);
        UIManager.put("ComboBox.background", PANEL);
        UIManager.put("ComboBox.foreground", INK);
    }

    // ---------- small component factories ----------
    public static JLabel label(String t, Font f, Color c) { JLabel l = new JLabel(t); l.setFont(f); l.setForeground(c); return l; }
    public static JLabel h1(String t)    { return label(t, H1, DARK); }
    public static JLabel h2(String t)    { return label(t, H2, DARK); }
    public static JLabel muted(String t) { return label(t, UI, MUTED); }

    public static JPanel plain() { JPanel p = new JPanel(); p.setOpaque(false); return p; }

    public static JPanel card() {
        JPanel p = new JPanel();
        p.setBackground(PANEL);
        p.setBorder(new CompoundBorder(new LineBorder(LINE, 1, true), new EmptyBorder(16, 16, 16, 16)));
        return p;
    }

    private static Border inputBorder() {
        return new CompoundBorder(new LineBorder(LINE, 1, true), new EmptyBorder(7, 10, 7, 10));
    }

    public static JTextField field(int cols) {
        JTextField f = new JTextField(cols);
        f.setFont(UI); f.setForeground(INK); f.setBackground(PANEL); f.setCaretColor(INK); f.setBorder(inputBorder());
        return f;
    }

    public static JPasswordField password(int cols) {
        JPasswordField f = new JPasswordField(cols);
        f.setFont(UI); f.setForeground(INK); f.setBackground(PANEL); f.setCaretColor(INK); f.setBorder(inputBorder());
        return f;
    }

    public static JTextArea area(boolean code) {
        JTextArea a = new JTextArea();
        a.setFont(code ? CODE : UI); a.setForeground(INK); a.setBackground(PANEL); a.setCaretColor(INK);
        a.setSelectionColor(new Color(0xDCDCDC)); a.setSelectedTextColor(INK);
        a.setBorder(new EmptyBorder(10, 12, 10, 12)); a.setTabSize(4);
        a.setLineWrap(true); a.setWrapStyleWord(!code);
        return a;
    }

    public static JComboBox<String> combo(String... items) {
        JComboBox<String> c = new JComboBox<>(items);
        c.setFont(UI); c.setBackground(PANEL); c.setForeground(INK);
        return c;
    }

    /** Stack "label above field" rows. */
    public static JPanel form(String[] labels, JComponent[] fields) {
        JPanel p = plain(); p.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < labels.length; i++) {
            g.gridy = 2 * i;     g.insets = new Insets(i == 0 ? 0 : 12, 0, 4, 0); p.add(label(labels[i], SMALL, MUTED), g);
            g.gridy = 2 * i + 1; g.insets = new Insets(0, 0, 0, 0);               p.add(fields[i], g);
        }
        return p;
    }

    public static JComponent dot(boolean online) {
        JComponent d = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(online ? DARK : new Color(0xD0D0D0));
                g2.fillOval(0, 0, 10, 10); g2.dispose();
            }
        };
        d.setPreferredSize(new Dimension(10, 10)); d.setMinimumSize(new Dimension(10, 10));
        return d;
    }

    public static void error(Component c, String m) { JOptionPane.showMessageDialog(c, m, "CoSpace", JOptionPane.PLAIN_MESSAGE); }

    private static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;"); }

    /** A flat list row (used for files, workspaces...). */
    public static JButton listItem(String title, String sub, boolean selected, Runnable action) {
        JButton b = new JButton("<html><b>" + esc(title) + "</b><br><span style='color:#848484'>" + esc(sub) + "</span></html>");
        b.setHorizontalAlignment(SwingConstants.LEFT); b.setFocusPainted(false);
        b.setOpaque(true); b.setBackground(selected ? SOFT : PANEL); b.setForeground(INK);
        b.setBorder(new CompoundBorder(new LineBorder(LINE, 1, true), new EmptyBorder(8, 12, 8, 12)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }

    // ---------- buttons ----------
    public static class Btn extends JButton {
        private final boolean primary;
        public Btn(String t, boolean primary) {
            super(t); this.primary = primary;
            setFont(BOLD); setFocusPainted(false); setContentAreaFilled(false); setBorderPainted(false); setOpaque(false);
            setForeground(primary ? Color.WHITE : INK);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(8, 16, 8, 16));
        }
        public Btn compact() { setBorder(new EmptyBorder(4, 10, 4, 10)); return this; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = primary ? DARK : SOFT;
            if (!isEnabled()) c = primary ? new Color(0xB5B5B5) : new Color(0xF2F2F2);
            else if (getModel().isRollover()) c = primary ? new Color(0x474747) : new Color(0xE2E2E2);
            g2.setColor(c); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10); g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class Nav extends JToggleButton {
        public Nav(String t) {
            super(t);
            setFont(BOLD); setFocusPainted(false); setContentAreaFilled(false); setBorderPainted(false); setOpaque(false);
            setHorizontalAlignment(SwingConstants.LEFT); setBorder(new EmptyBorder(10, 16, 10, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSelected()) { g2.setColor(DARK); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10); }
            else if (getModel().isRollover()) { g2.setColor(SOFT); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10); }
            g2.dispose();
            setForeground(isSelected() ? Color.WHITE : INK);
            super.paintComponent(g);
        }
    }

    /** Pagination instead of scrolling: shows a fixed number of rows per page. */
    public static class Paged extends JPanel {
        private final int size;
        private List<? extends JComponent> rows = List.of();
        private int page;
        private final JPanel body = new JPanel(new GridLayout(0, 1, 0, 6));
        private final JLabel info = label("", SMALL, MUTED);
        private final Btn prev = new Btn("Prev", false).compact(), next = new Btn("Next", false).compact();

        public Paged(int size) {
            super(new BorderLayout(0, 10));
            this.size = size; setOpaque(false); body.setOpaque(false);
            info.setHorizontalAlignment(SwingConstants.CENTER);
            JPanel foot = plain(); foot.setLayout(new BorderLayout());
            foot.add(prev, BorderLayout.WEST); foot.add(info, BorderLayout.CENTER); foot.add(next, BorderLayout.EAST);
            add(body, BorderLayout.NORTH); add(foot, BorderLayout.SOUTH);
            prev.addActionListener(e -> { page--; render(); });
            next.addActionListener(e -> { page++; render(); });
            render();
        }
        private int pages() { return Math.max(1, (rows.size() + size - 1) / size); }
        public void setRows(List<? extends JComponent> r) { rows = r; if (page >= pages()) page = pages() - 1; render(); }
        private void render() {
            body.removeAll();
            for (int i = 0; i < size; i++) {
                int idx = page * size + i;
                body.add(idx < rows.size() ? rows.get(idx) : plain());
            }
            info.setText((page + 1) + " / " + pages());
            prev.setEnabled(page > 0); next.setEnabled(page < pages() - 1);
            body.revalidate(); body.repaint();
        }
    }
}
