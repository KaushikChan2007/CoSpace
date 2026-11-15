package workspace;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Audit trail of everything that happened in the workspace (latest 60 events, 10 per page). */
final class ActivityPage extends Page {
    private final Theme.Paged list = new Theme.Paged(10);
    private List<Db.Event> last;

    ActivityPage(Ctx ctx) {
        super(ctx);
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 12));
        card.add(Theme.h2("Activity"), BorderLayout.NORTH);
        card.add(list, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
    }

    @Override void refresh() {
        Async.quiet(() -> Db.events(ctx.ws.id(), 60, 0), s -> {
            if (s.items().equals(last)) return;
            last = s.items();
            List<JComponent> rows = new ArrayList<>();
            for (Db.Event e : s.items()) {
                JPanel p = Theme.plain();
                p.setLayout(new BorderLayout(12, 0));
                p.add(Theme.label(e.at().substring(5, 16), Theme.SMALL, Theme.MUTED), BorderLayout.WEST);
                p.add(Theme.label(e.user() + "  " + e.text(), Theme.UI, Theme.INK), BorderLayout.CENTER);
                rows.add(p);
            }
            list.setRows(rows);
        });
    }
}
