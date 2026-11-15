package workspace;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/** Simulated meeting room: live timer, who is online, and notes shared through the database. */
final class MeetingRoom extends JDialog {
    private final Ctx ctx;
    private final Db.Meeting meeting;
    private final JTextArea notes = Theme.area(false);
    private final JLabel clock = Theme.h1("00:00");
    private final JLabel people = Theme.label(" ", Theme.UI, Theme.INK);
    private final javax.swing.Timer timer;
    private boolean dirty, loading;
    private int seconds;

    MeetingRoom(Window owner, Ctx ctx, Db.Meeting m) {
        super(owner, m.title(), ModalityType.MODELESS);
        this.ctx = ctx;
        this.meeting = m;
        boolean ended = m.status().equals("ENDED");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(860, 560);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(Theme.BG);
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel left = Theme.card();
        left.setLayout(new BorderLayout(0, 14));
        left.setPreferredSize(new Dimension(280, 0));
        JPanel info = Theme.plain();
        info.setLayout(new GridLayout(0, 1, 0, 6));
        info.add(Theme.h2(m.title()));
        info.add(Theme.label(m.start() + "  ·  host " + m.host(), Theme.SMALL, Theme.MUTED));
        info.add(ended ? Theme.muted("Meeting ended") : clock);
        info.add(Theme.label("Agenda", Theme.SMALL, Theme.MUTED));
        JTextArea ag = Theme.area(false);
        ag.setText(m.agenda().isEmpty() ? "No agenda" : m.agenda());
        ag.setEditable(false);
        ag.setBackground(Theme.BG);
        left.add(info, BorderLayout.NORTH);
        left.add(ag, BorderLayout.CENTER);
        JPanel bottom = Theme.plain();
        bottom.setLayout(new BorderLayout(0, 10));
        bottom.add(people, BorderLayout.NORTH);
        JPanel btns = Theme.plain();
        btns.setLayout(new GridLayout(0, 1, 0, 8));
        Theme.Btn leave = new Theme.Btn("Leave", false);
        btns.add(leave);
        if (!ended && (ctx.isOwner() || m.host().equals(ctx.me.name()))) {
            Theme.Btn end = new Theme.Btn("End meeting", true);
            end.addActionListener(e -> Async.exec(this, () -> Db.setMeetingStatus(m.id(), "ENDED"), this::dispose));
            btns.add(end);
        }
        bottom.add(btns, BorderLayout.SOUTH);
        left.add(bottom, BorderLayout.SOUTH);

        JPanel right = Theme.card();
        right.setLayout(new BorderLayout(0, 10));
        JPanel head = Theme.plain();
        head.setLayout(new BorderLayout());
        Theme.Btn saveBtn = new Theme.Btn("Save notes", true);
        head.add(Theme.h2("Shared notes"), BorderLayout.WEST);
        head.add(saveBtn, BorderLayout.EAST);
        JScrollPane sp = new JScrollPane(notes, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(new LineBorder(Theme.LINE, 1, true));
        right.add(head, BorderLayout.NORTH);
        right.add(sp, BorderLayout.CENTER);
        root.add(left, BorderLayout.WEST);
        root.add(right, BorderLayout.CENTER);
        setContentPane(root);

        loading = true; notes.setText(m.notes()); loading = false;
        notes.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { if (!loading) dirty = true; }
            public void removeUpdate(DocumentEvent e) { if (!loading) dirty = true; }
            public void changedUpdate(DocumentEvent e) { if (!loading) dirty = true; }
        });
        saveBtn.addActionListener(e -> {
            String t = notes.getText();
            Async.exec(this, () -> Db.saveNotes(m.id(), t), () -> dirty = false);
        });
        leave.addActionListener(e -> dispose());

        timer = new javax.swing.Timer(1000, e -> tick());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { timer.stop(); }
        });
        if (!ended) {
            if (m.status().equals("SCHEDULED")) Async.exec(this, () -> Db.setMeetingStatus(m.id(), "LIVE"), null);
            timer.start();
            sync();
        }
    }

    private void tick() {
        seconds++;
        clock.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
        if (seconds % 3 == 0) sync();
    }

    private void sync() {
        record Snap(Db.Meeting m, List<Db.Member> members) {}
        Async.quiet(() -> new Snap(Db.meeting(meeting.id()), Db.members(ctx.ws.id())), s -> {
            if (!dirty && !s.m().notes().equals(notes.getText())) {
                int pos = notes.getCaretPosition();
                loading = true; notes.setText(s.m().notes());
                notes.setCaretPosition(Math.min(pos, notes.getDocument().getLength())); loading = false;
            }
            StringBuilder sb = new StringBuilder("<html>In the room:<br>");
            for (Db.Member mem : s.members()) if (mem.online()) sb.append("&nbsp;&nbsp;• ").append(mem.name()).append("<br>");
            people.setText(sb.append("</html>").toString());
            if (s.m().status().equals("ENDED")) { timer.stop(); clock.setText("Ended"); }
        });
    }
}
