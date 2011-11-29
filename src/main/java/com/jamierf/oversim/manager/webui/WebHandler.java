package com.jamierf.oversim.manager.webui;

import java.util.HashSet;
import java.util.Set;

import org.webbitserver.WebSocketConnection;
import org.webbitserver.WebSocketHandler;

public class WebHandler implements WebSocketHandler {

	protected final WebUI web;
	protected final Set<WebSocketConnection> clients;

	public WebHandler(WebUI web) {
		this.web = web;

		clients = new HashSet<WebSocketConnection>();
	}

	public synchronized void broadcast(String message) {
		for (WebSocketConnection client : clients)
			client.send(message);
	}

	@Override
	public synchronized void onClose(WebSocketConnection connection) throws Exception {
		clients.remove(connection);
	}

	@Override
	public synchronized void onMessage(WebSocketConnection connection, String message) throws Exception {
		try{
			final ClientCommand command = new ClientCommand(message);

			// Pass the command to the WebUI
			final ServerCommand reply = web.receiveMessage(command);
			if (reply != null)
				connection.send(reply.toString());
		}
		catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
	}

	@Override
	public synchronized void onOpen(WebSocketConnection connection) throws Exception {
		clients.add(connection);

		final ServerCommand reply = web.onOpen();
		if (reply != null)
			connection.send(reply.toString());
	}
}
