package workspace;

import javax.swing.*;
import java.awt.Component;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * The Event Dispatch Thread (EDT) must never block on the database.
 * Every JDBC call goes through a SwingWorker: doInBackground() runs off the EDT, done() runs back on the EDT.
 */
public final class Async {
    private Async() {}
    public interface Job { void run() throws Exception; }

    /** Run work in background, deliver result on the EDT, show an error dialog on failure. */
    public static <T> void call(Component owner, Callable<T> work, Consumer<T> ui) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return work.call(); }
            @Override protected void done() {
                try { ui.accept(get()); }
                catch (ExecutionException e) { Theme.error(owner, message(e)); }
                catch (InterruptedException ignored) { }
            }
        }.execute();
    }

    public static void exec(Component owner, Job job, Runnable ui) {
        call(owner, () -> { job.run(); return Boolean.TRUE; }, ok -> { if (ui != null) ui.run(); });
    }

    /** For polling: failures are ignored silently (next tick retries). */
    public static <T> void quiet(Callable<T> work, Consumer<T> ui) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return work.call(); }
            @Override protected void done() { try { ui.accept(get()); } catch (Exception ignored) { } }
        }.execute();
    }

    private static String message(ExecutionException e) {
        Throwable c = e.getCause() != null ? e.getCause() : e;
        return c instanceof IllegalArgumentException ? c.getMessage() : "Something went wrong: " + c.getMessage();
    }
}
