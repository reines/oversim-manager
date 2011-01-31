package com.jamierf.oversim.manager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SimulationConfig extends DataSet {

	protected final String configFile;
	protected final String configName;
	protected final File resultDir;
	protected final File logDir;
	protected final Map<String, String> parameters;

	public SimulationConfig(String configFile, String configName, File resultRootDir, Map<String, String> globalParameters) throws IOException {
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
	}

	public String getFile() {
		return configFile;
	}

	public String getName() {
		return configName;
	}

	public File getResultDir() {
		return resultDir;
	}

	public File getLogDir() {
		return logDir;
	}

	public Map<String, String> getParameters() {
		return parameters;
	}

	@Override
	public String toString() {
		return "SimulationConfig(file = '" + configFile + "'; name = '" + configName + "';)";
	}
}
