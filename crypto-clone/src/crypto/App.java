package crypto;

import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import javax.swing.*;
import javax.swing.table.*;
import crypto.Models.*;
import crypto.MarketService.*;

/** Swing desktop client. Start: java -cp out crypto.App   (MySQL: -Ddb.url=... -Ddb.user=... -Ddb.password=...) */
public class App extends JFrame {
    static final Color BG = new Color(0x0f1623), CARD = new Color(0x182033), FG = new Color(0xe8ecf6), MUTED = new Color(0x93a0ba), GREEN = new Color(0x22c55e), RED = new Color(0xef4444), ACC = new Color(0xf7931a);
    final MarketService svc;
    User me;
    final CardLayout cards = new CardLayout();
    final JPanel root = new JPanel(cards);
    JTabbedPane tabs;
    JLabel status = new JLabel(" ");
    Timer ticker;

    public App(MarketService svc) {
        super("CryptoClone - Market Tracker");
        this.svc = svc;
        setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(1080, 700); setLocationRelativeTo(null);
        root.add(loginPanel(), "login"); setContentPane(root);
    }
    // ---------- helpers ----------
    static String usd(double v) { return v >= 1 ? String.format("$%,.2f", v) : String.format("$%.4f", v); }
    static String big(double v) { return v >= 1e9 ? String.format("$%.2fB", v / 1e9) : v >= 1e6 ? String.format("$%.2fM", v / 1e6) : String.format("$%,.0f", v); }
    static String pct(double v) { return String.format("%+.2f%%", v); }
    static JLabel label(String t, int size, Color c, boolean bold) { JLabel l = new JLabel(t); l.setFont(l.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, size)); l.setForeground(c); return l; }
    static JButton button(String t, Color bg) { JButton b = new JButton(t); b.setBackground(bg); b.setForeground(Color.WHITE); b.setFocusPainted(false); b.setOpaque(true); b.setBorderPainted(false); b.setFont(b.getFont().deriveFont(Font.BOLD)); b.setMargin(new Insets(6, 14, 6, 14)); return b; }
    static JPanel panel(LayoutManager l) { JPanel p = new JPanel(l); p.setBackground(BG); return p; }
    static void style(JTextField f) { f.setBackground(CARD); f.setForeground(FG); f.setCaretColor(FG); f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0x2b3550)), BorderFactory.createEmptyBorder(5, 8, 5, 8))); }
    void error(String m) { JOptionPane.showMessageDialog(this, m, "Error", JOptionPane.ERROR_MESSAGE); }
    void info(String m) { status.setText(m); }

    static class Model extends DefaultTableModel {
        Model(String... cols) { super(cols, 0); }
        @Override public boolean isCellEditable(int r, int c) { return false; }
    }
    static JTable table(Model m) {
        JTable t = new JTable(m);
        t.setBackground(CARD); t.setForeground(FG); t.setGridColor(new Color(0x25304a)); t.setRowHeight(28); t.setSelectionBackground(new Color(0x2b3a5e)); t.setSelectionForeground(Color.WHITE);
        t.getTableHeader().setBackground(new Color(0x111827)); t.getTableHeader().setForeground(MUTED); t.setFillsViewportHeight(true); t.setShowVerticalLines(false);
        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int row, int col) {
                super.getTableCellRendererComponent(tb, v, s, f, row, col);
                String x = String.valueOf(v);
                setForeground(!s && (x.endsWith("%") || x.startsWith("+$") || x.startsWith("-$")) ? (x.startsWith("-") ? RED : GREEN) : s ? Color.WHITE : FG);
                setHorizontalAlignment(col == 0 || col == 1 ? LEFT : RIGHT); return this;
            }
        };
        r.setOpaque(true);
        t.setDefaultRenderer(Object.class, r);
        return t;
    }
    static JScrollPane scroll(JTable t) { JScrollPane s = new JScrollPane(t); s.getViewport().setBackground(CARD); s.setBorder(BorderFactory.createLineBorder(new Color(0x25304a))); return s; }

    // ---------- login ----------
    JPanel loginPanel() {
        JPanel outer = panel(new GridBagLayout());
        JPanel box = new JPanel(new GridLayout(0, 1, 0, 8)); box.setBackground(CARD); box.setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));
        JTextField user = new JTextField(18), name = new JTextField(), email = new JTextField(); JPasswordField pw = new JPasswordField();
        style(user); style(name); style(email); style(pw); pw.setBackground(CARD); pw.setForeground(FG);
        JLabel msg = label(" ", 12, RED, false);
        box.add(label("₿ CryptoClone", 26, ACC, true)); box.add(label("Track the market. Practise trading with $10,000 virtual cash.", 12, MUTED, false));
        box.add(label("Username", 12, MUTED, false)); box.add(user); box.add(label("Password", 12, MUTED, false)); box.add(pw);
        JPanel extra = new JPanel(new GridLayout(0, 1, 0, 8)); extra.setBackground(CARD); extra.setVisible(false);
        extra.add(label("Full name (sign-up only)", 12, MUTED, false)); extra.add(name); extra.add(label("Email (sign-up only)", 12, MUTED, false)); extra.add(email);
        box.add(extra); box.add(msg);
        JButton login = button("Log in", ACC), signup = button("Create account", new Color(0x334155));
        Runnable doLogin = () -> { try { me = svc.login(user.getText(), new String(pw.getPassword())); pw.setText(""); msg.setText(" "); showMain(); } catch (CryptoException e) { msg.setText(e.getMessage()); } };
        login.addActionListener(e -> doLogin.run()); pw.addActionListener(e -> doLogin.run());
        signup.addActionListener(e -> {
            if (!extra.isVisible()) { extra.setVisible(true); signup.setText("Register now"); box.revalidate(); return; }
            try { svc.register(user.getText(), name.getText(), email.getText(), new String(pw.getPassword())); me = svc.login(user.getText(), new String(pw.getPassword())); pw.setText(""); msg.setText(" "); showMain(); }
            catch (CryptoException ex) { msg.setText(ex.getMessage()); }
        });
        box.add(login); box.add(signup); box.add(label("Demo admin: admin / Admin@123", 11, MUTED, false));
        outer.add(box); return outer;
    }

    // ---------- main ----------
    void showMain() {
        if (tabs != null) root.remove(tabs.getParent());
        tabs = new JTabbedPane();
        tabs.addTab("Overview", overviewTab()); tabs.addTab("Market", marketTab()); tabs.addTab("Watchlist", watchTab());
        tabs.addTab("Portfolio", portfolioTab()); tabs.addTab("History", historyTab()); tabs.addTab("Profile", profileTab());
        if (me.role() == Role.ADMIN) tabs.addTab("Admin", adminTab());
        tabs.addChangeListener(e -> refreshAll());
        JPanel main = panel(new BorderLayout());
        JPanel top = panel(new BorderLayout()); top.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        top.add(label("₿ CryptoClone", 20, ACC, true), BorderLayout.WEST);
        JPanel right = panel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JCheckBox live = new JCheckBox("Live prices"); live.setOpaque(false); live.setForeground(FG);
        live.addActionListener(e -> { if (live.isSelected()) { ticker = new Timer(3000, x -> { svc.tick(); refreshAll(); }); ticker.start(); } else if (ticker != null) ticker.stop(); });
        JButton out = button("Log out", new Color(0x334155)); out.addActionListener(e -> { if (ticker != null) ticker.stop(); me = null; cards.show(root, "login"); });
        right.add(live); right.add(label(me.fullName() + (me.role() == Role.ADMIN ? " (admin)" : ""), 13, FG, false)); right.add(out); top.add(right, BorderLayout.EAST);
        main.add(top, BorderLayout.NORTH); main.add(tabs, BorderLayout.CENTER); status.setForeground(MUTED); status.setBorder(BorderFactory.createEmptyBorder(4, 14, 4, 14)); main.add(status, BorderLayout.SOUTH);
        root.add(main, "main"); cards.show(root, "main"); refreshAll();
    }
    final java.util.List<Runnable> refreshers = new java.util.ArrayList<>();
    void refreshAll() { new java.util.ArrayList<>(refreshers).forEach(Runnable::run); }
    void reg(Runnable r) { refreshers.add(r); }

    JPanel overviewTab() {
        refreshers.clear();
        JPanel p = panel(new BorderLayout(0, 12)); p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JPanel stats = panel(new GridLayout(1, 0, 12, 0)); JTextArea movers = new JTextArea(); movers.setEditable(false); movers.setBackground(CARD); movers.setForeground(FG); movers.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        JPanel chart = new JPanel() {
            @Override protected void paintComponent(Graphics g) { super.paintComponent(g); drawBars(g, getWidth(), getHeight()); }
        };
        chart.setBackground(CARD); chart.setPreferredSize(new Dimension(300, 240));
        p.add(stats, BorderLayout.NORTH);
        JPanel mid = panel(new GridLayout(1, 2, 12, 0)); mid.add(wrap("Top movers (24h)", new JScrollPane(movers))); mid.add(wrap("24h change by coin", chart)); p.add(mid, BorderLayout.CENTER);
        reg(() -> {
            Overview o = svc.overview(); stats.removeAll();
            stats.add(card("Total market cap", big(o.totalMarketCap()), FG)); stats.add(card("24h volume", big(o.totalVolume()), FG));
            stats.add(card("BTC dominance", String.format("%.1f%%", o.btcDominance()), ACC));
            stats.add(card("Your net worth", usd(svc.netWorth(me.username())), GREEN)); stats.revalidate(); stats.repaint();
            StringBuilder sb = new StringBuilder();
            svc.search("", "All", SortKey.CHANGE, true).forEach(c -> sb.append(String.format("%-6s %-12s %12s %9s%n", c.symbol(), c.name(), usd(c.price()), pct(c.change24h()))));
            movers.setText(sb.toString()); chart.repaint();
        });
        return p;
    }
    void drawBars(Graphics g0, int w, int h) {
        Graphics2D g = (Graphics2D) g0; g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        List<Coin> cs = svc.search("", "All", SortKey.MARKET_CAP, true); if (cs.isEmpty()) return;
        double max = cs.stream().mapToDouble(c -> Math.abs(c.change24h())).max().orElse(1); if (max == 0) max = 1;
        int n = cs.size(), bw = Math.max(10, (w - 40) / n - 8), mid = h / 2 - 8;
        g.setColor(new Color(0x2b3550)); g.drawLine(10, mid, w - 10, mid);
        for (int i = 0; i < n; i++) {
            Coin c = cs.get(i); int x = 20 + i * ((w - 40) / n), bh = (int) (Math.abs(c.change24h()) / max * (mid - 24));
            g.setColor(c.change24h() >= 0 ? GREEN : RED); if (c.change24h() >= 0) g.fillRect(x, mid - bh, bw, bh); else g.fillRect(x, mid, bw, bh);
            g.setColor(MUTED); g.setFont(g.getFont().deriveFont(11f)); g.drawString(c.symbol(), x, h - 12);
        }
    }
    JPanel wrap(String title, JComponent c) { JPanel p = panel(new BorderLayout(0, 6)); p.add(label(title, 14, MUTED, true), BorderLayout.NORTH); p.add(c); return p; }
    JPanel card(String title, String value, Color c) { JPanel p = new JPanel(new GridLayout(2, 1)); p.setBackground(CARD); p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14)); p.add(label(title, 12, MUTED, false)); p.add(label(value, 22, c, true)); return p; }

    JPanel marketTab() {
        JPanel p = panel(new BorderLayout(0, 10)); p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JTextField search = new JTextField(16); style(search);
        JComboBox<String> filter = new JComboBox<>(new String[]{"All", "Gainers", "Losers", "Under $1", "Over $1000"});
        JComboBox<SortKey> sort = new JComboBox<>(SortKey.values()); JCheckBox desc = new JCheckBox("Descending", true); desc.setOpaque(false); desc.setForeground(FG);
        JPanel bar = panel(new FlowLayout(FlowLayout.LEFT, 8, 0)); bar.add(label("Search", 12, MUTED, false)); bar.add(search); bar.add(filter); bar.add(label("Sort", 12, MUTED, false)); bar.add(sort); bar.add(desc);
        Model m = new Model("#", "Name", "Price", "24h", "Market cap", "Volume (24h)"); JTable t = table(m);
        Runnable load = () -> {
            m.setRowCount(0); int i = 1;
            for (Coin c : svc.search(search.getText(), (String) filter.getSelectedItem(), (SortKey) sort.getSelectedItem(), desc.isSelected()))
                m.addRow(new Object[]{c.symbol(), c.name(), usd(c.price()), pct(c.change24h()), big(c.marketCap()), big(c.volume24h())});
            info(m.getRowCount() + " coins");
        };
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { load.run(); } public void removeUpdate(javax.swing.event.DocumentEvent e) { load.run(); } public void changedUpdate(javax.swing.event.DocumentEvent e) { load.run(); } });
        filter.addActionListener(e -> load.run()); sort.addActionListener(e -> load.run()); desc.addActionListener(e -> load.run());
        t.addMouseListener(new java.awt.event.MouseAdapter() { public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2 && t.getSelectedRow() >= 0) details((String) m.getValueAt(t.getSelectedRow(), 0)); } });
        JButton open = button("Coin details", ACC); open.addActionListener(e -> { if (t.getSelectedRow() < 0) error("Select a coin first."); else details((String) m.getValueAt(t.getSelectedRow(), 0)); });
        bar.add(open);
        p.add(bar, BorderLayout.NORTH); p.add(scroll(t)); reg(load); return p;
    }

    void details(String symbol) {
        Coin c = svc.coin(symbol);
        JDialog d = new JDialog(this, c.name() + " (" + c.symbol() + ")", true); d.setSize(640, 560); d.setLocationRelativeTo(this);
        JPanel p = panel(new BorderLayout(0, 10)); p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JPanel head = panel(new GridLayout(0, 1)); head.add(label(c.name() + " · " + c.symbol(), 20, FG, true));
        head.add(label(usd(c.price()) + "   " + pct(c.change24h()), 22, c.change24h() >= 0 ? GREEN : RED, true));
        head.add(label("Market cap " + big(c.marketCap()) + "   ·   Volume " + big(c.volume24h()) + "   ·   Supply " + String.format("%,.0f", c.supply()), 12, MUTED, false));
        JTextArea desc = new JTextArea(c.description()); desc.setLineWrap(true); desc.setWrapStyleWord(true); desc.setEditable(false); desc.setBackground(BG); desc.setForeground(MUTED);
        head.add(desc);
        JPanel chart = new JPanel() { @Override protected void paintComponent(Graphics g) { super.paintComponent(g); line(g, c.history(), getWidth(), getHeight()); } };
        chart.setBackground(CARD); chart.setPreferredSize(new Dimension(500, 200));
        JTextField qty = new JTextField("1", 8); style(qty);
        JButton buy = button("Buy", GREEN), sell = button("Sell", RED), watch = button("★ Watchlist", new Color(0x334155));
        buy.addActionListener(e -> trade(d, true, symbol, qty.getText())); sell.addActionListener(e -> trade(d, false, symbol, qty.getText()));
        watch.addActionListener(e -> { svc.addWatch(me.username(), symbol); refreshAll(); info(symbol + " added to watchlist"); });
        JPanel act = panel(new FlowLayout(FlowLayout.LEFT, 8, 0)); act.add(label("Quantity", 12, MUTED, false)); act.add(qty); act.add(buy); act.add(sell); act.add(watch);
        p.add(head, BorderLayout.NORTH); p.add(chart); p.add(act, BorderLayout.SOUTH); d.setContentPane(p); d.setVisible(true);
    }
    void trade(JDialog d, boolean buy, String symbol, String q) {
        try {
            double qty = Double.parseDouble(q.trim());
            if (buy) svc.buy(me.username(), symbol, qty); else svc.sell(me.username(), symbol, qty);
            me = svc.repo().findUser(me.username()).orElseThrow(); refreshAll();
            info((buy ? "Bought " : "Sold ") + qty + " " + symbol); d.dispose();
        } catch (NumberFormatException e) { error("Enter a valid number."); } catch (CryptoException e) { error(e.getMessage()); }
    }
    static void line(Graphics g0, List<Double> h, int w, int ht) {
        Graphics2D g = (Graphics2D) g0; g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (h.size() < 2) return;
        double mn = h.stream().mapToDouble(Double::doubleValue).min().getAsDouble(), mx = h.stream().mapToDouble(Double::doubleValue).max().getAsDouble(); if (mx == mn) mx = mn + 1;
        int pad = 24; g.setColor(h.get(h.size() - 1) >= h.get(0) ? GREEN : RED); g.setStroke(new BasicStroke(2f));
        int px = 0, py = 0;
        for (int i = 0; i < h.size(); i++) {
            int x = pad + (int) ((w - 2.0 * pad) * i / (h.size() - 1)), y = ht - pad - (int) ((ht - 2.0 * pad) * (h.get(i) - mn) / (mx - mn));
            if (i > 0) g.drawLine(px, py, x, y); px = x; py = y;
        }
        g.setColor(MUTED); g.setFont(g.getFont().deriveFont(11f)); g.drawString("High " + usd(mx), pad, 14); g.drawString("Low " + usd(mn), pad, ht - 6);
    }

    JPanel watchTab() {
        JPanel p = panel(new BorderLayout(0, 10)); p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        Model m = new Model("#", "Name", "Price", "24h", "Market cap"); JTable t = table(m);
        JComboBox<String> add = new JComboBox<>(); JButton addB = button("Add", ACC), rem = button("Remove selected", RED);
        addB.addActionListener(e -> { if (add.getSelectedItem() != null) { svc.addWatch(me.username(), ((String) add.getSelectedItem()).split(" ")[0]); refreshAll(); } });
        rem.addActionListener(e -> { if (t.getSelectedRow() < 0) error("Select a coin first."); else { svc.removeWatch(me.username(), (String) m.getValueAt(t.getSelectedRow(), 0)); refreshAll(); } });
        JPanel bar = panel(new FlowLayout(FlowLayout.LEFT, 8, 0)); bar.add(label("Add coin", 12, MUTED, false)); bar.add(add); bar.add(addB); bar.add(rem);
        p.add(bar, BorderLayout.NORTH); p.add(scroll(t));
        reg(() -> {
            m.setRowCount(0); for (Coin c : svc.watchlist(me.username())) m.addRow(new Object[]{c.symbol(), c.name(), usd(c.price()), pct(c.change24h()), big(c.marketCap())});
            add.removeAllItems(); svc.search("", "All", SortKey.NAME, false).forEach(c -> add.addItem(c.symbol() + " - " + c.name()));
        });
        return p;
    }

    JPanel portfolioTab() {
        JPanel p = panel(new BorderLayout(0, 10)); p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JPanel stats = panel(new GridLayout(1, 0, 12, 0));
        Model m = new Model("#", "Name", "Quantity", "Avg cost", "Price", "Value", "P/L", "P/L %"); JTable t = table(m);
        JButton sellAll = button("Sell selected position", RED);
        sellAll.addActionListener(e -> { if (t.getSelectedRow() < 0) { error("Select a position first."); return; } String s = (String) m.getValueAt(t.getSelectedRow(), 0);
            double q = svc.portfolio(me.username()).stream().filter(x -> x.coin().symbol().equals(s)).findFirst().get().quantity();
            try { svc.sell(me.username(), s, q); me = svc.repo().findUser(me.username()).orElseThrow(); refreshAll(); } catch (CryptoException ex) { error(ex.getMessage()); } });
        JPanel south = panel(new FlowLayout(FlowLayout.LEFT)); south.add(sellAll);
        p.add(stats, BorderLayout.NORTH); p.add(scroll(t)); p.add(south, BorderLayout.SOUTH);
        reg(() -> {
            me = svc.repo().findUser(me.username()).orElse(me);
            m.setRowCount(0);
            for (Position x : svc.portfolio(me.username())) m.addRow(new Object[]{x.coin().symbol(), x.coin().name(), String.format("%.6f", x.quantity()), usd(x.avgCost()), usd(x.coin().price()), usd(x.value()),
                    (x.pnl() >= 0 ? "+" : "-") + usd(Math.abs(x.pnl())), pct(x.pnlPct())});
            stats.removeAll(); stats.add(card("Cash", usd(me.cash()), FG)); stats.add(card("Holdings value", usd(svc.portfolioValue(me.username())), FG)); stats.add(card("Net worth", usd(svc.netWorth(me.username())), GREEN));
            stats.add(card("Total P/L", String.format("%+,.2f", svc.netWorth(me.username()) - MarketService.START_CASH), svc.netWorth(me.username()) >= MarketService.START_CASH ? GREEN : RED)); stats.revalidate(); stats.repaint();
        });
        return p;
    }
    JPanel historyTab() {
        JPanel p = panel(new BorderLayout()); p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        Model m = new Model("Time", "Coin", "Side", "Quantity", "Price", "Total"); JTable t = table(m); p.add(scroll(t));
        reg(() -> { m.setRowCount(0); List<Transaction> l = new java.util.ArrayList<>(svc.repo().transactions(me.username())); java.util.Collections.reverse(l);
            for (Transaction x : l) m.addRow(new Object[]{x.time(), x.symbol(), x.side(), String.format("%.6f", x.quantity()), usd(x.price()), usd(x.quantity() * x.price())}); });
        return p;
    }
    JPanel profileTab() {
        JPanel p = panel(new GridBagLayout()); JPanel box = new JPanel(new GridLayout(0, 1, 0, 8)); box.setBackground(CARD); box.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        JTextField n = new JTextField(me.fullName(), 22), e = new JTextField(me.email()); JPasswordField old = new JPasswordField(), nw = new JPasswordField(); style(n); style(e);
        for (JPasswordField f : new JPasswordField[]{old, nw}) { f.setBackground(CARD); f.setForeground(FG); f.setCaretColor(FG); }
        JButton save = button("Save profile", ACC), pw = button("Change password", new Color(0x334155));
        save.addActionListener(x -> { try { me = svc.updateProfile(me.username(), n.getText(), e.getText()); info("Profile saved"); } catch (CryptoException ex) { error(ex.getMessage()); } });
        pw.addActionListener(x -> { try { svc.changePassword(me.username(), new String(old.getPassword()), new String(nw.getPassword())); old.setText(""); nw.setText(""); info("Password changed"); } catch (CryptoException ex) { error(ex.getMessage()); } });
        box.add(label("Profile - " + me.username(), 18, FG, true)); box.add(label("Full name", 12, MUTED, false)); box.add(n); box.add(label("Email", 12, MUTED, false)); box.add(e); box.add(save);
        box.add(label("Current password", 12, MUTED, false)); box.add(old); box.add(label("New password (min 8)", 12, MUTED, false)); box.add(nw); box.add(pw);
        p.add(box); return p;
    }
    JPanel adminTab() {
        JPanel p = panel(new BorderLayout(0, 10)); p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        Model m = new Model("#", "Name", "Price", "Supply"); JTable t = table(m);
        JTextField sym = new JTextField(6), nm = new JTextField(10), pr = new JTextField(8), sp = new JTextField(10), vol = new JTextField(8); for (JTextField f : new JTextField[]{sym, nm, pr, sp, vol}) style(f);
        JButton add = button("List coin", GREEN), set = button("Set price", ACC), del = button("Delist", RED);
        add.addActionListener(e -> { try { svc.addCoin(me, sym.getText(), nm.getText(), Double.parseDouble(pr.getText()), Double.parseDouble(sp.getText()), vol.getText().isBlank() ? 0 : Double.parseDouble(vol.getText()), "Listed by administrator");
            refreshAll(); info("Coin listed"); } catch (NumberFormatException x) { error("Price, supply and volume must be numbers."); } catch (CryptoException x) { error(x.getMessage()); } });
        set.addActionListener(e -> { if (t.getSelectedRow() < 0) { error("Select a coin first."); return; } String v = JOptionPane.showInputDialog(this, "New price (USD):"); if (v == null) return;
            try { svc.setPrice(me, (String) m.getValueAt(t.getSelectedRow(), 0), Double.parseDouble(v)); refreshAll(); } catch (NumberFormatException x) { error("Enter a valid number."); } catch (CryptoException x) { error(x.getMessage()); } });
        del.addActionListener(e -> { if (t.getSelectedRow() < 0) { error("Select a coin first."); return; } String s = (String) m.getValueAt(t.getSelectedRow(), 0);
            if (JOptionPane.showConfirmDialog(this, "Delist " + s + "?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) { svc.delist(me, s); refreshAll(); } });
        JPanel form = panel(new FlowLayout(FlowLayout.LEFT, 6, 0)); form.add(label("Symbol", 12, MUTED, false)); form.add(sym); form.add(label("Name", 12, MUTED, false)); form.add(nm); form.add(label("Price", 12, MUTED, false)); form.add(pr);
        form.add(label("Supply", 12, MUTED, false)); form.add(sp); form.add(label("Volume", 12, MUTED, false)); form.add(vol); form.add(add);
        JPanel acts = panel(new FlowLayout(FlowLayout.LEFT)); acts.add(set); acts.add(del);
        Model um = new Model("Username", "Name", "Email", "Role", "Cash"); JTable ut = table(um);
        JPanel top = panel(new BorderLayout(0, 8)); top.add(form, BorderLayout.NORTH); top.add(scroll(t)); top.add(acts, BorderLayout.SOUTH);
        JPanel both = panel(new GridLayout(2, 1, 0, 10)); both.add(top); both.add(wrap("Registered users", scroll(ut))); p.add(both);
        reg(() -> { m.setRowCount(0); svc.search("", "All", SortKey.MARKET_CAP, true).forEach(c -> m.addRow(new Object[]{c.symbol(), c.name(), usd(c.price()), String.format("%,.0f", c.supply())}));
            um.setRowCount(0); svc.repo().listUsers().forEach(u -> um.addRow(new Object[]{u.username(), u.fullName(), u.email(), u.role(), usd(u.cash())})); });
        return p;
    }

    // ---------- bootstrap ----------
    public static Repository openRepository() {
        String url = System.getProperty("db.url");
        if (url != null) return new JdbcRepository(url, System.getProperty("db.user", "root"), System.getProperty("db.password", ""));
        return new FileRepository(Path.of(System.getProperty("data.dir", "data")));
    }
    public static void main(String[] args) {
        MarketService svc = new MarketService(openRepository(), System.nanoTime()); svc.seedIfEmpty();
        SwingUtilities.invokeLater(() -> { try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) { } new App(svc).setVisible(true); });
    }
}
