var socket = null;
var output = null;

function htmlencode(str) {
	return str
	.replace(/&/g, "&amp;")
	.replace(/</g, "&lt;")
	.replace(/>/g, "&gt;")
	.replace(/"/g, "&quot;")
	.replace(/'/g, "&#039;");
}

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

	output = $('#output>pre');

	// Cancel button
	$('nav .button-cancel').click(function() {
		if (confirm('Are you sure you want to shut down OverSim-Manager and all running simulations?'))
			sendShutdown();

		return false;
	});

	// Start button
	// Pause button
	// Config button
	// Find button
});

/* general functions */

function onOpen() {
	println('Connected to OverSim-Manager.', 'green');
}

function onClose() {
	println('Connection to OverSim-Manager lost!', 'red');
}

function onError(exception) {
	println('Connection error: ' + exception.message, 'red');
}

function onMessage(type, data) {
	switch (type) {
		case 'ADDED_CONFIG': return onAddedConfig(data.config, data.totalRunCount, data.resultDir);
		case 'COMPLETED_CONFIG': return onCompletedConfig(data.config);
		case 'STARTED_RUN': return onStartedRun(data.config, data.run);
		case 'COMPLETED_RUN': return onCompletedRun(data.config, data.run, data.duration);
		case 'FAILED_RUN': return onFailedRun(data.config, data.run);
		case 'DISPLAY_LOG': return onDisplayLog(data.line);
		case 'SHUTDOWN': return onShutdown();
	}

	return null;
}

function println(line) {
	println(line, 'inherit');
}

function println(line, color) {
	output.html(output.html() + '<span style="color: ' + color + '">' + htmlencode(line) + '</span>\n');
}

function sendMessage(type) {
	sendMessage(type, {});
}

function sendMessage(type, data) {
	// Check we have a working connection
	if (socket == null || socket.readyState != WebSocket.OPEN)
		return;

	if (typeof data == 'object')
		data.webuiCommand = type;
	else
		data = {webuiCommand: type};

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

function onCompletedRun(configName, runId, duration) {

}

function onFailedRun(configName, runId) {

}

function onDisplayLog(line) {
	println(line); // TODO: htmlencode?
}

function onShutdown() {

}

/* client commands */

function sendShutdown() {
	println('Sending shutdown request.', 'yellow');
	sendMessage('SHUTDOWN');
}
