package com.jamierf.oversim.manager.webui.handler;

import com.jamierf.oversim.manager.Manager;
import com.jamierf.oversim.manager.webui.ClientCommand;
import com.jamierf.oversim.manager.webui.ServerCommand;

public class ShutdownHandler implements ClientCommandHandler {

	protected final Manager manager;

	public ShutdownHandler(Manager manager) {
		this.manager = manager;
	}

	@Override
	public ServerCommand handleCommand(ClientCommand command) throws Exception {
		// Request the manager shutdown
		manager.shutdown();

		return null; // No response required
	}
}
