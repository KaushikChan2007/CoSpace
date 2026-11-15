package workspace;

import javax.swing.*;
import java.awt.*;

final class LoginFrame extends JFrame {
    private boolean register;
    private final JTextField name = Theme.field(20), email = Theme.field(20), user = Theme.field(20);
    private final JPasswordField pass = Theme.password(20);
    private final JLabel msg = Theme.muted(" ");
    private final JPanel formHolder = Theme.plain();
    private final Theme.Btn submit = new Theme.Btn("Sign in", true);
    private final Theme.Btn toggle = new Theme.Btn("Create an account", false);

    LoginFrame() {
        super("CoSpace");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(480, 600);
        setResizable(false);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);
        setLayout(new GridBagLayout());

        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 18));
        card.setPreferredSize(new Dimension(380, 500));
        JPanel top = Theme.plain();
        top.setLayout(new GridLayout(2, 1));
        top.add(Theme.h1("CoSpace"));
        top.add(Theme.muted("A shared workspace for your team"));
        formHolder.setLayout(new BorderLayout());
        JPanel bottom = Theme.plain();
        bottom.setLayout(new GridLayout(3, 1, 0, 8));
        bottom.add(msg);
        bottom.add(submit);
        bottom.add(toggle);
        card.add(top, BorderLayout.NORTH);
        card.add(formHolder, BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        add(card);
        rebuild();

        toggle.addActionListener(e -> { register = !register; rebuild(); });
        submit.addActionListener(e -> go());
        pass.addActionListener(e -> go());
    }

    private void rebuild() {
        formHolder.removeAll();
        formHolder.add(register
                ? Theme.form(new String[]{"Full name", "Email", "Username", "Password"}, new JComponent[]{name, email, user, pass})
                : Theme.form(new String[]{"Username", "Password"}, new JComponent[]{user, pass}), BorderLayout.NORTH);
        submit.setText(register ? "Create account" : "Sign in");
        toggle.setText(register ? "I already have an account" : "Create an account");
        msg.setText(" ");
        formHolder.revalidate();
        formHolder.repaint();
    }

    private void go() {
        String u = user.getText().trim(), p = new String(pass.getPassword()), n = name.getText().trim(), em = email.getText().trim();
        if (u.isEmpty() || p.isEmpty()) { msg.setText("Enter username and password."); return; }
        if (register && (n.isEmpty() || !em.contains("@") || p.length() < 4)) { msg.setText("Fill every field (email valid, password 4+ chars)."); return; }
        submit.setEnabled(false);
        Async.call(this, () -> register ? Db.register(u, n, em, p) : Db.login(u, p), me -> {
            submit.setEnabled(true);
            if (me == null) { msg.setText("Wrong username or password."); return; }
            new AppFrame(me).setVisible(true);
            dispose();
        });
    }
}
