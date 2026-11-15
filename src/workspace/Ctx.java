package workspace;

/** Shared context handed to every page: who I am, which workspace, and my current role. */
final class Ctx {
    final Db.User me;
    final Db.Workspace ws;
    volatile String role;
    Ctx(Db.User me, Db.Workspace ws) { this.me = me; this.ws = ws; this.role = ws.role(); }
    boolean canEdit() { return !"VIEWER".equals(role); }
    boolean isOwner() { return "OWNER".equals(role); }
}
