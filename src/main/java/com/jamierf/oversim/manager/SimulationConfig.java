package com.jamierf.oversim.manager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.time.DurationFormatUtils;

import com.jamierf.oversim.manager.util.DirectoryArchiver;

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

	public SimulationConfig(String configFile, String configName, File resultRootDir, String id) throws IOException {
		this.configFile = configFile;
		this.configName = configName;

		resultDir = new File(resultRootDir, configName + "-" + id);
		logDir = new File(resultDir, "logs");

		parameters = new HashMap<String, String>();

		// Add our result directory as an override parameter
		parameters.put("result-dir", resultDir.getCanonicalPath());

		startTime = System.currentTimeMillis();
		pendingRuns = 0;
		completedRuns = 0;
		failedRuns = 0;
	}

	public SimulationConfig(String configFile, String configName, File resultRootDir, Map<String, String> globalParameters, int pendingRuns) throws IOException {
		this.configFile = configFile;
		this.configName = configName;
		this.pendingRuns = pendingRuns;

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

	public void processData(Manager manager, File resultRootDir, DirectoryArchiver archiver, boolean deleteData) throws IOException {
		// Lets process the data
		manager.println("-------------------------------------");

		// If we have any data, output a CSV of it
		if (super.hasData()) {
			manager.println("Creating CSV file at: " + resultDir.getName() + ".csv");

			// Save the collated data to a CSV file
			super.writeCSV(new File(resultRootDir, resultDir.getName() + ".csv"));
		}

		// If we should compress the raw data, do it
		if (archiver != null) {
			manager.println("Compressing raw data to: " + resultDir.getName() + ".tar.gz");
			archiver.compress(resultDir, new File(resultRootDir, resultDir.getName() + ".tar.gz"));
		}

		// If we should delete the raw data, do it
		if (deleteData) {
			manager.println("Deleting raw data in: " + resultDir.getName());
			FileUtils.deleteDirectory(resultDir);
		}

		// Display a summary
		manager.println("-------------------------------------");
		manager.println("Config: " + this);
		manager.println("Total duration: " + DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, true));
		manager.println("Completed runs: " + completedRuns);
		manager.println("Failed runs: " + failedRuns);

		manager.println("-------------------------------------");
	}

	@Override
	public String toString() {
		return "SimulationConfig(file = '" + configFile + "'; name = '" + configName + "';)";
	}
}
