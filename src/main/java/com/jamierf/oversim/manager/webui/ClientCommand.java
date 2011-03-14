package com.jamierf.oversim.manager.webui;

import java.util.Map;

import org.json.simple.JSONValue;

public class ClientCommand {

	public static enum Type {SHUTDOWN};

	public final Type type;
	protected final Map<String, Object> map;

	@SuppressWarnings("unchecked")
	public ClientCommand(String message) throws IllegalArgumentException {
		try {
			map = (Map<String, Object>) JSONValue.parse(message);
			type = Type.valueOf((String) map.get("webuiCommand"));
		}
		catch (ClassCastException e) {
			throw new IllegalArgumentException();
		}
		catch (NullPointerException e) {
			throw new IllegalArgumentException();
		}
	}

	public String getString(String key) { if (!map.containsKey(key)) return ""; return (String) map.get(key); }
	public int getInt(String key) { if (!map.containsKey(key)) return 0; return (Integer) map.get(key); }
	public long getLong(String key) { if (!map.containsKey(key)) return 0; return (Long) map.get(key); }
	public boolean getBoolean(String key) { if (!map.containsKey(key)) return false; return (Boolean) map.get(key); }

	public String toString() {
		return JSONValue.toJSONString(map);
	}
}
