package com.jamierf.oversim.manager.runnable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.time.DurationFormatUtils;

public class SimulationRun implements Runnable {

	protected final String configFile;
	protected final String configName;
	protected final int runId;
	protected final File logDir;
	protected final File workingDir;
	protected final Map<String, String> parameters;
	protected final File overSim;

	public SimulationRun(String configFile, String configName, int runId, File logDir, File workingDir, Map<String, String> parameters, File overSim) {
		this.configFile = configFile;
		this.configName = configName;
		this.runId = runId;
		this.logDir = logDir;
		this.workingDir = workingDir;
		this.parameters = parameters;
		this.overSim = overSim;
	}

	public String getConfigFile() {
		return configFile;
	}

	public String getConfigName() {
		return configName;
	}

	public int getRunId() {
		return runId;
	}

	@Override
	public void run() {
		try {
			List<String> command = new LinkedList<String>();

			command.add(overSim.getAbsolutePath());
			command.add("-f" + configFile);
			command.add("-c" + configName);
			command.add("-uCmdenv");
			command.add("-r" + runId);

			// Append any special parameters
			for (Map.Entry<String, String> entry : parameters.entrySet())
				command.add("--" + entry.getKey() + "=" + entry.getValue());

			// Execute OverSim
			ProcessBuilder processBuilder = new ProcessBuilder(command);
			processBuilder.directory(workingDir);
			processBuilder.redirectErrorStream(true);

			long startTime = System.currentTimeMillis();

			Process process = processBuilder.start();

			// If we have a log directory then lets save a log
			if (logDir != null) {
				File logFile = new File(logDir, "run" + runId + ".log");

				BufferedReader in = null;
				PrintWriter out = null;

				try {
					in = new BufferedReader(new InputStreamReader(process.getInputStream()));
					out = new PrintWriter(new FileWriter(logFile), true);

					for (String line;(line = in.readLine()) != null;)
						out.println(line);
				}
				finally {
					if (in != null)
						in.close();

					if (out != null)
						out.close();
				}
			}

			// Wait for the process to end (if we were logging it already has, but thats fine)
			int result = process.waitFor();
			if (result != 0)
				throw new RuntimeException("OverSim run " + runId + " exited with result code: " + result);

			System.out.println(this + " completed in " + DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, true) + ".");
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public String toString() {
		return "SimulationRun(name = '" + configName + "'; id = " + runId + ";)";
	}
}
