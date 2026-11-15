package workspace;

import javax.swing.*;
import java.awt.*;

/** Team chat. Shows 12 messages per page with Older/Newer buttons instead of a scrollbar. */
final class ChatPage extends Page {
    private static final int PER = 12;
    private final JTextArea view = Theme.area(false);
    private final JTextField input = Theme.field(30);
    private final Theme.Btn older = new Theme.Btn("Older", false).compact();
    private final Theme.Btn newer = new Theme.Btn("Newer", false).compact();
    private final JLabel info = Theme.muted(" ");
    private int page;

    ChatPage(Ctx ctx) {
        super(ctx);
        view.setEditable(false);
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 12));
        JPanel head = Theme.plain();
        head.setLayout(new BorderLayout());
        JPanel nav = Theme.plain();
        nav.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        nav.add(info); nav.add(older); nav.add(newer);
        head.add(Theme.h2("Team chat"), BorderLayout.WEST);
        head.add(nav, BorderLayout.EAST);
        JPanel send = Theme.plain();
        send.setLayout(new BorderLayout(10, 0));
        Theme.Btn sendBtn = new Theme.Btn("Send", true);
        send.add(input, BorderLayout.CENTER);
        send.add(sendBtn, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        card.add(view, BorderLayout.CENTER);
        card.add(send, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);

        older.addActionListener(e -> { page++; refresh(); });
        newer.addActionListener(e -> { page--; refresh(); });
        Runnable go = () -> {
            String t = input.getText().trim();
            if (t.isEmpty()) return;
            input.setText("");
            page = 0;
            Async.exec(this, () -> Db.post(ctx.ws.id(), ctx.me.id(), t), this::refresh);
        };
        sendBtn.addActionListener(e -> go.run());
        input.addActionListener(e -> go.run());
    }

    @Override void refresh() {
        final int p = page;
        Async.quiet(() -> Db.messages(ctx.ws.id(), PER, p * PER), s -> {
            StringBuilder sb = new StringBuilder();
            for (Db.Msg m : s.items())
                sb.append(m.at().substring(11, 16)).append("   ").append(m.user()).append(":  ").append(m.body()).append("\n\n");
            String text = s.items().isEmpty() ? "No messages yet. Say hello to your team." : sb.toString();
            if (!text.equals(view.getText())) view.setText(text);
            older.setEnabled((p + 1) * PER < s.total());
            newer.setEnabled(p > 0);
            info.setText(s.total() + " messages");
        });
    }
}
