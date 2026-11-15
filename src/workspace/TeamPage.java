package workspace;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Members, roles (Owner / Editor / Viewer), presence and invitations. */
final class TeamPage extends Page {
    private final Theme.Paged list = new Theme.Paged(7);
    private final JTextField ident = Theme.field(18);
    private final JComboBox<String> roleBox = Theme.combo("EDITOR", "VIEWER");
    private List<Db.Member> last;

    TeamPage(Ctx ctx) {
        super(ctx);
        JPanel invite = Theme.card();
        invite.setLayout(new BorderLayout(0, 14));
        invite.setPreferredSize(new Dimension(300, 0));
        invite.add(Theme.h2("Invite a coworker"), BorderLayout.NORTH);
        JPanel body = Theme.plain();
        body.setLayout(new BorderLayout(0, 14));
        body.add(Theme.form(new String[]{"Username or email", "Role"}, new JComponent[]{ident, roleBox}), BorderLayout.NORTH);
        JPanel note = Theme.plain();
        note.setLayout(new BorderLayout(0, 10));
        Theme.Btn send = new Theme.Btn("Send invitation", true);
        note.add(send, BorderLayout.NORTH);
        JLabel hint = Theme.label("<html>The invitation shows up on their dashboard. They can accept even if they sign up later with that email.</html>", Theme.SMALL, Theme.MUTED);
        hint.setVerticalAlignment(SwingConstants.TOP);
        note.add(hint, BorderLayout.CENTER);
        body.add(note, BorderLayout.CENTER);
        invite.add(body, BorderLayout.CENTER);

        JPanel members = Theme.card();
        members.setLayout(new BorderLayout(0, 12));
        members.add(Theme.h2("Members"), BorderLayout.NORTH);
        members.add(list, BorderLayout.CENTER);
        add(invite, BorderLayout.WEST);
        add(members, BorderLayout.CENTER);

        send.addActionListener(e -> {
            if (!ctx.canEdit()) { Theme.error(owner(), "Viewers cannot invite people."); return; }
            String id = ident.getText();
            String role = (String) roleBox.getSelectedItem();
            Async.exec(this, () -> Db.invite(ctx.ws.id(), ctx.me.id(), id, role), () -> {
                ident.setText("");
                Theme.error(owner(), "Invitation sent.");
                refresh();
            });
        });
    }

    @Override void refresh() {
        Async.quiet(() -> Db.members(ctx.ws.id()), ms -> {
            if (ms.equals(last)) return;
            last = ms;
            List<JComponent> rows = new ArrayList<>();
            for (Db.Member m : ms) rows.add(row(m));
            list.setRows(rows);
        });
    }

    private JComponent row(Db.Member m) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBackground(Theme.PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE), BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        JPanel dot = Theme.plain();
        dot.setLayout(new GridBagLayout());
        dot.add(Theme.dot(m.online()));
        JPanel text = Theme.plain();
        text.setLayout(new GridLayout(2, 1));
        text.add(Theme.label(m.name() + (m.userId() == ctx.me.id() ? "  (you)" : ""), Theme.BOLD, Theme.INK));
        text.add(Theme.label("@" + m.username() + "  ·  " + (m.online() ? "online" : "offline"), Theme.SMALL, Theme.MUTED));
        JPanel right = Theme.plain();
        right.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (ctx.isOwner() && !m.role().equals("OWNER")) {
            JComboBox<String> cb = Theme.combo("EDITOR", "VIEWER");
            cb.setSelectedItem(m.role());
            cb.addActionListener(e -> Async.exec(this, () -> Db.changeRole(ctx.ws.id(), m.userId(), (String) cb.getSelectedItem()), null));
            Theme.Btn rm = new Theme.Btn("Remove", false).compact();
            rm.addActionListener(e -> Async.exec(this, () -> Db.removeMember(ctx.ws.id(), m.userId()), this::refresh));
            right.add(cb);
            right.add(rm);
        } else right.add(Theme.label(m.role(), Theme.SMALL, Theme.MUTED));
        p.add(dot, BorderLayout.WEST);
        p.add(text, BorderLayout.CENTER);
        p.add(right, BorderLayout.EAST);
        return p;
    }
}
