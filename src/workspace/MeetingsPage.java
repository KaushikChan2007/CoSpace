package workspace;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Schedule meetings, start an instant one, and open the shared meeting room. */
final class MeetingsPage extends Page {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final JTextField title = Theme.field(18);
    private final JTextField when = Theme.field(18);
    private final JTextArea agenda = Theme.area(false);
    private final Theme.Paged list = new Theme.Paged(4);
    private List<Db.Meeting> last;

    MeetingsPage(Ctx ctx) {
        super(ctx);
        when.setText(LocalDateTime.now().plusHours(1).withMinute(0).format(FMT));
        agenda.setRows(4);
        JPanel form = Theme.card();
        form.setLayout(new BorderLayout(0, 14));
        form.setPreferredSize(new Dimension(320, 0));
        form.add(Theme.h2("New meeting"), BorderLayout.NORTH);
        JPanel agendaBox = Theme.plain();
        agendaBox.setLayout(new BorderLayout());
        agendaBox.setBorder(new LineBorder(Theme.LINE, 1, true));
        agendaBox.add(agenda);
        JPanel fields = Theme.plain();
        fields.setLayout(new BorderLayout());
        fields.add(Theme.form(new String[]{"Title", "When (yyyy-MM-dd HH:mm)", "Agenda"}, new JComponent[]{title, when, agendaBox}), BorderLayout.NORTH);
        form.add(fields, BorderLayout.CENTER);
        JPanel btns = Theme.plain();
        btns.setLayout(new GridLayout(2, 1, 0, 8));
        Theme.Btn schedule = new Theme.Btn("Schedule", true), now = new Theme.Btn("Meet now", false);
        btns.add(schedule); btns.add(now);
        form.add(btns, BorderLayout.SOUTH);

        JPanel right = Theme.card();
        right.setLayout(new BorderLayout(0, 12));
        right.add(Theme.h2("Meetings"), BorderLayout.NORTH);
        right.add(list, BorderLayout.CENTER);
        add(form, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);

        schedule.addActionListener(e -> create(false));
        now.addActionListener(e -> create(true));
    }

    private void create(boolean live) {
        String t = title.getText().trim();
        if (live && t.isEmpty()) t = "Quick sync";
        if (t.isEmpty()) { Theme.error(owner(), "Give the meeting a title."); return; }
        String start;
        try {
            start = live ? LocalDateTime.now().format(FMT) : LocalDateTime.parse(when.getText().trim().replace(' ', 'T')).format(FMT);
        } catch (Exception ex) { Theme.error(owner(), "Use the date format 2026-11-20 15:30."); return; }
        final String ft = t, fs = start, ag = agenda.getText().trim();
        Async.call(this, () -> Db.createMeeting(ctx.ws.id(), ctx.me.id(), ft, fs, ag, live), id -> {
            title.setText(""); agenda.setText("");
            refresh();
            if (live) Async.call(this, () -> Db.meeting(id), m -> new MeetingRoom(owner(), ctx, m).setVisible(true));
        });
    }

    @Override void refresh() {
        Async.quiet(() -> Db.meetings(ctx.ws.id()), ms -> {
            if (ms.equals(last)) return;
            last = ms;
            List<JComponent> rows = new ArrayList<>();
            for (Db.Meeting m : ms) rows.add(row(m));
            list.setRows(rows);
        });
    }

    private JComponent row(Db.Meeting m) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBackground(Theme.PANEL);
        p.setBorder(BorderFactory.createCompoundBorder(new LineBorder(Theme.LINE, 1, true), BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JPanel text = Theme.plain();
        text.setLayout(new GridLayout(3, 1));
        text.add(Theme.label(m.title(), Theme.BOLD, Theme.INK));
        text.add(Theme.label(m.start() + "  ·  host " + m.host() + "  ·  " + m.status().toLowerCase(), Theme.SMALL, Theme.MUTED));
        String ag = m.agenda().replace('\n', ' ');
        text.add(Theme.label(ag.isEmpty() ? "No agenda" : (ag.length() > 70 ? ag.substring(0, 70) + "..." : ag), Theme.SMALL, Theme.MUTED));
        boolean ended = m.status().equals("ENDED");
        Theme.Btn open = new Theme.Btn(ended ? "Notes" : "Join", !ended);
        open.addActionListener(e -> new MeetingRoom(owner(), ctx, m).setVisible(true));
        p.add(text, BorderLayout.CENTER);
        p.add(open, BorderLayout.EAST);
        return p;
    }
}
