package workspace;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Shared code editor. Live-reloads when a coworker saves; optimistic locking detects edit conflicts. */
final class CodePage extends Page {
    private final Theme.Paged list = new Theme.Paged(9);
    private final JTextArea editor = Theme.area(true);
    private final JLabel title = Theme.h2("No file selected");
    private final JLabel meta = Theme.muted(" ");
    private final JLabel banner = Theme.muted(" ");
    private final Theme.Btn save = new Theme.Btn("Save", true);
    private final Theme.Btn reload = new Theme.Btn("Reload", false);
    private final Theme.Btn addBtn = new Theme.Btn("New file", false);
    private List<Db.FileRec> files = List.of();
    private Db.FileRec current;
    private boolean dirty, loading;
    private String lastKey = "";

    CodePage(Ctx ctx) {
        super(ctx);
        JPanel left = Theme.card();
        left.setLayout(new BorderLayout(0, 12));
        left.setPreferredSize(new Dimension(250, 0));
        left.add(Theme.h2("Files"), BorderLayout.NORTH);
        left.add(list, BorderLayout.CENTER);
        left.add(addBtn, BorderLayout.SOUTH);

        JPanel right = Theme.card();
        right.setLayout(new BorderLayout(0, 10));
        JPanel head = Theme.plain();
        head.setLayout(new BorderLayout());
        JPanel t = Theme.plain();
        t.setLayout(new GridLayout(2, 1));
        t.add(title); t.add(meta);
        JPanel btns = Theme.plain();
        btns.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.add(reload); btns.add(save);
        head.add(t, BorderLayout.WEST); head.add(btns, BorderLayout.EAST);
        // scrollbars hidden on purpose: no visible scrolling UI, caret/mouse-wheel still reach every line
        JScrollPane sp = new JScrollPane(editor, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(new LineBorder(Theme.LINE, 1, true));
        right.add(head, BorderLayout.NORTH);
        right.add(sp, BorderLayout.CENTER);
        right.add(banner, BorderLayout.SOUTH);
        add(left, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);

        editor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { edited(); }
            public void removeUpdate(DocumentEvent e) { edited(); }
            public void changedUpdate(DocumentEvent e) { edited(); }
        });
        save.addActionListener(e -> save(false));
        reload.addActionListener(e -> reloadCurrent());
        addBtn.addActionListener(e -> newFile());
        updateLabels();
    }

    private void edited() { if (!loading) { dirty = true; updateLabels(); } }

    private void updateLabels() {
        title.setText(current == null ? "No file selected" : current.name());
        meta.setText(current == null ? " " : "v" + current.version() + "  ·  last edit by " + current.by() + "  ·  " + current.at() + (dirty ? "  ·  unsaved changes" : ""));
        save.setEnabled(current != null && ctx.canEdit());
        editor.setEditable(ctx.canEdit());
        if (!ctx.canEdit()) banner.setText("View-only access: ask the owner for Editor rights.");
    }

    @Override void refresh() {
        Async.quiet(() -> Db.files(ctx.ws.id()), fs -> {
            files = fs;
            if (current != null) {
                Db.FileRec latest = null;
                for (Db.FileRec f : fs) if (f.id() == current.id()) latest = f;
                if (latest != null && latest.version() > current.version()) {
                    if (!dirty) { load(latest, true); banner.setText(latest.by() + " updated this file. Showing the latest version."); }
                    else banner.setText(latest.by() + " saved v" + latest.version() + ". Saving will ask how to merge.");
                }
            } else if (!fs.isEmpty()) load(fs.get(0), false);
            buildList();
            updateLabels();
        });
    }

    private void buildList() {
        StringBuilder k = new StringBuilder(current == null ? "-" : "" + current.id());
        for (Db.FileRec f : files) k.append('|').append(f.id()).append(':').append(f.version());
        if (k.toString().equals(lastKey)) return;
        lastKey = k.toString();
        List<JComponent> rows = new ArrayList<>();
        for (Db.FileRec f : files)
            rows.add(Theme.listItem(f.name(), "v" + f.version() + "  ·  " + (f.by().isEmpty() ? "-" : f.by()),
                    current != null && f.id() == current.id(), () -> open(f)));
        list.setRows(rows);
    }

    private void open(Db.FileRec f) {
        if (dirty && JOptionPane.showConfirmDialog(owner(), "Discard unsaved changes?", "CoSpace", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        load(f, false);
        banner.setText(" ");
        lastKey = "";
        buildList();
    }

    private void load(Db.FileRec f, boolean keepCaret) {
        int pos = keepCaret ? editor.getCaretPosition() : 0;
        loading = true;
        editor.setText(f.content());
        editor.setCaretPosition(Math.min(pos, editor.getDocument().getLength()));
        loading = false;
        current = f;
        dirty = false;
        updateLabels();
    }

    private void reloadCurrent() {
        if (current == null) return;
        for (Db.FileRec f : files) if (f.id() == current.id()) { load(f, false); banner.setText(" "); return; }
    }

    private void save(boolean force) {
        if (current == null || !ctx.canEdit()) return;
        final Db.FileRec f = current;
        final String text = editor.getText();
        Async.call(this, () -> Db.saveFile(f.id(), text, f.version(), ctx.me.id(), force), v -> {
            if (v < 0) { resolveConflict(); return; }
            current = new Db.FileRec(f.id(), f.name(), text, v, ctx.me.name(), "just now");
            dirty = false;
            banner.setText("Saved as v" + v + ".");
            lastKey = "";
            updateLabels();
            refresh();
        });
    }

    private void resolveConflict() {
        String[] opts = {"Overwrite theirs", "Load their version", "Cancel"};
        int c = JOptionPane.showOptionDialog(owner(), "Someone saved this file after you opened it.", "Edit conflict",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, opts[1]);
        if (c == 0) save(true);
        else if (c == 1) { dirty = false; refresh(); }
    }

    private void newFile() {
        if (!ctx.canEdit()) { Theme.error(owner(), "View-only access."); return; }
        String name = JOptionPane.showInputDialog(owner(), "File name (for example Utils.java)");
        if (name == null || name.isBlank()) return;
        Async.exec(this, () -> Db.createFile(ctx.ws.id(), ctx.me.id(), name.trim()), this::refresh);
    }
}
