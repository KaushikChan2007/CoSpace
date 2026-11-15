package workspace;

import javax.swing.*;
import java.awt.*;

/** Main window: switches between the dashboard and an open workspace. */
final class AppFrame extends JFrame {
    private final Db.User me;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final Dashboard dash;
    private WorkspacePanel open;

    AppFrame(Db.User me) {
        super("CoSpace  -  " + me.name());
        this.me = me;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 780);
        setMinimumSize(new Dimension(1050, 700));
        setLocationRelativeTo(null);
        dash = new Dashboard(me, this::openWorkspace, this::signOut);
        root.add(dash, "dash");
        setContentPane(root);
    }

    private void openWorkspace(Db.Workspace w) {
        if (open != null) return;
        open = new WorkspacePanel(me, w, this::closeWorkspace);
        root.add(open, "ws");
        cards.show(root, "ws");
        open.start();
    }

    private void closeWorkspace() {
        if (open == null) return;
        open.stop();
        root.remove(open);
        open = null;
        cards.show(root, "dash");
        dash.refresh();
    }

    private void signOut() {
        dash.stop();
        dispose();
        new LoginFrame().setVisible(true);
    }
}
