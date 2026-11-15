package workspace;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Home screen: your workspaces, pending invitations and the "recipe" picker for new ones. */
final class Dashboard extends JPanel {
    private final Db.User me;
    private final Consumer<Db.Workspace> opener;
    private final Theme.Paged wsList = new Theme.Paged(5);
    private final Theme.Paged invList = new Theme.Paged(2);
    private final JTextField name = Theme.field(20);
    private final JTextField desc = Theme.field(20);
    private final JComboBox<String> recipe = Theme.combo(Db.TEMPLATES);
    private final JLabel recipeInfo = Theme.label(Db.TEMPLATE_INFO[0], Theme.SMALL, Theme.MUTED);
    private final javax.swing.Timer poll = new javax.swing.Timer(3000, e -> refresh());
    private List<Db.Workspace> lastWs;
    private List<Db.Invite> lastInv;

    Dashboard(Db.User me, Consumer<Db.Workspace> opener, Runnable signOut) {
        super(new BorderLayout(0, 20));
        this.me = me;
        this.opener = opener;
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        JPanel head = Theme.plain();
        head.setLayout(new BorderLayout());
        JPanel t = Theme.plain();
        t.setLayout(new GridLayout(2, 1));
        t.add(Theme.h1("CoSpace"));
        t.add(Theme.muted("Signed in as " + me.name() + "  (@" + me.username() + ")"));
        Theme.Btn out = new Theme.Btn("Sign out", false);
        out.addActionListener(e -> { poll.stop(); signOut.run(); });
        head.add(t, BorderLayout.WEST);
        head.add(out, BorderLayout.EAST);

        JPanel left = Theme.card();
        left.setLayout(new BorderLayout(0, 12));
        left.add(Theme.h2("Your workspaces"), BorderLayout.NORTH);
        left.add(wsList, BorderLayout.CENTER);

        JPanel inv = Theme.card();
        inv.setLayout(new BorderLayout(0, 12));
        inv.add(Theme.h2("Invitations"), BorderLayout.NORTH);
        inv.add(invList, BorderLayout.CENTER);

        JPanel create = Theme.card();
        create.setLayout(new BorderLayout(0, 12));
        create.add(Theme.h2("New workspace"), BorderLayout.NORTH);
        create.add(Theme.form(new String[]{"Name", "Description", "Recipe"}, new JComponent[]{name, desc, recipe}), BorderLayout.CENTER);
        JPanel foot = Theme.plain();
        foot.setLayout(new BorderLayout(0, 8));
        Theme.Btn go = new Theme.Btn("Create workspace", true);
        foot.add(recipeInfo, BorderLayout.NORTH);
        foot.add(go, BorderLayout.SOUTH);
        create.add(foot, BorderLayout.SOUTH);

        JPanel rightCol = Theme.plain();
        rightCol.setLayout(new GridLayout(2, 1, 0, 16));
        rightCol.add(inv);
        rightCol.add(create);
        JPanel body = Theme.plain();
        body.setLayout(new GridLayout(1, 2, 20, 0));
        body.add(left);
        body.add(rightCol);
        add(head, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);

        recipe.addActionListener(e -> recipeInfo.setText(Db.TEMPLATE_INFO[recipe.getSelectedIndex()]));
        go.addActionListener(e -> {
            String n = name.getText().trim();
            if (n.isEmpty()) { Theme.error(this, "Give the workspace a name."); return; }
            String d = desc.getText().trim(), r = (String) recipe.getSelectedItem();
            Async.call(this, () -> Db.createWorkspace(me.id(), n, d, r), w -> { name.setText(""); desc.setText(""); opener.accept(w); });
        });
        poll.start();
        refresh();
    }

    void refresh() {
        record Data(List<Db.Workspace> ws, List<Db.Invite> inv) {}
        Async.quiet(() -> new Data(Db.workspaces(me.id()), Db.pendingInvites(me)), d -> {
            if (!d.ws().equals(lastWs)) {
                lastWs = d.ws();
                List<JComponent> rows = new ArrayList<>();
                for (Db.Workspace w : d.ws()) rows.add(wsRow(w));
                wsList.setRows(rows);
            }
            if (!d.inv().equals(lastInv)) {
                lastInv = d.inv();
                List<JComponent> rows = new ArrayList<>();
                for (Db.Invite i : d.inv()) rows.add(invRow(i));
                invList.setRows(rows);
            }
        });
    }

    private JComponent wsRow(Db.Workspace w) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBackground(Theme.PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE), BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JPanel text = Theme.plain();
        text.setLayout(new GridLayout(2, 1));
        text.add(Theme.label(w.name(), Theme.BOLD, Theme.INK));
        String d = w.description() == null ? "" : w.description();
        text.add(Theme.label(w.template() + "  ·  " + w.role().toLowerCase() + (d.isEmpty() ? "" : "  ·  " + d), Theme.SMALL, Theme.MUTED));
        Theme.Btn open = new Theme.Btn("Open", true);
        open.addActionListener(e -> opener.accept(w));
        p.add(text, BorderLayout.CENTER);
        p.add(open, BorderLayout.EAST);
        return p;
    }

    private JComponent invRow(Db.Invite i) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBackground(Theme.PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE), BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JPanel text = Theme.plain();
        text.setLayout(new GridLayout(2, 1));
        text.add(Theme.label(i.workspace(), Theme.BOLD, Theme.INK));
        text.add(Theme.label(i.from() + " invited you as " + i.role().toLowerCase(), Theme.SMALL, Theme.MUTED));
        JPanel btns = Theme.plain();
        btns.setLayout(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        Theme.Btn yes = new Theme.Btn("Accept", true).compact(), no = new Theme.Btn("Decline", false).compact();
        yes.addActionListener(e -> Async.exec(this, () -> Db.respond(i.id(), me, true), this::refresh));
        no.addActionListener(e -> Async.exec(this, () -> Db.respond(i.id(), me, false), this::refresh));
        btns.add(yes);
        btns.add(no);
        p.add(text, BorderLayout.CENTER);
        p.add(btns, BorderLayout.EAST);
        return p;
    }

    void stop() { poll.stop(); }
}
