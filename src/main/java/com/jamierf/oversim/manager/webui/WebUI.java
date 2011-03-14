package com.jamierf.oversim.manager.webui;

import java.io.IOException;
import java.net.URI;

import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.handler.StaticFileHandler;

import com.jamierf.oversim.manager.Manager;

public class WebUI {

	protected final WebServer server;
	protected final Manager manager;
	protected final StaticFileHandler staticHandler;
	protected final WebHandler socketHandler;

	public WebUI(int port, Manager manager) {
		this.manager = manager;

		server = WebServers.createWebServer(port);

		staticHandler = new StaticFileHandler("./webui/");
		server.add(staticHandler);

		socketHandler = new WebHandler(this);
		server.add("/ws", socketHandler);
	}

	public void start() throws IOException {
		server.start();
	}

	public void sendMessage(ServerCommand command) {
		String message = command.toString();

		socketHandler.broadcast(message);
	}

	public ServerCommand receiveMessage(ClientCommand command) {

		return null;
	}

	public void stop() throws IOException {
		server.stop();
	}

	public URI getUri() {
		return server.getUri();
	}
}
