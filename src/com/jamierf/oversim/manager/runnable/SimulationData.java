package com.jamierf.oversim.manager.runnable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.jamierf.oversim.manager.Manager;

public class SimulationData implements Runnable {

	protected final Manager manager;
	protected final File resultDir;
	protected final String configName;
	protected final int runId;
	protected final String[] wantedScalars;

	public SimulationData(Manager manager, File resultDir, String configName, int runId, String[] wantedScalars) {
		this.manager = manager;
		this.resultDir = resultDir;
		this.configName = configName;
		this.runId = runId;
		this.wantedScalars = wantedScalars;
	}

	@Override
	public void run() {
		try {
			File sca = new File(resultDir, configName + "-" + runId + ".sca");
			if (!sca.exists())
				throw new FileNotFoundException("Unable to find scalar results for: " + configName + "(" + runId + ")");

			Map<String, String> attributes = new HashMap<String, String>();
			SortedMap<String, String> scalars = new TreeMap<String, String>();

			BufferedReader in = null;

			try {
				in = new BufferedReader(new FileReader(sca));

				// Read the version number and check it is what we expect
				String version = in.readLine();
				if (!version.matches("^version\\s2$"))
					throw new RuntimeException("Unrecognised scalar result file version.");

				@SuppressWarnings("unused")
				String runIdentifier = in.readLine();

				// Read the header
				Pattern headerPattern = Pattern.compile("^attr\\s+(\\w+)\\s+(.+)$");
				for (String line;(line = in.readLine()) != null;) {
					// When we reach an empty line it signifies the end of the header
					if (line.isEmpty())
						break;

					Matcher m = headerPattern.matcher(line);
					if (!m.find())
						continue;

					attributes.put(m.group(1), m.group(2));
				}

				// Confirm we have iterationvars, otherwise we don't have anything to use as a unique identifier
				if (!attributes.containsKey("iterationvars"))
					throw new RuntimeException("Malformed scalar file, no iterationvars found: " + sca.getAbsolutePath());

				// Read the scalars
				Pattern scalarPattern = Pattern.compile("^scalar\\s+([\\w\\.]+)\\s+\"(" + StringUtils.join(wantedScalars, '|') + ")\"\\s+(.+)$");
				for (String line;(line = in.readLine()) != null;) {
					// When we reach an empty line it signifies the end of the scalars
					if (line.isEmpty())
						break;

					Matcher m = scalarPattern.matcher(line);
					if (!m.find())
						continue;

					// Record the scalar
					scalars.put(m.group(2), m.group(3));
				}

				// Add the iterationvars as scalars
				Matcher m = Pattern.compile("\\$([^=]+)=([^,\"]+)(?:[,\"]|$)").matcher(attributes.get("iterationvars"));
				while (m.find())
					scalars.put(m.group(1), m.group(2));

				// TODO: Handle any vectors?
			}
			finally {
				if (in != null)
					in.close();
			}

			// Hand the data back to the manager, using the iterationvars as a unique identifier so that all repetitions are grouped together
			manager.mergeData(attributes.get("iterationvars"), scalars);
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public String toString() {
		return "SimulationData(name = '" + configName + "'; id = " + runId + ";)";
	}
}
