package com.jamierf.oversim.manager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.time.DurationFormatUtils;

public class SimulationConfig extends DataSet {

	protected final String configFile;
	protected final String configName;
	protected final File resultDir;
	protected final File logDir;
	protected final Map<String, String> parameters;
	protected final long startTime;

	public int pendingRuns;
	public int completedRuns;
	public int failedRuns;

	public SimulationConfig(String configFile, String configName, File resultRootDir, Map<String, String> globalParameters, int pendingRuns) throws IOException {
		this.configFile = configFile;
		this.configName = configName;

		resultDir = new File(resultRootDir, configName + "-" + (System.currentTimeMillis() / 1000));
		if (!resultDir.mkdir())
			throw new RuntimeException("Unable to create result subdirectory.");

		logDir = new File(resultDir, "logs");
		if (!logDir.mkdir())
			throw new RuntimeException("Unable to create logs subdirectory.");

		// Create a map to hold overriding OverSim parameters for this specific config
		parameters = new HashMap<String, String>();
		parameters.putAll(globalParameters);

		// Add our new result directory as an override parameter
		parameters.put("result-dir", resultDir.getCanonicalPath());

		startTime = System.currentTimeMillis();

		this.pendingRuns = pendingRuns;

		completedRuns = 0;
		failedRuns = 0;
	}

	public String getFile() {
		return configFile;
	}

	public String getName() {
		return configName;
	}

	public File getLogDir() {
		return logDir;
	}

	public File getResultDir() {
		return resultDir;
	}

	public Map<String, String> getParameters() {
		return parameters;
	}

	public void processData(File resultRootDir) throws IOException {
		// Lets process the data
		System.out.println("-------------------------------------");

		// If we have any data, output a CSV of it
		if (super.hasData()) {
			System.out.println("Creating CSV file at: " + resultDir.getName() + ".csv");

			// Save the collated data to a CSV file
			super.writeCSV(new File(resultRootDir, resultDir.getName() + ".csv"));
		}

		System.out.println("Compressing raw data to: " + resultDir.getName() + ".tar.gz");

		// Save the raw results into an archive
		List<String> command = new LinkedList<String>();

		command.add("tar");
		command.add("-czf");
		command.add(resultDir.getName() + ".tar.gz");
		command.add(resultDir.getName());

		Process process = new ProcessBuilder(command).directory(resultRootDir).start();

		try {
			process.waitFor();
		}
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Display a summary
		System.out.println("-------------------------------------");
		System.out.println("Config: " + this);
		System.out.println("Total duration: " + DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, true));
		System.out.println("Completed runs: " + completedRuns);
		System.out.println("Failed runs: " + failedRuns);

		System.out.println("-------------------------------------");
	}

	@Override
	public String toString() {
		return "SimulationConfig(file = '" + configFile + "'; name = '" + configName + "';)";
	}
}
