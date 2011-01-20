import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Manager {

	public static final void main(String[] args) {
		try {
			HashMap<String, String> parameters = new HashMap<String, String>();

			// Count how many available cores we have
			int coreCount = Runtime.getRuntime().availableProcessors();

			Manager manager = new Manager(coreCount, new File("/home/jamie/Development/oversim/OverSim-20101103/simulations"), "EpiChordLarge", parameters);
			manager.start();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static int countRuns(File workingDir, String configName) throws IOException
	{
		Process process = Runtime.getRuntime().exec("../src/OverSim -x" + configName, null, workingDir);

		BufferedReader in = null;
		int runs = 0;

		try {
			in = new BufferedReader(new InputStreamReader(process.getInputStream()));

			Pattern p = Pattern.compile("Number of runs: (\\d+)");
			for (String line;(line = in.readLine()) != null;) {
				Matcher m = p.matcher(line);
				if (m.find())
				{
					runs = Integer.parseInt(m.group(1));
					break;
				}
			}
		}
		finally {
			if (in != null)
				in.close();
		}

		return runs;
	}

	protected final File workingDir;
	protected final File resultDir;
	protected final HashMap<String, String> parameters;
	protected final List<SimulationThread> threads;

	public Manager(int processCount, File workingDir, String configName) throws IOException {
		this (processCount, workingDir, configName, new HashMap<String, String>());
	}

	public Manager(int processCount, File workingDir, String configName, HashMap<String, String> parameters) throws IOException {
		this.workingDir = workingDir;
		this.parameters = parameters;

		File resultRootDir = new File(workingDir, parameters.containsKey("result-dir") ? parameters.get("result-dir") : "results");
		if (!resultRootDir.isDirectory())
			throw new RuntimeException("Invalid result directory: " + resultRootDir.getAbsolutePath());

		resultDir = new File(resultRootDir, configName + "-" + (new Date()));
		if (!resultDir.mkdir())
			throw new RuntimeException("Unable to create result subdirectory.");

		parameters.put("result-dir", resultDir.getAbsolutePath());

		threads = new ArrayList<SimulationThread>(processCount);

		int totalRunCount = Manager.countRuns(workingDir, configName); // Fetch the total run count
		int perProcess = (int) Math.ceil((double)totalRunCount / (double)processCount);
		List<SimulationRun> totalRuns = new ArrayList<SimulationRun>(totalRunCount);

		// Create the list of simulation runs
		for (int i = 0;i < totalRunCount;i++) {
			SimulationRun run = new SimulationRun(configName, i);
			totalRuns.add(run);
		}

		// Shuffle the list so that we don't end up with all similar runs in the same thread
		Collections.shuffle(totalRuns);

		// Check we don't have more threads than we need
		if (processCount > totalRunCount)
			processCount = totalRunCount;

		for (int i = 0;i < processCount;i++)
		{
			int runCount = perProcess > totalRuns.size() ? totalRuns.size() : perProcess; // Check how many to delegate

			SimulationThread thread = new SimulationThread(this, workingDir, parameters);

			// Add runs to be handled by this thread
			for (int j = 0;j < runCount;j++)
				thread.queue(totalRuns.remove(0));

			threads.add(thread);
		}

		System.out.println("Initialized " + threads.size() + " threads, with a total of " + totalRunCount + " runs.");
	}

	public synchronized void start() {
		// Start all our threads
		for (SimulationThread thread : threads)
			thread.start();
	}

	public synchronized void notifyCompletion(SimulationThread thread, Queue<SimulationRun> completed, Queue<SimulationRun> failed) {
		System.out.println(thread + " completed all simulations, terminating.");
		threads.remove(thread);

		// TODO: Combine the collated data with any existing

		if (threads.isEmpty()) {
			System.out.println("All runs completed, terminating.");
			// TODO: Save the results somewhere, and tar up the raw data
		}
	}
}
