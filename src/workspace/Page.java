package workspace;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

abstract class Page extends JPanel {
    final Ctx ctx;
    Page(Ctx ctx) {
        super(new BorderLayout(16, 16));
        this.ctx = ctx;
        setOpaque(false);
        setBorder(new EmptyBorder(20, 20, 20, 20));
    }
    abstract void refresh();
    Window owner() { return SwingUtilities.getWindowAncestor(this); }
}
