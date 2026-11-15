package workspace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.List;

/**
 * JDBC data-access layer. Every method opens its own short-lived Connection with try-with-resources,
 * so it is safe to call from any SwingWorker thread. Swap the URL to use MySQL/PostgreSQL instead.
 */
public final class Db {
    private Db() {}

    public static final String PATH = System.getProperty("cw.db",
            System.getProperty("user.home") + java.io.File.separator + "cospace.db");
    private static final String URL = "jdbc:sqlite:" + PATH;

    public static final String[] TEMPLATES = {"Blank", "Java Project", "Research Project", "Web App"};
    public static final String[] TEMPLATE_INFO = {
            "One empty notes file.",
            "Main.java, README, 3 starter tasks.",
            "Abstract, notes, references, 3 starter tasks.",
            "index.html, style.css, app.js, 3 starter tasks."};

    public record User(int id, String username, String name, String email) {}
    public record Workspace(int id, String name, String description, String template, int ownerId, String role) {}
    public record Member(int userId, String username, String name, String role, boolean online) {}
    public record Invite(int id, String workspace, String from, String role) {}
    public record FileRec(int id, String name, String content, int version, String by, String at) {}
    public record Task(int id, String title, String status, String assignee) {}
    public record Msg(String user, String body, String at) {}
    public record Meeting(int id, String title, String start, String agenda, String notes, String host, String status) {}
    public record Event(String user, String text, String at) {}
    public record Slice<T>(List<T> items, int total) {}

    static {
        try { Class.forName("org.sqlite.JDBC"); }
        catch (ClassNotFoundException e) { throw new ExceptionInInitializerError(e); }
    }

    // ---------- plumbing ----------
    private interface Row<T> { T map(ResultSet rs) throws SQLException; }

    private static Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(URL);
        try (Statement s = c.createStatement()) { s.execute("PRAGMA busy_timeout=5000"); }
        return c;
    }
    private static void bind(PreparedStatement ps, Object[] p) throws SQLException {
        for (int i = 0; i < p.length; i++) ps.setObject(i + 1, p[i]);
    }
    private static int update(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) { bind(ps, p); return ps.executeUpdate(); }
    }
    private static <T> List<T> query(Connection c, String sql, Row<T> m, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(m.map(rs));
                return out;
            }
        }
    }
    private static int count(Connection c, String sql, Object... p) throws SQLException {
        return query(c, sql, rs -> rs.getInt(1), p).get(0);
    }
    private static int lastId(Connection c) throws SQLException { return count(c, "SELECT last_insert_rowid()"); }
    private static void log(Connection c, int ws, int uid, String text) throws SQLException {
        update(c, "INSERT INTO events(ws_id,user_id,text) VALUES(?,?,?)", ws, uid, text);
    }
    private static User user(ResultSet rs) throws SQLException {
        return new User(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4));
    }

    public static void init() throws SQLException {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, name TEXT NOT NULL, email TEXT UNIQUE NOT NULL, pass TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS workspaces(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT, template TEXT, owner_id INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS members(ws_id INTEGER, user_id INTEGER, role TEXT NOT NULL, PRIMARY KEY(ws_id,user_id))");
            s.execute("CREATE TABLE IF NOT EXISTS invites(id INTEGER PRIMARY KEY AUTOINCREMENT, ws_id INTEGER, from_id INTEGER, ident TEXT, role TEXT, status TEXT DEFAULT 'PENDING')");
            s.execute("CREATE TABLE IF NOT EXISTS files(id INTEGER PRIMARY KEY AUTOINCREMENT, ws_id INTEGER, name TEXT, content TEXT, version INTEGER DEFAULT 1, updated_by INTEGER, updated_at TEXT DEFAULT (datetime('now','localtime')), UNIQUE(ws_id,name))");
            s.execute("CREATE TABLE IF NOT EXISTS tasks(id INTEGER PRIMARY KEY AUTOINCREMENT, ws_id INTEGER, title TEXT, status TEXT DEFAULT 'TODO', assignee INTEGER)");
            s.execute("CREATE TABLE IF NOT EXISTS messages(id INTEGER PRIMARY KEY AUTOINCREMENT, ws_id INTEGER, user_id INTEGER, body TEXT, at TEXT DEFAULT (datetime('now','localtime')))");
            s.execute("CREATE TABLE IF NOT EXISTS meetings(id INTEGER PRIMARY KEY AUTOINCREMENT, ws_id INTEGER, title TEXT, starts_at TEXT, agenda TEXT, notes TEXT DEFAULT '', host_id INTEGER, status TEXT DEFAULT 'SCHEDULED')");
            s.execute("CREATE TABLE IF NOT EXISTS events(id INTEGER PRIMARY KEY AUTOINCREMENT, ws_id INTEGER, user_id INTEGER, text TEXT, at TEXT DEFAULT (datetime('now','localtime')))");
            s.execute("CREATE TABLE IF NOT EXISTS presence(user_id INTEGER, ws_id INTEGER, seen INTEGER, PRIMARY KEY(user_id,ws_id))");
        }
    }

    // ---------- accounts ----------
    private static String hash(String pw, byte[] salt) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt);
        return HexFormat.of().formatHex(md.digest(pw.getBytes(StandardCharsets.UTF_8)));
    }
    private static String newPass(String pw) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return HexFormat.of().formatHex(salt) + ":" + hash(pw, salt);
    }

    public static User register(String username, String name, String email, String pw) throws Exception {
        try (Connection c = open()) {
            update(c, "INSERT INTO users(username,name,email,pass) VALUES(?,?,?,?)",
                    username.toLowerCase(), name, email.toLowerCase(), newPass(pw));
            return query(c, "SELECT id,username,name,email FROM users WHERE username=?", Db::user, username.toLowerCase()).get(0);
        } catch (SQLException e) {
            if (String.valueOf(e.getMessage()).contains("UNIQUE"))
                throw new IllegalArgumentException("That username or email is already registered.");
            throw e;
        }
    }

    public static User login(String username, String pw) throws Exception {
        try (Connection c = open()) {
            List<String[]> r = query(c, "SELECT id,username,name,email,pass FROM users WHERE username=?",
                    rs -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)},
                    username.toLowerCase());
            if (r.isEmpty()) return null;
            String[] u = r.get(0), sp = u[4].split(":");
            if (!hash(pw, HexFormat.of().parseHex(sp[0])).equals(sp[1])) return null;
            return new User(Integer.parseInt(u[0]), u[1], u[2], u[3]);
        }
    }

    // ---------- workspaces ----------
    public static List<Workspace> workspaces(int uid) throws SQLException {
        try (Connection c = open()) {
            return query(c, "SELECT w.id,w.name,w.description,w.template,w.owner_id,m.role FROM workspaces w JOIN members m ON m.ws_id=w.id WHERE m.user_id=? ORDER BY w.id DESC",
                    rs -> new Workspace(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getString(6)), uid);
        }
    }

    public static Workspace createWorkspace(int uid, String name, String desc, String template) throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);                       // one transaction: workspace + owner + template content
            try {
                update(c, "INSERT INTO workspaces(name,description,template,owner_id) VALUES(?,?,?,?)", name, desc, template, uid);
                int ws = lastId(c);
                update(c, "INSERT INTO members(ws_id,user_id,role) VALUES(?,?,'OWNER')", ws, uid);
                seed(c, ws, uid, template);
                log(c, ws, uid, "created the workspace from the \"" + template + "\" recipe");
                c.commit();
                return new Workspace(ws, name, desc, template, uid, "OWNER");
            } catch (SQLException e) { c.rollback(); throw e; }
        }
    }

    /** "Recipes": ready-made starting content for a new workspace. */
    private static void seed(Connection c, int ws, int uid, String t) throws SQLException {
        String[][] files;
        String[] tasks;
        switch (t) {
            case "Java Project" -> {
                files = new String[][]{
                        {"Main.java", "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello, team!\");\n    }\n}\n"},
                        {"README.md", "# Project\n\nDescribe the goal and how to run it.\n"}};
                tasks = new String[]{"Set up package structure", "Write core classes", "Add tests"};
            }
            case "Research Project" -> {
                files = new String[][]{
                        {"Abstract.md", "# Abstract\n\nProblem, method, expected result.\n"},
                        {"Notes.md", "# Notes\n\n- \n"},
                        {"References.md", "# References\n\n1. \n"}};
                tasks = new String[]{"Literature survey", "Define methodology", "Write first draft"};
            }
            case "Web App" -> {
                files = new String[][]{
                        {"index.html", "<!DOCTYPE html>\n<html>\n<head><link rel=\"stylesheet\" href=\"style.css\"></head>\n<body>\n  <h1>Hello</h1>\n  <script src=\"app.js\"></script>\n</body>\n</html>\n"},
                        {"style.css", "body { font-family: sans-serif; }\n"},
                        {"app.js", "console.log('ready');\n"}};
                tasks = new String[]{"Design the layout", "Build the pages", "Test in browsers"};
            }
            default -> {
                files = new String[][]{{"notes.txt", "Start writing here.\n"}};
                tasks = new String[]{};
            }
        }
        for (String[] f : files)
            update(c, "INSERT INTO files(ws_id,name,content,updated_by) VALUES(?,?,?,?)", ws, f[0], f[1], uid);
        for (String task : tasks)
            update(c, "INSERT INTO tasks(ws_id,title,status,assignee) VALUES(?,?,'TODO',?)", ws, task, uid);
    }

    // ---------- team & invitations ----------
    public static String role(int ws, int uid) throws SQLException {
        try (Connection c = open()) {
            List<String> r = query(c, "SELECT role FROM members WHERE ws_id=? AND user_id=?", rs -> rs.getString(1), ws, uid);
            return r.isEmpty() ? null : r.get(0);
        }
    }

    public static void heartbeat(int uid, int ws) throws SQLException {
        try (Connection c = open()) {
            update(c, "INSERT OR REPLACE INTO presence(user_id,ws_id,seen) VALUES(?,?,?)", uid, ws, System.currentTimeMillis());
        }
    }

    public static List<Member> members(int ws) throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection c = open()) {
            return query(c, "SELECT u.id,u.username,u.name,m.role,COALESCE(p.seen,0) FROM members m JOIN users u ON u.id=m.user_id "
                            + "LEFT JOIN presence p ON p.user_id=u.id AND p.ws_id=m.ws_id WHERE m.ws_id=? "
                            + "ORDER BY CASE m.role WHEN 'OWNER' THEN 0 ELSE 1 END, u.name",
                    rs -> new Member(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5) > now - 8000), ws);
        }
    }

    public static void changeRole(int ws, int uid, String role) throws SQLException {
        try (Connection c = open()) {
            update(c, "UPDATE members SET role=? WHERE ws_id=? AND user_id=? AND role<>'OWNER'", role, ws, uid);
            log(c, ws, uid, "role changed to " + role);
        }
    }

    public static void removeMember(int ws, int uid) throws SQLException {
        try (Connection c = open()) {
            update(c, "DELETE FROM members WHERE ws_id=? AND user_id=? AND role<>'OWNER'", ws, uid);
            log(c, ws, uid, "was removed from the workspace");
        }
    }

    /** Invite by username or email. The invitee may not even have an account yet. */
    public static void invite(int ws, int fromId, String ident, String role) throws SQLException {
        String id = ident.trim().toLowerCase();
        if (id.isEmpty()) throw new IllegalArgumentException("Enter a username or an email.");
        try (Connection c = open()) {
            if (!query(c, "SELECT 1 FROM members m JOIN users u ON u.id=m.user_id WHERE m.ws_id=? AND (u.username=? OR u.email=?)", rs -> 1, ws, id, id).isEmpty())
                throw new IllegalArgumentException("Already a member of this workspace.");
            if (!query(c, "SELECT 1 FROM invites WHERE ws_id=? AND ident=? AND status='PENDING'", rs -> 1, ws, id).isEmpty())
                throw new IllegalArgumentException("An invitation is already pending for that person.");
            update(c, "INSERT INTO invites(ws_id,from_id,ident,role) VALUES(?,?,?,?)", ws, fromId, id, role);
            log(c, ws, fromId, "invited " + id + " as " + role);
        }
    }

    public static List<Invite> pendingInvites(User u) throws SQLException {
        try (Connection c = open()) {
            return query(c, "SELECT i.id,w.name,f.name,i.role FROM invites i JOIN workspaces w ON w.id=i.ws_id JOIN users f ON f.id=i.from_id "
                            + "WHERE i.status='PENDING' AND (i.ident=? OR i.ident=?) ORDER BY i.id DESC",
                    rs -> new Invite(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)), u.username(), u.email());
        }
    }

    public static void respond(int inviteId, User u, boolean accept) throws SQLException {
        try (Connection c = open()) {
            List<String[]> r = query(c, "SELECT ws_id,role FROM invites WHERE id=? AND status='PENDING'",
                    rs -> new String[]{rs.getString(1), rs.getString(2)}, inviteId);
            if (r.isEmpty()) return;
            update(c, "UPDATE invites SET status=? WHERE id=?", accept ? "ACCEPTED" : "DECLINED", inviteId);
            if (accept) {
                int ws = Integer.parseInt(r.get(0)[0]);
                update(c, "INSERT OR IGNORE INTO members(ws_id,user_id,role) VALUES(?,?,?)", ws, u.id(), r.get(0)[1]);
                log(c, ws, u.id(), "joined the workspace");
            }
        }
    }

    // ---------- shared files (optimistic locking via version column) ----------
    public static List<FileRec> files(int ws) throws SQLException {
        try (Connection c = open()) {
            return query(c, "SELECT f.id,f.name,f.content,f.version,COALESCE((SELECT name FROM users WHERE id=f.updated_by),''),f.updated_at FROM files f WHERE f.ws_id=? ORDER BY f.name",
                    rs -> new FileRec(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5), rs.getString(6)), ws);
        }
    }

    public static void createFile(int ws, int uid, String name) throws SQLException {
        try (Connection c = open()) {
            update(c, "INSERT INTO files(ws_id,name,content,updated_by) VALUES(?,?,'',?)", ws, name, uid);
            log(c, ws, uid, "created " + name);
        } catch (SQLException e) {
            if (String.valueOf(e.getMessage()).contains("UNIQUE")) throw new IllegalArgumentException("A file with that name already exists.");
            throw e;
        }
    }

    /** Returns the new version, or -1 if someone else saved first (edit conflict). */
    public static int saveFile(int fileId, String content, int expected, int uid, boolean force) throws SQLException {
        try (Connection c = open()) {
            String sql = "UPDATE files SET content=?, version=version+1, updated_by=?, updated_at=datetime('now','localtime') WHERE id=?";
            int n = force ? update(c, sql, content, uid, fileId) : update(c, sql + " AND version=?", content, uid, fileId, expected);
            if (n == 0) return -1;
            Object[] f = query(c, "SELECT ws_id,name,version FROM files WHERE id=?",
                    rs -> new Object[]{rs.getInt(1), rs.getString(2), rs.getInt(3)}, fileId).get(0);
            log(c, (Integer) f[0], uid, "saved " + f[1] + " (v" + f[2] + ")");
            return (Integer) f[2];
        }
    }

    // ---------- tasks ----------
    public static List<Task> tasks(int ws) throws SQLException {
        try (Connection c = open()) {
            return query(c, "SELECT t.id,t.title,t.status,COALESCE(u.name,'') FROM tasks t LEFT JOIN users u ON u.id=t.assignee WHERE t.ws_id=? ORDER BY t.id",
                    rs -> new Task(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)), ws);
        }
    }
    public static void addTask(int ws, int uid, String title) throws SQLException {
        try (Connection c = open()) {
            update(c, "INSERT INTO tasks(ws_id,title,assignee) VALUES(?,?,?)", ws, title, uid);
            log(c, ws, uid, "added task \"" + title + "\"");
        }
    }
    public static void moveTask(int ws, int uid, int taskId, String status, String title) throws SQLException {
        try (Connection c = open()) {
            update(c, "UPDATE tasks SET status=?, assignee=? WHERE id=?", status, uid, taskId);
            log(c, ws, uid, "moved \"" + title + "\" to " + status);
        }
    }

    // ---------- chat ----------
    public static Slice<Msg> messages(int ws, int limit, int offset) throws SQLException {
        try (Connection c = open()) {
            int total = count(c, "SELECT COUNT(*) FROM messages WHERE ws_id=?", ws);
            List<Msg> l = query(c, "SELECT u.name,m.body,m.at FROM messages m JOIN users u ON u.id=m.user_id WHERE m.ws_id=? ORDER BY m.id DESC LIMIT ? OFFSET ?",
                    rs -> new Msg(rs.getString(1), rs.getString(2), rs.getString(3)), ws, limit, offset);
            Collections.reverse(l);
            return new Slice<>(l, total);
        }
    }
    public static void post(int ws, int uid, String body) throws SQLException {
        try (Connection c = open()) { update(c, "INSERT INTO messages(ws_id,user_id,body) VALUES(?,?,?)", ws, uid, body); }
    }

    // ---------- meetings ----------
    private static Meeting meeting(ResultSet rs) throws SQLException {
        return new Meeting(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7));
    }
    private static final String MEETING_SQL = "SELECT m.id,m.title,m.starts_at,COALESCE(m.agenda,''),COALESCE(m.notes,''),u.name,m.status FROM meetings m JOIN users u ON u.id=m.host_id ";

    public static List<Meeting> meetings(int ws) throws SQLException {
        try (Connection c = open()) {
            return query(c, MEETING_SQL + "WHERE m.ws_id=? ORDER BY CASE m.status WHEN 'ENDED' THEN 1 ELSE 0 END, m.starts_at", Db::meeting, ws);
        }
    }
    public static Meeting meeting(int id) throws SQLException {
        try (Connection c = open()) { return query(c, MEETING_SQL + "WHERE m.id=?", Db::meeting, id).get(0); }
    }
    public static int createMeeting(int ws, int uid, String title, String startsAt, String agenda, boolean live) throws SQLException {
        try (Connection c = open()) {
            update(c, "INSERT INTO meetings(ws_id,title,starts_at,agenda,host_id,status) VALUES(?,?,?,?,?,?)",
                    ws, title, startsAt, agenda, uid, live ? "LIVE" : "SCHEDULED");
            int id = lastId(c);
            log(c, ws, uid, (live ? "started meeting \"" : "scheduled meeting \"") + title + "\"");
            return id;
        }
    }
    public static void saveNotes(int id, String notes) throws SQLException {
        try (Connection c = open()) { update(c, "UPDATE meetings SET notes=? WHERE id=?", notes, id); }
    }
    public static void setMeetingStatus(int id, String status) throws SQLException {
        try (Connection c = open()) { update(c, "UPDATE meetings SET status=? WHERE id=?", status, id); }
    }

    // ---------- activity ----------
    public static Slice<Event> events(int ws, int limit, int offset) throws SQLException {
        try (Connection c = open()) {
            return new Slice<>(query(c, "SELECT u.name,e.text,e.at FROM events e JOIN users u ON u.id=e.user_id WHERE e.ws_id=? ORDER BY e.id DESC LIMIT ? OFFSET ?",
                    rs -> new Event(rs.getString(1), rs.getString(2), rs.getString(3)), ws, limit, offset),
                    count(c, "SELECT COUNT(*) FROM events WHERE ws_id=?", ws));
        }
    }

    // ---------- simulated coworker ----------
    public static User ensureBot(int ws) throws Exception {
        try (Connection c = open()) {
            List<User> l = query(c, "SELECT id,username,name,email FROM users WHERE username='aarav.sim'", Db::user);
            if (l.isEmpty()) {
                update(c, "INSERT INTO users(username,name,email,pass) VALUES('aarav.sim','Aarav (sim)','aarav.sim@cospace.local',?)", newPass(UUID.randomUUID().toString()));
                l = query(c, "SELECT id,username,name,email FROM users WHERE username='aarav.sim'", Db::user);
            }
            User b = l.get(0);
            if (update(c, "INSERT OR IGNORE INTO members(ws_id,user_id,role) VALUES(?,?,'EDITOR')", ws, b.id()) > 0)
                log(c, ws, b.id(), "joined the workspace (simulated coworker)");
            return b;
        }
    }
}
