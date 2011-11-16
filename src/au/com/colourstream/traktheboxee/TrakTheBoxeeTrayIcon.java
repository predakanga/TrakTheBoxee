/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.com.colourstream.traktheboxee;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.prefs.BackingStoreException;
import javax.swing.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import au.com.colourstream.traktheboxee.BoxeeClient.BoxeeNotificationEvent;
import java.security.MessageDigest;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 *
 * @author predakanga
 */
public class TrakTheBoxeeTrayIcon extends TrayIcon implements javax.jmdns.ServiceListener, BoxeeClient.BoxeeNotificationListener, BoxeeClient.BoxeePasscodeRequestListener, BoxeeClient.BoxeeConnectedListener {
    SystemTray tray = null;
    PopupMenu menu = null;
    Menu boxeeMenu = null;
    JmDNS mdns = null;
    HashMap<String,ServiceInfo> foundBoxees = null;
    HashMap<MenuItem,ServiceInfo> boxeeMenuMap = null;
    MenuItem connectedBoxeeItem = null;
    MenuItem scrobbledItem = null;
    BoxeeClient boxee = null;
    TraktClient trakt = null;
    Timer actionTimer = null;
    Calendar lastReport = Calendar.getInstance();
    Map lastReportData = null;
    boolean hasBeenScrobbled = false;
    Logger _log = Logger.getLogger(this.getClass().getCanonicalName());
    
    public TrakTheBoxeeTrayIcon() {
        super(TrakTheBoxeeTrayIcon.createImage("images/trakt-centered.png", "TrakTheBoxee"));
        
        // Configure the image to auto-resize
        setImageAutoSize(true);
        lastReport.setTimeInMillis(0);
        
        // Set up the menu
        foundBoxees = new HashMap<String,ServiceInfo>();
        menu = new PopupMenu();
        setupMenu();
        setPopupMenu(menu);
        
        // And display the icon
        tray = SystemTray.getSystemTray();
        try {
            tray.add(this);
        } catch (AWTException ex) {
            Logger.getLogger(TrakTheBoxeeTrayIcon.class.getName()).log(Level.SEVERE, "Exception occurred whilst adding our icon to the tray", ex);
        }
        try {
            // Next, create the JmDNS instance
            mdns = JmDNS.create();
            mdns.addServiceListener("_boxee-jsonrpc._tcp.local.", this);
            _log.log(Level.INFO, "Searching for Boxees");
            setupBoxeeMenu();
        } catch (IOException ex) {
            Logger.getLogger(TrakTheBoxeeTrayIcon.class.getName()).log(Level.SEVERE, "Couldn't create the JmDNS searcher", ex);
        }
        
        actionTimer = new Timer(3*60*1000, new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                timerFunc();
            }
        });
        ensurePreferences();
    }
    
    protected void ensurePreferences() {
        _log.log(Level.INFO, "Checking whether we need to show the preference pane");
        Preferences prefs = Preferences.userNodeForPackage(this.getClass());

        if(prefs.get("username", "").equals("")) {
            showPreferences();
        } else {
            // If we have preferences, set up the Trakt API
            trakt = new TraktClient();
        }
        // If we have a previous server, connect to it here
    }
    
    protected void showPreferences() {
        _log.log(Level.INFO, "Showing the preference pane");
        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        String origUsername = "";
        String origPassword = "";
        
        // Populate from preferences, if we have any
        Preferences prefs = Preferences.userNodeForPackage(this.getClass());

        origUsername = prefs.get("username", "");
        origPassword = prefs.get("password", "");
        if(!origUsername.equals("")) {
            username.setText(origUsername);
        }
        if(!origPassword.equals("")) {
            password.setText(origPassword);
        }
        
        final JComponent[] inputs = new JComponent[] {
            new JLabel("Trakt.TV Username"),
            username,
            new JLabel("Trakt.TV Password"),
            password
        };
        
        JOptionPane pane = new JOptionPane(inputs, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dlg = pane.createDialog(null, "TrakTheBoxee Preferences");
        dlg.show();
        int selectedValue = (Integer)pane.getValue();
        if(selectedValue == JOptionPane.CANCEL_OPTION) {
            return;
        }
        
        // Not cancelled - hash the password if it's changed
        // Set the username regardless
        prefs.put("username", username.getText());
        String newPassword = password.getText();
        if(!newPassword.equals(origPassword)) {
            prefs.put("password", sha1hash(newPassword));
        }
        try {
            prefs.sync();
        } catch (BackingStoreException ex) {
            _log.log(Level.SEVERE, "Could not store preferences", ex);
        }
        
        // And once the settings have been saved, start up the trakt client
        _log.log(Level.INFO, "Starting the Trakt client");
        trakt = new TraktClient();
    }
    
    protected String sha1hash(String input) {
        MessageDigest sha1 = null;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException ex) {
            _log.log(Level.SEVERE, "Couldn't hash the password, Trakt will be non-functional", ex);
            return "";
        }
        byte[] digest = sha1.digest(input.getBytes());
        StringBuilder sb = new StringBuilder(digest.length*2);
        for(byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    protected void rescheduleTimer(int delay) {
        actionTimer.stop();
        actionTimer.setInitialDelay(delay);
        actionTimer.restart();
        actionTimer.start();
    }
    
    protected void setupMenu() {
        final TrayIcon icon = this;
        boxeeMenu = new Menu("Boxees");
        setupBoxeeMenu();
        
        scrobbledItem = new MenuItem("Recently scrobbled");
        scrobbledItem.setEnabled(false);
        MenuItem quitItem = new MenuItem("Quit");
        quitItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tray.remove(icon);
                System.exit(0);
            }
        });
        MenuItem prefsItem = new MenuItem("Preferences");
        prefsItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showPreferences();
            }
        });

        menu.add(boxeeMenu);
        menu.addSeparator();
        menu.add(scrobbledItem);
        menu.addSeparator();
        menu.add(prefsItem);
        menu.add(quitItem);
    }
    
    protected void setupBoxeeMenu() {
        _log.log(Level.INFO, "Regenerating the Boxee menu");
        boxeeMenu.removeAll();
        if(mdns == null) {
            MenuItem failureItem = new MenuItem("Could not search for Boxee boxes. Please check your logs");
            failureItem.setEnabled(false);
            boxeeMenu.add(failureItem);
            return;
        }
        if(foundBoxees.isEmpty()) {
            MenuItem searchingItem = new MenuItem("Searching for Boxee boxes");
            searchingItem.setEnabled(false);
            boxeeMenu.add(searchingItem);
        } else {
            boxeeMenuMap = new HashMap<MenuItem,ServiceInfo>();
            
            // Create MenuItems for each found boxee
            for(Entry<String,ServiceInfo> entry : foundBoxees.entrySet()) {
                String name = entry.getKey();
                final ServiceInfo info = entry.getValue();
                
                if(info == null) {
                    // Generate a temporary 'resolving' MenuItem
                    MenuItem item = new MenuItem(name + " (resolving)");
                    item.setEnabled(false);
                    boxeeMenu.add(item);
                } else {
                    String ip = info.getInet4Addresses()[0].getHostAddress();
                    MenuItem item = new MenuItem(name + " at " + ip);
                    boxeeMenuMap.put(item, info);
                    item.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            connectToFromLabel((MenuItem)e.getSource());
                        }
                    });
                    boxeeMenu.add(item);
                }
            }
        }
    }
    
    //Obtain the image URL, taken from TrayIconDemo.java
    protected static Image createImage(String path, String description) {
        URL imageURL = TrakTheBoxeeTrayIcon.class.getResource(path);
        
        if (imageURL == null) {
            System.err.println("Resource not found: " + path);
            return null;
        } else {
            return (new ImageIcon( imageURL, description)).getImage();
        }
    }
    
    protected static Icon createIcon(String path, String description) {
        URL imageURL = TrakTheBoxeeTrayIcon.class.getResource(path);
        
        if (imageURL == null) {
            System.err.println("Resource not found: " + path);
            return null;
        } else {
            return new ImageIcon( imageURL, description);
        }
    }

    private void connectToFromLabel(MenuItem item) {
        // Clear the current connected Boxee if set
        if(connectedBoxeeItem != null) {
            connectedBoxeeItem.setLabel(connectedBoxeeItem.getLabel().substring(2));
        }
        // Append the * to the new item
        item.setLabel("* " + item.getLabel());
        connectedBoxeeItem = item;
        // Grab the ServiceInfo to use
        ServiceInfo info = boxeeMenuMap.get(item);
        // And start the actual connection
        connectTo(info.getInet4Addresses()[0], info.getPort());
    }
    
    private void connectTo(Inet4Address address, int port) {
        _log.log(Level.INFO, "Connecting to " + address.getHostAddress() + ", port " + port);
        // If we're already connected, disconnect
        if(boxee != null) {
            boxee.stop();
            boxee = null;
        }
        // Otherwise, just start a new connection
        String hostname = "localhost";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            Logger.getLogger(TrakTheBoxeeTrayIcon.class.getName()).log(Level.WARNING, "Couldn't find local hostname", ex);
        }
        boxee = new BoxeeClient(address, port, hostname);
        boxee.addNotificationListener(this);
        boxee.addPasscodeRequestListener(this);
        boxee.addConnectedListener(this);
        boxee.start();
    }
    
    @Override
    public void serviceAdded(ServiceEvent se) {
        _log.log(Level.INFO, "Found a new Boxee: " + se.getName());
        // Store null for this instance, so that we can show it as resolving
        foundBoxees.put(se.getName(), null);
        // Refresh the menu
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setupBoxeeMenu();
            }
        });
        // And begin the resolution process for this instance
        se.getDNS().requestServiceInfo(se.getType(), se.getName());
    }

    @Override
    public void serviceRemoved(ServiceEvent se) {
        _log.log(Level.INFO, "Removed a Boxee: " + se.getName());
        // Remove the boxee from the list
        foundBoxees.remove(se.getName());
        // Refresh the menu
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setupBoxeeMenu();
            }
        });
    }

    @Override
    public void serviceResolved(ServiceEvent se) {
        _log.log(Level.INFO, "Resolved Boxee " + se.getName());
        // Store the new info
        foundBoxees.put(se.getName(), se.getInfo());
        // Refresh the menu
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setupBoxeeMenu();
            }
        });
    }

    @Override
    public void boxeeNotificationOccurred(BoxeeNotificationEvent event) {
        _log.log(Level.INFO, "Received Boxee notification: " + event.type);
        if(event.type.equals("PlaybackStarted")) {
            // Start the timer at 30 seconds, to allow for the video to spin up
            rescheduleTimer(30*1000);
        } else if(event.type.equals("PlaybackStopped")) {
            // Just stop the timer
            actionTimer.stop();
            // And cancel watching
            sendCancelWatching();
        } else if(event.type.equals("PlaybackEnded")) {
            // Double check that we have scrobbled
            if(!hasBeenScrobbled && lastReportData != null) {
                // If not, scrobble it now
                sendScrobble(lastReportData);
            }
            // And kill the timer
            actionTimer.stop();
        } else if(event.type.equals("PlaybackSeek")) {
            // Schedule the timer for 30 seconds in the future, so that we don't generate thousands of events
            rescheduleTimer(30*1000);
        } else if(event.type.equals("ApplicationStop")) {
            // TODO: Decide how to handle application quitting
        }
    }

    @Override
    public String boxeePasscodeRequestOccurred(EventObject event) {
        _log.log(Level.INFO, "Received Boxee passcode request");
        Icon icon = TrakTheBoxeeTrayIcon.createIcon("images/trakt-128.png", "TrakTheBoxee");
        return (String)JOptionPane.showInputDialog(null, "Please enter the passcode displayed on your TV", "TrakTheBoxee", 0, icon, null, "");
    }

    @Override
    public void boxeeConnected(EventObject event) {
        _log.log(Level.INFO, "Connected to Boxee");
        // Start the timer func at 30 seconds, so that we scrobble whatever's playing at spin-up
        rescheduleTimer(30*1000);
        // Also grab the boxee version info and pass that to the Trakt client
        Map boxeeVersion = boxee.getBoxeeVersion();
        if(trakt != null) {
            trakt.setBoxeeDetails((String)boxeeVersion.get("System.BuildVersion"), (String)boxeeVersion.get("System.BuildDate"));
        }
    }
    
    protected void timerFunc() {
        _log.log(Level.INFO, "Timer function running");
        // Fetch the data from the client
        Map boxeeData = boxee.getCurrentlyPlaying();
        
        // If the playing file has changed, mark it as not scrobbled
        if(lastReportData != null) {
            if(!lastReportData.get("title").equals(boxeeData.get("title"))) {
                _log.log(Level.INFO, "Video file has changed. Resetting has-scrobbled");
                hasBeenScrobbled = false;
            }
        }
        lastReportData = boxeeData;
        if(boxeeData.get("type").equals("none")) {
            _log.log(Level.INFO, "Nothing playing");
            // Nothing to do, just reschedule and return
            // Go for 10 minutes between checks if the boxee is idle
            rescheduleTimer(10*60*1000);
            return;
        }
        
        int progress = (Integer)boxeeData.get("progress");
        long duration = (Long)boxeeData.get("duration");
        long time = (Long)boxeeData.get("time");
        long timeLeft = duration-time;
        
        if(timeLeft < 300 || progress > 95) {
            sendScrobble(boxeeData);
        } else {
            sendReport(boxeeData);
        }
        lastReportData = boxeeData;
        // Reschedule the timer for 3 minutes' time from now
        rescheduleTimer(3*60*1000);
    }
    
    protected void sendReport(Map showData) {
        // Report throttling
        // Only report a maximum of every 10 minutes
        Calendar now = Calendar.getInstance();
        Calendar then = (Calendar)lastReport.clone();
        then.add(Calendar.MINUTE, 10);
        if(now.before(then)) {
            // Too soon, return
            _log.log(Level.INFO, "Not sending report - sent one too recently");
            return;
        }
        // Report what we're watching
        if(trakt == null) {
            _log.log(Level.INFO, "Not sending report - no Trakt API available");
            return;
        }
        // And send the report
        long duration = (Long)showData.get("duration");
        int progress = (Integer)showData.get("progress");
        
        duration /= 60;
        
        _log.log(Level.INFO, "Sending report");
        if(showData.get("type").equals("tv")) {
            int year = Integer.parseInt((String)showData.get("year"));
            int season = Integer.parseInt((String)showData.get("season"));
            int episode = Integer.parseInt((String)showData.get("episode"));
            trakt.watchingShow((String)showData.get("title"), year, season, episode, (int)duration, progress);
        } else if(showData.get("type").equals("movie")) {
            int year = Integer.parseInt((String)showData.get("year"));
            trakt.watchingMovie((String)showData.get("title"), year, (int)duration, progress);
        }
        // Store the new latest report
        lastReport = now;
    }
    
    protected void sendScrobble(Map showData) {
        if(hasBeenScrobbled) {
            _log.log(Level.INFO, "Already scrobbled this file");
            return;
        }
        // Perform the scrobble
        if(trakt != null) {
            _log.log(Level.INFO, "Sending scrobble");
            // And send the report
            long duration = (Long)showData.get("duration");
            int progress = (Integer)showData.get("progress");

            duration /= 60;

            if(showData.get("type").equals("tv")) {
                int year = Integer.parseInt((String)showData.get("year"));
                int season = Integer.parseInt((String)showData.get("season"));
                int episode = Integer.parseInt((String)showData.get("episode"));
                trakt.scrobbleShow((String)showData.get("title"), year, season, episode, (int)duration, progress);
                // Update the latest scrobbled, just debug info for now
                scrobbledItem.setLabel("Scrobbled " + (String)showData.get("title"));
            } else if(showData.get("type").equals("movie")) {
                int year = Integer.parseInt((String)showData.get("year"));
                trakt.scrobbleMovie((String)showData.get("title"), year, (int)duration, progress);
                scrobbledItem.setLabel("Scrobbled " + (String)showData.get("title"));
            } else {
                _log.info("Not scrobbling - " + (String)showData.get("title") + " doesn't appear to be a TV show or movie");
            }
        } else {
            _log.log(Level.INFO, "Not scrobbling, no Trakt API available");
        }
        // And record this as lastScrobbled, to avoid re-scrobbling
        hasBeenScrobbled = true;
    }
    
    protected void sendCancelWatching() {
        _log.log(Level.INFO, "Cancelling watching");
        if(lastReportData != null && trakt != null) {
            if(lastReportData.get("type").equals("tv")) {
                trakt.cancelWatchingShow();
            } else {
                trakt.cancelWatchingMovie();
            }
        }
    }
}
