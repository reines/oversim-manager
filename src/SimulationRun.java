import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SimulationRun {

	protected final String configName;
	protected final int runID;

	public SimulationRun(String configName, int runID) {
		this.configName = configName;
		this.runID = runID;
	}

	public int run(File workingDir, HashMap<String, String> parameters) throws IOException, InterruptedException {
		String[] cmd = new String[4 + parameters.size()];
		int index = 0;

		cmd[index++] = "../src/OverSim";
		cmd[index++] = "-c" + configName;
		cmd[index++] = "-uCmdenv";
		cmd[index++] = "-r" + runID;

		// Append any special parameters
		for (Map.Entry<String, String> entry : parameters.entrySet())
			cmd[index++] = "--" + entry.getKey() + "=" + entry.getValue();

		// Execute OverSim
		Process process = Runtime.getRuntime().exec(cmd, null, workingDir);
		return process.waitFor();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SimulationRun))
			return false;

		SimulationRun run = (SimulationRun) o;
		return configName.equals(run.configName) && runID == run.runID;
	}

	@Override
	public String toString() {
		return "SimulationRun(name = '" + configName + "'; id = " + runID + ";)";
	}
}
