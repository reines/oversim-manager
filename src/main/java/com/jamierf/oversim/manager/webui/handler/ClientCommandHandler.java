package com.jamierf.oversim.manager.webui.handler;

import com.jamierf.oversim.manager.webui.ClientCommand;
import com.jamierf.oversim.manager.webui.ServerCommand;

public interface ClientCommandHandler {
	public ServerCommand handleCommand(ClientCommand command) throws Exception;
}
