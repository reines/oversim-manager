var socket = null;

$(document).ready(function() {
	try {
		// Attempt to open websocket connection
		socket = new WebSocket('ws://' + window.location.hostname + ':' + window.location.port + '/ws');

		socket.onopen = onOpen;

		socket.onmessage = function(message) {
			var data = JSON.parse(message.data);
			onMessage(data.webuiCommand, data);
		};

		socket.onclose = onClose;
	}
	catch (exception) {
		onError(exception);
	}
});

/* general functions */

function onOpen() {

}

function onClose() {

}

function onError(exception) {

}

function onMessage(type, data) {
	switch (type) {
		case 'ADDED_CONFIG': return onAddedConfig(data.config, data.totalRunCount, data.resultDir);
		case 'COMPLETED_CONFIG': return onCompletedConfig(data.config);
		case 'STARTED_RUN': return onStartedRun(data.config, data.run);
		case 'COMPLETED_RUN': return onCompletedRun(data.config, data.run);
		case 'FAILED_RUN': return onFailedRun(data.config, data.run);
		case 'SHUTDOWN': return onShutdown();
	}

	return null;
}

function sendMessage(type) {
	sendMessage(type, {});
}

function sendMessage(type, data) {
	// Check we have a working connection
	if (socket == null || socket.readyState != WebSocket.OPEN)
		return;

	data.webuiCommand = type;

	var message = JSON.stringify(data);
	socket.send(message);
}

/* server commands */

function onAddedConfig(configName, totalRunCount, resultDir) {

}

function onCompletedConfig(configName) {

}

function onStartedRun(configName, runId) {

}

function onCompletedRun(configName, runId) {

}

function onFailedRun(configName, runId) {

}

function onShutdown() {

}

/* client commands */

function sendShutdown(graceful) {
	sendMessage('SHUTDOWN', {graceful: graceful});
}
