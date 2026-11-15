package workspace;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.List;

/** Three-column task board. Each column shows at most 6 cards (no scrolling). */
final class TasksPage extends Page {
    private static final String[] KEYS = {"TODO", "DOING", "DONE"};
    private static final String[] NAMES = {"To do", "In progress", "Done"};
    private static final int MAX = 6;
    private final JPanel[] bodies = new JPanel[3];
    private final JLabel[] counts = new JLabel[3];
    private final JTextField input = Theme.field(30);
    private List<Db.Task> last;

    TasksPage(Ctx ctx) {
        super(ctx);
        JPanel top = Theme.card();
        top.setLayout(new BorderLayout(10, 0));
        Theme.Btn addBtn = new Theme.Btn("Add task", true);
        top.add(input, BorderLayout.CENTER);
        top.add(addBtn, BorderLayout.EAST);
        JPanel cols = Theme.plain();
        cols.setLayout(new GridLayout(1, 3, 16, 0));
        for (int i = 0; i < 3; i++) {
            JPanel col = Theme.card();
            col.setLayout(new BorderLayout(0, 12));
            JPanel h = Theme.plain();
            h.setLayout(new BorderLayout());
            counts[i] = Theme.muted("0");
            h.add(Theme.h2(NAMES[i]), BorderLayout.WEST);
            h.add(counts[i], BorderLayout.EAST);
            bodies[i] = Theme.plain();
            bodies[i].setLayout(new GridLayout(MAX, 1, 0, 8));
            JPanel wrap = Theme.plain();
            wrap.setLayout(new BorderLayout());
            wrap.add(bodies[i], BorderLayout.NORTH);
            col.add(h, BorderLayout.NORTH);
            col.add(wrap, BorderLayout.CENTER);
            cols.add(col);
        }
        add(top, BorderLayout.NORTH);
        add(cols, BorderLayout.CENTER);
        Runnable add = () -> {
            String t = input.getText().trim();
            if (t.isEmpty() || !ctx.canEdit()) return;
            input.setText("");
            Async.exec(this, () -> Db.addTask(ctx.ws.id(), ctx.me.id(), t), this::refresh);
        };
        addBtn.addActionListener(e -> add.run());
        input.addActionListener(e -> add.run());
    }

    @Override void refresh() {
        Async.quiet(() -> Db.tasks(ctx.ws.id()), ts -> {
            if (ts.equals(last)) return;
            last = ts;
            for (int c = 0; c < 3; c++) {
                bodies[c].removeAll();
                int n = 0, total = 0;
                for (Db.Task t : ts) {
                    if (!t.status().equals(KEYS[c])) continue;
                    total++;
                    if (n < MAX) { bodies[c].add(card(t, c)); n++; }
                }
                while (n < MAX) { bodies[c].add(Theme.plain()); n++; }
                counts[c].setText(total > MAX ? total + "  (showing " + MAX + ")" : "" + total);
                bodies[c].revalidate();
                bodies[c].repaint();
            }
        });
    }

    private JPanel card(Db.Task t, int col) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setBackground(Theme.BG);
        p.setBorder(new CompoundBorder(new LineBorder(Theme.LINE, 1, true), new EmptyBorder(8, 10, 8, 8)));
        JPanel text = Theme.plain();
        text.setLayout(new GridLayout(2, 1));
        text.add(Theme.label(t.title(), Theme.BOLD, Theme.INK));
        text.add(Theme.label(t.assignee().isEmpty() ? "Unassigned" : t.assignee(), Theme.SMALL, Theme.MUTED));
        JPanel btns = Theme.plain();
        btns.setLayout(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        Theme.Btn l = new Theme.Btn("<", false).compact(), r = new Theme.Btn(">", false).compact();
        l.setEnabled(col > 0 && ctx.canEdit());
        r.setEnabled(col < 2 && ctx.canEdit());
        l.addActionListener(e -> move(t, KEYS[col - 1]));
        r.addActionListener(e -> move(t, KEYS[col + 1]));
        btns.add(l);
        btns.add(r);
        p.add(text, BorderLayout.CENTER);
        p.add(btns, BorderLayout.EAST);
        return p;
    }

    private void move(Db.Task t, String status) {
        Async.exec(this, () -> Db.moveTask(ctx.ws.id(), ctx.me.id(), t.id(), status, t.title()), this::refresh);
    }
}
