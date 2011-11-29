package com.jamierf.oversim.manager.webui;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.webbitserver.WebServer;
import org.webbitserver.WebServers;
import org.webbitserver.handler.StaticFileHandler;

import com.jamierf.oversim.manager.Manager;
import com.jamierf.oversim.manager.webui.handler.ClientCommandHandler;
import com.jamierf.oversim.manager.webui.handler.ShutdownHandler;

public class WebUI {

	protected final WebServer server;
	protected final Manager manager;
	protected final StaticFileHandler staticHandler;
	protected final WebHandler socketHandler;
	protected final Map<ClientCommand.Type, ClientCommandHandler> handlers;

	public WebUI(int port, Manager manager) {
		this.manager = manager;

		server = WebServers.createWebServer(port);

		staticHandler = new StaticFileHandler("./webui/");
		server.add(staticHandler);

		socketHandler = new WebHandler(this);
		server.add("/ws", socketHandler);

		handlers = new HashMap<ClientCommand.Type, ClientCommandHandler>();

		handlers.put(ClientCommand.Type.SHUTDOWN, new ShutdownHandler(manager));
	}

	public void start() throws IOException {
		server.start();
	}

	public void sendMessage(ServerCommand command) {
		final String message = command.toString();

		socketHandler.broadcast(message);
	}

	public ServerCommand onOpen() {
		final ServerCommand reply = new ServerCommand(ServerCommand.Type.NEW_CONNECTION);

		reply.add("output", manager.getBuffer());
		reply.add("paused", manager.isPaused());

		return reply;
	}

	public ServerCommand receiveMessage(ClientCommand command) throws Exception {
		final ClientCommandHandler handler = handlers.get(command.type);
		if (handler == null)
			return null;

		return handler.handleCommand(command);
	}

	public void stop() throws IOException {
		server.stop();
	}

	public URI getUri() {
		return server.getUri();
	}
}
