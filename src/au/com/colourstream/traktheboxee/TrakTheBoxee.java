/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.com.colourstream.traktheboxee;

import java.awt.SystemTray;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 *
 * @author predakanga
 */
public class TrakTheBoxee {
    public static final String version = "2.0.0b2";
    public static final String apiKey = "42920cadcb31ff648cb5fe2865473c9bf164c5bd";
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            Logger.getLogger(TrakTheBoxee.class.getName()).log(Level.WARNING, "Exception caught whilst setting the system look and feel", ex);
        }
        // Kick-start the TrayIcon and hence the program
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowTrayIcon();
            }
        });
    }
    
    private static void createAndShowTrayIcon() {
        if(!SystemTray.isSupported()) {
            Logger.getLogger(TrakTheBoxee.class.getName()).log(Level.SEVERE, "No System Tray is available. Exiting.");
            return;
        }
        
        TrakTheBoxeeTrayIcon icon = new TrakTheBoxeeTrayIcon();
    }
}
