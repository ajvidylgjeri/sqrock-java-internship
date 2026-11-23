package crypto;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Renders the GUI to PNG files (run under Xvfb):  xvfb-run java -cp out crypto.Shots screenshots */
public class Shots {
    static void snap(Component c, Path f) throws Exception {
        c.validate();
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics(); c.paint(g); g.dispose(); ImageIO.write(img, "png", f.toFile());
    }
    public static void main(String[] a) throws Exception {
        Path out = Path.of(a.length > 0 ? a[0] : "screenshots"); Files.createDirectories(out);
        Path data = Files.createTempDirectory("shots");
        MarketService svc = new MarketService(new FileRepository(data), 2026); svc.seedIfEmpty();
        svc.register("demo", "Demo Trader", "demo@example.com", "Password1"); svc.buy("demo", "BTC", 0.05); svc.buy("demo", "ETH", 0.8); svc.buy("demo", "SOL", 12);
        svc.addWatch("demo", "BTC"); svc.addWatch("demo", "DOGE"); svc.addWatch("demo", "ADA");
        for (int i = 0; i < 6; i++) svc.tick();
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                App app = new App(svc); app.setVisible(true);
                snap(app.getContentPane(), out.resolve("01-login.png"));
                app.me = svc.login("demo", "Password1"); app.showMain();
                String[] names = {"02-overview", "03-market", "04-watchlist", "05-portfolio", "06-history", "07-profile"};
                for (int i = 0; i < names.length; i++) { app.tabs.setSelectedIndex(i); app.refreshAll(); snap(app.getContentPane(), out.resolve(names[i] + ".png")); }
                app.me = svc.login("admin", "Admin@123"); app.showMain(); app.tabs.setSelectedIndex(6); app.refreshAll(); snap(app.getContentPane(), out.resolve("08-admin.png"));
                app.me = svc.login("demo", "Password1"); app.showMain();
                new javax.swing.Timer(700, e -> {
                    for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isVisible()) { try { snap(d.getContentPane(), out.resolve("09-coin-details.png")); } catch (Exception ex) { ex.printStackTrace(); } d.dispose(); }
                }) {{ setRepeats(false); start(); }};
                app.details("ETH");
                app.dispose();
            } catch (Exception e) { e.printStackTrace(); System.exit(1); }
        });
        System.out.println("screenshots written to " + out); System.exit(0);
    }
}
