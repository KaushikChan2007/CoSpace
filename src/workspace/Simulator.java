package workspace;

import javax.swing.SwingWorker;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/** A simulated coworker: a background SwingWorker that edits files, chats and moves tasks. publish()/process() hand status to the EDT. */
final class Simulator extends SwingWorker<Void, String> {
    private static final String[] CHAT = {
            "Pushed my changes, can someone review?", "I'll take the next task on the board.",
            "Found a small bug in the last save, fixing it.", "Meeting at 4 works for me.",
            "Added notes to the file, please check.", "Looks good to me, merging my part."};
    private static final String[] NOTES = {
            "TODO: validate user input", "refactored helper method", "added null check", "see team chat for details", "cleaned up formatting"};
    private final int ws;
    private final Db.User bot;
    private final Consumer<String> status;
    private final Random rnd = new Random();

    Simulator(int ws, Db.User bot, Consumer<String> status) { this.ws = ws; this.bot = bot; this.status = status; }

    @Override protected Void doInBackground() throws Exception {
        while (!isCancelled()) {
            try {
                Db.heartbeat(bot.id(), ws);
                step();
                for (int i = 0; i < 4 + rnd.nextInt(3) && !isCancelled(); i++) { Thread.sleep(1000); Db.heartbeat(bot.id(), ws); }
            } catch (InterruptedException e) { break; }
            catch (Exception e) { Thread.sleep(2000); }
        }
        return null;
    }

    private void step() throws Exception {
        switch (rnd.nextInt(4)) {
            case 0 -> { Db.post(ws, bot.id(), CHAT[rnd.nextInt(CHAT.length)]); publish("is chatting"); }
            case 1 -> {
                List<Db.FileRec> fs = Db.files(ws);
                if (fs.isEmpty()) return;
                Db.FileRec f = fs.get(rnd.nextInt(fs.size()));
                if (f.content().length() > 1500) return;
                publish("is editing " + f.name());
                Thread.sleep(1500);
                String add = (f.content().endsWith("\n") || f.content().isEmpty() ? "" : "\n") + "// Aarav: " + NOTES[rnd.nextInt(NOTES.length)] + "\n";
                Db.saveFile(f.id(), f.content() + add, f.version(), bot.id(), false);   // conflict => -1 => ignored
            }
            case 2 -> { Db.addTask(ws, bot.id(), "Review " + (1 + rnd.nextInt(20)) + ": " + NOTES[rnd.nextInt(NOTES.length)]); publish("added a task"); }
            default -> {
                List<Db.Task> ts = Db.tasks(ws);
                if (ts.isEmpty()) return;
                Db.Task t = ts.get(rnd.nextInt(ts.size()));
                String next = t.status().equals("TODO") ? "DOING" : "DONE";
                if (!t.status().equals("DONE")) Db.moveTask(ws, bot.id(), t.id(), next, t.title());
                publish("moved a task");
            }
        }
    }

    @Override protected void process(List<String> chunks) { status.accept(chunks.get(chunks.size() - 1)); }
}
