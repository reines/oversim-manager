var socket = null;
var queue = null;
var output = null;
var paused = false;
var info = {};

function htmlencode(str) {
	if (typeof str != 'string')
		return str;

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

	queue = $('#queue');
	queue.tablesorter({
		headers: {
			4: { sorter: false }
		}
	});

	output = $('#output>pre');

	info.status = $('#info .info-status>span');
	info.load = $('#info .info-load>span');
	info.runs = $('#info .info-runs>span');

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

function addConfigRow(config, runID, status, resultDir) {
	var row = $('<tr>')
	.append($('<td>' + htmlencode(config) + '</td>'))
	.append($('<td>' + htmlencode(runID) + '</td>'))
	.append($('<td>' + htmlencode(status) + '</td>'))
	.append($('<td>' + htmlencode(resultDir) + '</td>'));

	queue.find('tbody').append(row);

	queue.trigger('update');
	queue.trigger('sort');
}

function setStatus() {
	var playpause = $('nav .button-playpause');

	if (socket == null || socket.readyState != WebSocket.OPEN) {
		info.status.attr('style', 'color: red');
		info.status.html('terminated');

		playpause.parent().remove();

		return;
	}

	info.status.attr('style', 'color: ' + (paused ? 'orange' : 'green'));
	info.status.html(paused ? 'paused' : 'running');

	playpause.attr('title', paused ? 'Start' : 'Pause');

	playpause = playpause.find('img');
	playpause.attr('src', 'images/' + (paused ? 'play' : 'pause') + '.png');
	playpause.attr('alt', paused ? 'Start' : 'Pause');
}

function onOpen() {
	println('Connected to OverSim-Manager.', 'green');
}

function onClose() {
	println('Connection to OverSim-Manager lost!', 'red');
	socket = null;
}

function onError(exception) {
	println('Connection error: ' + exception.message, 'red');
}

function onMessage(type, data) {
	switch (type) {
		case 'NEW_CONNECTION':	return onNewConnection(data.output, data.paused);
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
	// TODO: Scroll down
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

function onNewConnection(output, paused) {
	println(output);

	this.paused = paused;
	setStatus();

	addConfigRow('KademliaTest0', 1, 'test', '/home/jamie/Data/test-435435/');
	addConfigRow('KademliaTest1', 0, 'test', '/home/jamie/Data/test-435435/');
}

function onAddedConfig(configName, totalRunCount, resultDir) {
	for (var runID = 0;runID < totalRunCount;runID++)
		addConfigRow(configName, runID, 'test', resultDir);
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
	println(line);
}

function onShutdown() {
	if (socket != null) {
		socket.close();
		socket = null;
	}

	setStatus();
}

/* client commands */

function sendShutdown() {
	println('Sending shutdown request.', 'yellow');
	sendMessage('SHUTDOWN');
}
