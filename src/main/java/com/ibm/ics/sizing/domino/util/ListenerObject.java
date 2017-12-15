package com.ibm.ics.sizing.domino.util;

import java.util.logging.Logger;

import org.ehcache.event.CacheEvent;
import org.ehcache.event.CacheEventListener;

import com.google.gson.JsonObject;

/**
 * 
 * @author skipper
 *
 */
public class ListenerObject implements CacheEventListener<String, JsonObject> {
	
	private static final boolean DEBUG = Boolean.parseBoolean(System.getenv("DOMINO_SIZING_SERVICE_LOG"));
	private static Logger logger = Logger.getLogger(ListenerObject.class.getName());
	
	public void onEvent(CacheEvent<? extends String, ? extends JsonObject> event) {
	    if (DEBUG)
	    	logger.info(event.getOldValue().get("_id") + " has expired from cache");
	}
}