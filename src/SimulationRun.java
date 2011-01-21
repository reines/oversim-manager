import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SimulationRun {

	protected final String configFile;
	protected final String configName;
	protected final int runId;
	protected final File logDir;

	public SimulationRun(String configFile, String configName, int runId, File logDir) {
		this.configFile = configFile;
		this.configName = configName;
		this.runId = runId;
		this.logDir = logDir;
	}

	public int getRunId() {
		return runId;
	}

	public long run(File workingDir, HashMap<String, String> parameters, File overSim) throws IOException, InterruptedException {
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
			throw new RuntimeException("OverSim process exited with result code: " + result);

		return System.currentTimeMillis() - startTime;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SimulationRun))
			return false;

		SimulationRun run = (SimulationRun) o;
		return configFile.equals(run.configFile) && configName.equals(run.configName) && runId == run.runId;
	}

	@Override
	public String toString() {
		return "SimulationRun(file = '" + configFile + "'; name = '" + configName + "'; id = " + runId + ";)";
	}
}
