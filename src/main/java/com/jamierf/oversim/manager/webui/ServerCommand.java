package com.jamierf.oversim.manager.webui;

import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONValue;

public class ServerCommand {

	public static enum Type {NEW_CONNECTION, ADDED_CONFIG, COMPLETED_CONFIG, STARTED_RUN, COMPLETED_RUN, FAILED_RUN, SHUTDOWN, DISPLAY_LOG};

	public final Type type;
	protected final Map<String, Object> map;

	public ServerCommand(Type type) {
		this.type = type;

		map = new HashMap<String, Object>();
		map.put("webuiCommand", type.toString());
	}

	public void add(String key, String value) { map.put(key, value); }	// String
	public void add(String key, int value) { map.put(key, value); }	// int
	public void add(String key, long value) { map.put(key, value); } // long
	public void add(String key, boolean value) { map.put(key, value); } // boolean)

	public String toString() {
		return JSONValue.toJSONString(map);
	}
}
