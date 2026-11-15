package workspace;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        try { Db.init(); }
        catch (Exception e) { System.err.println("Database error: " + e.getMessage()); return; }
        // All Swing UI is created on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> { Theme.install(); new LoginFrame().setVisible(true); });
    }
}
