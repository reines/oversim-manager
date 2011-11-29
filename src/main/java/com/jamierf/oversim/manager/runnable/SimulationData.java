package com.jamierf.oversim.manager.runnable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jamierf.oversim.manager.SimulationConfig;

public class SimulationData implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(SimulationData.class);

	protected final int runId;
	protected final String[] wantedScalars;
	protected final SimulationConfig config;

	public SimulationData(int runId, String[] wantedScalars, SimulationConfig config) {
		this.runId = runId;
		this.wantedScalars = wantedScalars;
		this.config = config;
	}

	public SimulationConfig getConfig() {
		return config;
	}

	@Override
	public void run() {
		try {
			final File sca = new File(config.getResultDir(), config.getName() + "-" + runId + ".sca");
			if (!sca.exists())
				throw new FileNotFoundException("Unable to find scalar results for: " + config.getName() + "(" + runId + ")");

			final Map<String, String> attributes = new HashMap<String, String>();
			final SortedMap<String, String> scalars = new TreeMap<String, String>();

			BufferedReader in = null;

			try {
				in = new BufferedReader(new FileReader(sca));

				// Read the version number and check it is what we expect
				final String version = in.readLine();
				if (!version.matches("^version\\s2$"))
					throw new RuntimeException("Unrecognised scalar result file version.");

				@SuppressWarnings("unused")
				final String runIdentifier = in.readLine();

				// Read the header
				final Pattern headerPattern = Pattern.compile("^attr\\s+(\\w+)\\s+(.+)$");
				for (String line;(line = in.readLine()) != null;) {
					// When we reach an empty line it signifies the end of the header
					if (line.isEmpty())
						break;

					final Matcher m = headerPattern.matcher(line);
					if (!m.find())
						continue;

					attributes.put(m.group(1), m.group(2));
				}

				// Confirm we have iterationvars, otherwise we don't have anything to use as a unique identifier
				if (!attributes.containsKey("iterationvars"))
					throw new RuntimeException("Malformed scalar file, no iterationvars found: " + sca.getCanonicalPath());

				// Read the scalars
				final Pattern scalarPattern = Pattern.compile("^scalar\\s+([\\w\\.\\d\\[\\]]+)\\s+\"(" + StringUtils.join(wantedScalars, '|') + ")\"\\s+(.+)$");
				for (String line;(line = in.readLine()) != null;) {
					// When we reach an empty line it signifies the end of the scalars
					if (line.isEmpty())
						break;

					final Matcher m = scalarPattern.matcher(line);
					if (!m.find())
						continue;

					// Record the scalar
					scalars.put(m.group(2), m.group(3));
				}

				// TODO: Handle any vectors?
			}
			finally {
				if (in != null)
					in.close();
			}

			// Hand the data back to the config, using the iterationvars as a unique identifier so that all repetitions are grouped together
			config.mergeData(attributes.get("iterationvars"), scalars);
		}
		catch (Exception e) {
			if (logger.isWarnEnabled())
				logger.warn("Error processing data", e);
		}
	}

	@Override
	public String toString() {
		return "SimulationData(name = '" + config.getName() + "'; id = " + runId + ";)";
	}
}
