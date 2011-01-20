import java.io.File;
import java.io.IOException;

public class SimulationRun {
	
	protected final String configName;
	protected final int runID;
	
	public SimulationRun(String configName, int runID) {
		this.configName = configName;
		this.runID = runID;
	}
	
	public int run(File workingDir) throws IOException, InterruptedException {
		Process process = Runtime.getRuntime().exec("../src/OverSim -c " + configName + " -u Cmdenv -r " + runID, null, workingDir);
		return process.waitFor();
	}
	
	public boolean equals(Object o) {
		if (!(o instanceof SimulationRun))
			return false;
		
		SimulationRun run = (SimulationRun) o;
		return configName.equals(run.configName) && runID == run.runID; 
	}
	
	public String toString() {
		return "SimulationRun(name = '" + configName + "'; id = " + runID + ";)";
	}
}
