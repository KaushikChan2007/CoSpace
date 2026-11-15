package workspace;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One open workspace: left navigation, header with presence, and a CardLayout of pages. */
final class WorkspacePanel extends JPanel {
    private final Ctx ctx;
    private final Runnable back;
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final JLabel online = Theme.muted(" ");
    private final JLabel simStatus = Theme.muted(" ");
    private final Theme.Btn sim = new Theme.Btn("Simulate a coworker", false);
    private final javax.swing.Timer poll = new javax.swing.Timer(2500, e -> tick());
    private Simulator simulator;
    private String current = "Code";

    WorkspacePanel(Db.User me, Db.Workspace ws, Runnable back) {
        super(new BorderLayout());
        this.ctx = new Ctx(me, ws);
        this.back = back;
        setBackground(Theme.BG);
        pages.put("Code", new CodePage(ctx));
        pages.put("Tasks", new TasksPage(ctx));
        pages.put("Chat", new ChatPage(ctx));
        pages.put("Meetings", new MeetingsPage(ctx));
        pages.put("Team", new TeamPage(ctx));
        pages.put("Activity", new ActivityPage(ctx));
        center.setOpaque(false);
        for (Map.Entry<String, Page> e : pages.entrySet()) center.add(e.getValue(), e.getKey());

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(Theme.PANEL);
        side.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.LINE), new EmptyBorder(16, 12, 16, 12)));
        side.setPreferredSize(new Dimension(190, 0));
        ButtonGroup group = new ButtonGroup();
        for (String name : pages.keySet()) {
            Theme.Nav b = new Theme.Nav(name);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            b.setAlignmentX(Component.LEFT_ALIGNMENT);
            b.setSelected(name.equals(current));
            b.addActionListener(e -> show(name));
            group.add(b);
            side.add(b);
            side.add(Box.createVerticalStrut(4));
        }

        JPanel head = new JPanel(new BorderLayout(16, 0));
        head.setBackground(Theme.PANEL);
        head.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE), new EmptyBorder(14, 20, 14, 20)));
        Theme.Btn backBtn = new Theme.Btn("Workspaces", false);
        backBtn.addActionListener(e -> back.run());
        JPanel titles = Theme.plain();
        titles.setLayout(new GridLayout(2, 1));
        titles.add(Theme.h1(ws.name()));
        titles.add(Theme.label(ws.template() + " recipe  ·  you are " + ws.role().toLowerCase(), Theme.SMALL, Theme.MUTED));
        JPanel right = Theme.plain();
        right.setLayout(new FlowLayout(FlowLayout.RIGHT, 14, 8));
        right.add(simStatus);
        right.add(online);
        right.add(sim);
        head.add(backBtn, BorderLayout.WEST);
        head.add(titles, BorderLayout.CENTER);
        head.add(right, BorderLayout.EAST);

        add(head, BorderLayout.NORTH);
        add(side, BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        sim.addActionListener(e -> toggleSim());
    }

    void start() { cards.show(center, current); pages.get(current).refresh(); tick(); poll.start(); }

    void stop() {
        poll.stop();
        if (simulator != null) simulator.cancel(true);
    }

    private void show(String name) { current = name; cards.show(center, name); pages.get(name).refresh(); }

    private void tick() {
        record Snap(String role, List<Db.Member> members) {}
        Async.quiet(() -> {
            Db.heartbeat(ctx.me.id(), ctx.ws.id());
            return new Snap(Db.role(ctx.ws.id(), ctx.me.id()), Db.members(ctx.ws.id()));
        }, s -> {
            if (s.role() == null) { Theme.error(this, "You were removed from this workspace."); back.run(); return; }
            ctx.role = s.role();
            StringBuilder sb = new StringBuilder("Online: ");
            int n = 0;
            for (Db.Member m : s.members()) if (m.online()) { sb.append(n++ > 0 ? ", " : "").append(m.name()); }
            online.setText(n == 0 ? "Online: nobody" : sb.toString());
            pages.get(current).refresh();
        });
    }

    private void toggleSim() {
        if (simulator != null) {
            simulator.cancel(true);
            simulator = null;
            sim.setText("Simulate a coworker");
            simStatus.setText(" ");
            return;
        }
        Async.call(this, () -> Db.ensureBot(ctx.ws.id()), bot -> {
            simulator = new Simulator(ctx.ws.id(), bot, t -> simStatus.setText("Aarav " + t));
            simulator.execute();
            sim.setText("Stop simulation");
            simStatus.setText("Aarav joined");
        });
    }
}
