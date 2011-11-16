/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.com.colourstream.traktheboxee;

import com.nbarraille.jjsonrpc.JJsonPeer;
import com.nbarraille.jjsonrpc.RemoteError;
import com.nbarraille.jjsonrpc.TcpClient;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EventListener;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.event.EventListenerList;

/**
 *
 * @author predakanga
 */
public class BoxeeClient {
    public class BoxeeNotificationEvent extends EventObject {
        public String type;
        
        public BoxeeNotificationEvent(Object source, String kType) {
            super(source);
            type = kType;
        }
    }
    
    public interface BoxeeNotificationListener extends EventListener {
        public void boxeeNotificationOccurred(BoxeeNotificationEvent event);
    }
    
    public interface BoxeePasscodeRequestListener extends EventListener {
        public String boxeePasscodeRequestOccurred(EventObject event);
    }
    
    public class BoxeeApi {
        protected EventListenerList m_notificationListeners = new EventListenerList();
        protected BoxeeClient client;

        public BoxeeApi(BoxeeClient kClient) {
            client = kClient;
        }
        
        public void addNotificationListener(BoxeeNotificationListener listener) {
            m_notificationListeners.add(BoxeeNotificationListener.class, listener);
        }

        public void removeNotificationListener(BoxeeNotificationListener listener) {
            m_notificationListeners.remove(BoxeeNotificationListener.class, listener);
        }
        
        protected void fireNotification(BoxeeNotificationEvent evt) {
            BoxeeNotificationListener[] listeners = m_notificationListeners.getListeners(BoxeeNotificationListener.class);
            for(BoxeeNotificationListener listener : listeners) {
                listener.boxeeNotificationOccurred(evt);
            }
        }
    
        public void Announcement(Map kwArgs) {
            String message = (String)kwArgs.get("message");
            String sender = (String)kwArgs.get("sender");
            fireNotification(new BoxeeNotificationEvent(client, message));
        }
    }
    
    public void addNotificationListener(BoxeeNotificationListener listener) {
        m_api.addNotificationListener(listener);
    }
    
    public void removeNotificationListener(BoxeeNotificationListener listener) {
        m_api.removeNotificationListener(listener);
    }
    
    protected EventListenerList m_listeners = new EventListenerList();
    
    public void addPasscodeRequestListener(BoxeePasscodeRequestListener listener) {
        m_listeners.add(BoxeePasscodeRequestListener.class, listener);
    }

    public void removePasscodeRequestListener(BoxeePasscodeRequestListener listener) {
        m_listeners.remove(BoxeePasscodeRequestListener.class, listener);
    }
    
    protected String firePasscodeRequest() {
        BoxeePasscodeRequestListener[] listeners = m_listeners.getListeners(BoxeePasscodeRequestListener.class);
        String toRet = null;
        for(BoxeePasscodeRequestListener listener : m_listeners.getListeners(BoxeePasscodeRequestListener.class)) {
            String intermediaryPasscode = listener.boxeePasscodeRequestOccurred(new EventObject(this));
            // Do some basic validation
            if(intermediaryPasscode.length() != 4) {
                continue;
            }
            if(!intermediaryPasscode.matches("[0-9]{4}")) {
                continue;
            }
            // Store in toRet if we got a valid string
            toRet = intermediaryPasscode;
        }
        
        if(toRet != null) {
            return toRet;
        } else {
            // Recurse if we didn't get a valid passcode
            return firePasscodeRequest();
        }
    }
    
    public interface BoxeeConnectedListener extends EventListener {
        public void boxeeConnected(EventObject event);
    }
    
    public void addConnectedListener(BoxeeConnectedListener listener) {
        m_listeners.add(BoxeeConnectedListener.class, listener);
    }
    
    public void removeConnectedListener(BoxeeConnectedListener listener) {
        m_listeners.remove(BoxeeConnectedListener.class, listener);
    }
    
    protected void fireConnected() {
        for(BoxeeConnectedListener listener : m_listeners.getListeners(BoxeeConnectedListener.class)) {
            listener.boxeeConnected(new EventObject(this));
        }
    }
    
    TcpClient m_client = null;
    JJsonPeer m_peer = null;
    BoxeeApi m_api = new BoxeeApi(this);
    String m_clientID = null;
    String m_appID = "TrakTheBoxee";
    String m_appLabel = "TrakTheBoxee";
    String m_appIcon = "";
    String m_appType = "other";
    
    
    public BoxeeClient(Inet4Address host, int port, String identifier) {
        m_clientID = "TTB" + identifier;
        
        try {
            m_client = new TcpClient(host.getHostAddress(), port, m_api);
        } catch (UnknownHostException ex) {
            Logger.getLogger(BoxeeClient.class.getName()).log(Level.SEVERE, "Couldn't resolve " + host.getHostAddress(), ex);
        } catch (IOException ex) {
            Logger.getLogger(BoxeeClient.class.getName()).log(Level.SEVERE, "Couldn't connect to " + host.getHostAddress(), ex);
        }
        m_peer = m_client.getPeer();
    }
    
    public void start() {
        m_peer.start();
        tryToConnect();
    }
    
    public void stop() {
        m_peer.stop();
    }
    
    protected long timeStrToSeconds(String timeStr) {
        String[] timeParts = timeStr.split(":");
        long mult = 1, accum = 0;
        
        for(int i = timeParts.length-1; i >= 0; i--) {
            accum += (Integer.parseInt(timeParts[i])*mult);
            mult *= 60;
        }
        
        return accum;
    }

    private void tryToConnect() {
        // Attempt to connect to the device
        Object connectResp = m_peer.sendSyncRequest("Device.Connect", Arrays.asList(m_clientID), true);
        if(connectResp instanceof RemoteError) {
            // If we get an error on Connect, we need to start the pairing process
            pairDevice();
        }
        // Otherwise, we're all done
        fireConnected();
    }

    private void pairDevice() {
        Object ignoredResp = m_peer.sendSyncRequest("Device.PairChallenge", Arrays.asList(m_clientID, m_appID, m_appLabel, m_appIcon, m_appType), true);
        if((ignoredResp instanceof RemoteError)) {
            try {
                // If we couldn't send the pair request, wait 30 seconds and try again
                Thread.sleep(30000);
            } catch (InterruptedException ex) {
                Logger.getLogger(BoxeeClient.class.getName()).log(Level.INFO, "Sleep was interrupted", ex);
            }
            pairDevice();
        }
        boolean succeeded = false;
        while(!succeeded) {
            String pin = firePasscodeRequest();
            // Try to authenticate with it
            Object pairResp = m_peer.sendSyncRequest("Device.PairResponse", Arrays.asList(m_clientID, pin), true);
            if(pairResp instanceof RemoteError) {
                succeeded = false;
            } else {
                succeeded = true;
            }
        }
        // If we get here, we're successfully authenticated. Log it.
        fireConnected();
        
    }
    
    public Map getCurrentlyPlaying() {
        List requestedLabels = Arrays.asList("VideoPlayer.TVShowTitle", "VideoPlayer.Title", "VideoPlayer.Season", "VideoPlayer.Episode", "VideoPlayer.Year", "VideoPlayer.Time", "VideoPlayer.Duration");
        Map args = new HashMap<String,Object>();
        args.put("labels", requestedLabels);
        Map response = (Map)m_peer.sendSyncRequest("System.GetInfoLabels", args, true);
        
        HashMap retMap = new HashMap();
        if(response.get("VideoPlayer.Title").equals("")) {
            retMap.put("type", "none");
            return retMap;
        } else {
            // Calculate the length and progress
            long duration = timeStrToSeconds((String)response.get("VideoPlayer.Duration"));
            long time = timeStrToSeconds((String)response.get("VideoPlayer.Time"));
            int progress = (int)((time*100)/duration);
            
            retMap.put("duration", duration);
            retMap.put("time", time);
            retMap.put("progress", progress);
            
            if(!response.get("VideoPlayer.TVShowTitle").equals("")) {
                retMap.put("type", "tv");
                retMap.put("title", response.get("VideoPlayer.TVShowTitle"));
                retMap.put("season", response.get("VideoPlayer.Season"));
                retMap.put("episode", response.get("VideoPlayer.Episode"));
                retMap.put("episode_title", response.get("VideoPlayer.Title"));
                retMap.put("year", response.get("VideoPlayer.Year"));
            } else if(!response.get("VideoPlayer.Year").equals("")) {
                retMap.put("type", "movie");
                retMap.put("title", response.get("VideoPlayer.Title"));
                retMap.put("year", response.get("VideoPlayer.Year"));
            } else {
                retMap.put("type", "file");
                retMap.put("title", response.get("VideoPlayer.Title"));
            }
        }
        return retMap;
    }
    
    public Map getBoxeeVersion() {
        List requestedLabels = Arrays.asList("System.BuildDate", "System.BuildVersion");
        Map args = new HashMap<String,Object>();
        args.put("labels", requestedLabels);
        Map response = (Map)m_peer.sendSyncRequest("System.GetInfoLabels", args, true);
        
        return response;
    }
}
