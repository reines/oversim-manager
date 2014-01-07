package com.jamierf.oversim.manager.runnable;

import com.google.common.collect.ImmutableSet;
import com.jamierf.oversim.manager.SimulationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SimulationRun implements Run {

	private static final Logger logger = LoggerFactory.getLogger(SimulationRun.class);
    private static final Set<String> RESULT_EXTENSIONS = ImmutableSet.of("sca", "vci", "vec");

	protected final int runId;
	protected final File workingDir;
	protected final File overSim;
	protected final SimulationConfig config;

	public SimulationRun(int runId, File workingDir, File overSim, SimulationConfig config) {
		this.runId = runId;
		this.workingDir = workingDir;
		this.overSim = overSim;
		this.config = config;
	}

    @Override
	public SimulationConfig getConfig() {
		return config;
	}

	public int getRunId() {
		return runId;
	}

    public boolean resultsExist() {
        for (String ext : RESULT_EXTENSIONS) {
            final File file = new File(config.getResultDir(), String.format("%s-%d.%s", config.getName(), runId, ext));
            if (!file.exists()) {
                return false;
            }
        }

        return true;
    }

	@Override
	public void run() {
		try {
			final List<String> command = new LinkedList<String>();

			final String osName = System.getProperty("os.name").toLowerCase();
			// If we are on linux, unix, or mac then run using nice
			if (osName.contains("nix") || osName.contains("nux") || osName.contains("mac")) {
				command.add("nice");
            }

			command.add(overSim.getCanonicalPath());
			command.add("-f" + config.getFile());
			command.add("-c" + config.getName());
			command.add("-uCmdenv");
			command.add("-r" + runId);

			// Append any special parameters
			for (Map.Entry<String, String> entry : config.getParameters().entrySet()) {
				command.add("--" + entry.getKey() + "=" + entry.getValue());
            }

			// Execute OverSim
			final ProcessBuilder processBuilder = new ProcessBuilder(command);
			processBuilder.directory(workingDir);
			processBuilder.redirectErrorStream(true);

			final Process process = processBuilder.start();

			// If we have a log directory then lets save a log
			if (config.getLogDir() != null) {
				final File logFile = new File(config.getLogDir(), "run" + runId + ".log");

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
			final int result = process.waitFor();
			if (result != 0) {
				throw new RuntimeException("OverSim run " + runId + " exited with result code: " + result);
            }
		}
		catch (Exception e) {
			if (logger.isWarnEnabled())
				logger.warn("Error running simulation", e);
		}
	}

	@Override
	public String toString() {
		return "SimulationRun(name = '" + config.getName() + "'; id = " + runId + ";)";
	}
}
