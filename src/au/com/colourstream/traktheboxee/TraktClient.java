/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.com.colourstream.traktheboxee;

import com.jakewharton.trakt.ServiceManager;
import com.jakewharton.trakt.enumerations.Rating;
import com.jakewharton.trakt.services.ShowService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.logging.*;

/**
 *
 * @author predakanga
 */
public class TraktClient {
    private ServiceManager manager;
    
    public TraktClient() {
        manager = new ServiceManager();
        manager.setApiKey(TrakTheBoxee.apiKey);
        Preferences prefs = Preferences.userNodeForPackage(this.getClass());
        String user = prefs.get("username", "");
        String pass = prefs.get("password", "");
        
        manager.setAuthentication(user, pass);
    }
    
    public void setBoxeeDetails(String boxeeVersion, String boxeeBuildDate) {
        manager.setDebugInfo(TrakTheBoxee.version, boxeeVersion, boxeeBuildDate);
    }
    
    public void scrobbleShow(String show, int year, int season, int episode, int duration, int progress) {
        manager.showService().scrobble(show, year).season(season).episode(episode).duration(duration).progress(progress).fire();
    }
    
    public void scrobbleMovie(String title, int year, int duration, int progress) {
        manager.movieService().scrobble(title, year).duration(duration).progress(progress).fire();
    }
    
    public void watchingShow(String show, int year, int season, int episode, int duration, int progress) {
        manager.showService().watching(show, year).season(season).episode(episode).duration(duration).progress(progress).fire();
    }
    
    public void watchingMovie(String title, int year, int duration, int progress) {
        manager.movieService().watching(title, year).duration(duration).progress(progress).fire();
    }
    
    public void cancelWatchingShow() {
        manager.showService().cancelWatching().fire();
    }
    
    public void cancelWatchingMovie() {
        manager.movieService().cancelWatching().fire();
    }
    
    public void rateShow(String show, int year, int season, int episode, boolean liked) {
        Rating rating = liked ? Rating.Love : Rating.Hate;
        manager.rateService().episode(show, year).season(season).episode(episode).rating(rating).fire();
    }
    
    public void rateMovie(String title, int year, boolean liked) {
        Rating rating = liked ? Rating.Love : Rating.Hate;
        manager.rateService().movie(title, year).rating(rating).fire();
    }
}
