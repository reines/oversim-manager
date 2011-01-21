import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Manager {

	public static final Properties getConfig(File configFile) throws IOException {
		Properties config = new Properties();

		if (configFile.exists())
			config.load(new FileInputStream(configFile));

		return config;
	}

	public static final void main(String[] args) {
		try {
			if (args.length != 1) {
				System.err.println("Usage: java Manager <config name>");
				System.exit(1);
			}

			// Read the config name from command line arguments
			String configName = args[0];

			Properties config = Manager.getConfig(new File("manager.ini"));

			HashMap<String, String> parameters = new HashMap<String, String>();

			// Count how many available cores we should use (max)
			int maxThreads = Runtime.getRuntime().availableProcessors();
			if (config.containsKey("max-threads")) {
				try {
					int overrideCoreCount = Integer.parseInt(config.getProperty("coreCount"));
					if (overrideCoreCount > maxThreads)
						throw new RuntimeException("max threads (" + overrideCoreCount + ") in config is higher than phyiscal core count (" + maxThreads + "). This is a bad idea!");

					maxThreads = overrideCoreCount;
				}
				catch (NumberFormatException e) {
					throw new RuntimeException("Malformed configuration, max-threads must be an integer!");
				}
			}

			// Set the working directory and config file
			File workingDir = new File(config.getProperty("working-dir", "."));
			String configFile = config.getProperty("config-file", "omnetpp.ini");

			Manager manager = new Manager(maxThreads, workingDir, configFile, configName, parameters);
			manager.start();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (RuntimeException e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}
	}

	public static File findOverSim(File workingDir) throws FileNotFoundException {
		File release = new File(workingDir, "../out/gcc-release/src/OverSim");
		if (release.exists()) {
			System.out.println("Using OverSim in RELEASE mode.");
			return release;
		}

		File debug = new File(workingDir, "../out/gcc-debug/src/OverSim");
		if (debug.exists()) {
			System.out.println("Using OverSim in DEBUG mode.");

			Scanner scanner = new Scanner(System.in);
			System.out.print("This will be slow! Are you sure you wish to continue? [y/n]: ");

			try {
				String response = scanner.next();
				if (!response.equalsIgnoreCase("y") && !response.equalsIgnoreCase("yes"))
					throw new RuntimeException("Simulations cancelled.");
			}
			finally {
				scanner.close();
			}

			return debug;
		}

		File link = new File(workingDir, "../src/OverSim");
		if (link.exists())
			return link;

		throw new FileNotFoundException("Unable to locate OverSim executable.");
	}

	public int countRuns(String configFile, String configName) throws IOException
	{
		List<String> command = new LinkedList<String>();

		command.add(overSim.getAbsolutePath());
		command.add("-f" + configFile);
		command.add("-x" + configName);

		Process process = new ProcessBuilder(command).directory(workingDir).start();

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

	protected final File overSim;
	protected final File workingDir;
	protected final File resultRootDir;
	protected final File resultDir;
	protected final File logDir;
	protected final HashMap<String, String> parameters;
	protected final List<SimulationThread> threads;
	protected boolean finished;

	public Manager(int maxThreads, File workingDir, String configFile, String configName) throws IOException {
		this (maxThreads, workingDir, configFile, configName, new HashMap<String, String>());
	}

	public Manager(int maxThreads, File workingDir, String configFile, String configName, HashMap<String, String> parameters) throws IOException {
		this.workingDir = workingDir;
		this.parameters = parameters;

		finished = false;

		resultRootDir = new File(workingDir, parameters.containsKey("result-dir") ? parameters.get("result-dir") : "results");
		if (!resultRootDir.isDirectory())
			throw new RuntimeException("Invalid result directory: " + resultRootDir.getAbsolutePath());

		resultDir = new File(resultRootDir, configName + "-" + (System.currentTimeMillis() / 1000));
		if (!resultDir.mkdir())
			throw new RuntimeException("Unable to create result subdirectory.");

		parameters.put("result-dir", resultDir.getAbsolutePath());

		logDir = new File(resultDir, "logs");
		if (!logDir.mkdir())
			throw new RuntimeException("Unable to create logs subdirectory.");

		// Find OverSim - attempt to use the RELEASE version by default
		overSim = Manager.findOverSim(workingDir);

		int totalRunCount = this.countRuns(configFile, configName); // Fetch the total run count
		if (totalRunCount == 0)
			throw new RuntimeException("Invalid config name, 0 runs found.");

		// Check we don't have more threads than we need
		if (maxThreads > totalRunCount)
			maxThreads = totalRunCount;

		threads = new ArrayList<SimulationThread>(maxThreads);

		// Calculate how many runs we should do per thread
		int perThread = (int) Math.ceil((double)totalRunCount / (double)maxThreads);
		List<SimulationRun> totalRuns = new ArrayList<SimulationRun>(totalRunCount);

		// Create the list of simulation runs
		for (int i = 0;i < totalRunCount;i++) {
			SimulationRun run = new SimulationRun(configFile, configName, i, logDir);
			totalRuns.add(run);
		}

		// Shuffle the list so that we don't end up with all similar runs in the same thread
		Collections.shuffle(totalRuns);

		for (int i = 0;i < maxThreads;i++)
		{
			int runCount = perThread > totalRuns.size() ? totalRuns.size() : perThread; // Check how many to delegate

			SimulationThread thread = new SimulationThread(this, workingDir, parameters, overSim);

			// Add runs to be handled by this thread
			for (int j = 0;j < runCount;j++)
				thread.queue(totalRuns.remove(0));

			threads.add(thread);
		}

		System.out.println("Initialized " + threads.size() + " threads, with a total of " + totalRunCount + " runs.");
	}

	public synchronized void start() throws InterruptedException, IOException {
		// Start all our threads
		for (SimulationThread thread : threads)
			thread.start();

		while (!finished)
			this.wait();

		// All child threads have now finished, so lets process the data

		System.out.println("Creating CSV file at: " + resultDir.getName() + ".csv");

		// TODO: Save the collated data to a CSV file

		System.out.println("Compressing raw data to: " + resultDir.getName() + ".tar.gz");

		// Save the raw results into an archive
		String[] cmd = {"tar", "-czvf", resultDir.getName() + ".tar.gz", resultDir.getName()};

		Process process = Runtime.getRuntime().exec(cmd, null, resultRootDir);
		process.waitFor();

		System.out.println("Data compression completed, terminating.");
	}

	public synchronized void notifyCompletion(SimulationThread thread, Queue<SimulationRun> completed, Queue<SimulationRun> failed) {
		System.out.println(thread + " completed all simulations, terminating.");
		threads.remove(thread);

		// TODO: Combine the collated data with any existing

		// This was the final thread, so notify the main thread
		if (threads.isEmpty()) {
			finished = true;
			this.notify();
		}
	}
}
