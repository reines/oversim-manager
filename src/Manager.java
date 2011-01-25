import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConversionException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DurationFormatUtils;

import runnable.SimulationRun;

public class Manager extends DataManager {

	public static final void main(String[] args) {
		try {
			if (args.length != 1) {
				System.err.println("Usage: java Manager <config name>");
				System.exit(1);
			}

			// Read the config name from command line arguments
			String configName = args[0];

			// Load the config file
			PropertiesConfiguration config = new PropertiesConfiguration("manager.ini");

			// TODO: Setup defaults/check for missing

			Manager manager = new Manager(config, configName);
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
		catch (ConfigurationException e) {
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

	protected final File overSim;
	protected final File workingDir;
	protected final File resultRootDir;
	protected final File logDir;
	protected final Map<String, String> parameters;
	protected final Queue<Runnable> queue;
	protected final List<SimulationThread> threads;
	protected final Queue<SimulationRun> completed;
	protected final Queue<SimulationRun> failed;
	protected final String configName;
	protected long startTime;
	protected boolean finished;

	public Manager(Configuration config, String configName) throws IOException {
		super (config);

		this.configName = configName;

		// Count how many available cores we should use (max)
		int maxThreads = Runtime.getRuntime().availableProcessors();
		if (config.containsKey("simulation.max-threads")) {
			try {
				int overrideCoreCount = config.getInt("simulation.max-threads");
				if (overrideCoreCount > maxThreads)
					throw new RuntimeException("max threads (" + overrideCoreCount + ") in config is higher than phyiscal core count (" + maxThreads + "). This is a bad idea!");

				maxThreads = overrideCoreCount;
			}
			catch (ConversionException e) {
				throw new RuntimeException("Malformed configuration, max-threads must be an integer!");
			}
		}

		// Create a map to hold overriding OverSim parameters
		parameters = new HashMap<String, String>();

		// TODO: Load in any override parameters

		// Set the working directory and config file
		workingDir = new File(config.getString("simulation.working-dir", "."));
		String configFile = config.getString("simulation.config-file", "omnetpp.ini");

		completed = new LinkedList<SimulationRun>();
		failed = new LinkedList<SimulationRun>();

		finished = false;

		resultRootDir = new File(workingDir, parameters.containsKey("result-dir") ? parameters.get("result-dir") : "results");
		if (!resultRootDir.isDirectory())
			throw new RuntimeException("Invalid result directory: " + resultRootDir.getAbsolutePath());

		super.resultDir = new File(resultRootDir, configName + "-" + (System.currentTimeMillis() / 1000));
		if (!super.resultDir.mkdir())
			throw new RuntimeException("Unable to create result subdirectory.");

		parameters.put("result-dir", super.resultDir.getAbsolutePath());

		logDir = new File(super.resultDir, "logs");
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
		queue = new LinkedList<Runnable>();

		// Create the queue of simulation runs
		for (int i = 0;i < totalRunCount;i++) {
			SimulationRun run = new SimulationRun(configFile, configName, i, logDir, workingDir, parameters, overSim);
			queue.add(run);
		}

		for (int i = 0;i < maxThreads;i++)
		{
			SimulationThread thread = new SimulationThread(this);
			threads.add(thread);
		}

		System.out.println("Initialized " + threads.size() + " threads, for a total of " + totalRunCount + " runs.");
		System.out.println("-------------------------------------");
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

	public synchronized void start() throws InterruptedException, IOException {
		if (startTime > 0)
			throw new RuntimeException("This manager has already been started.");

		startTime = System.currentTimeMillis();

		// Start all our threads
		for (SimulationThread thread : threads)
			thread.start();

		while (!finished)
			this.wait();

		// All child threads have now finished, so lets process the data
		System.out.println("-------------------------------------");

		System.out.println("Creating CSV file at: " + super.resultDir.getName() + ".csv");

		// Save the collated data to a CSV file
		super.writeCSV(new File(resultRootDir, super.resultDir.getName() + ".csv"));

		System.out.println("Compressing raw data to: " + super.resultDir.getName() + ".tar.gz");

		// Save the raw results into an archive
		List<String> command = new LinkedList<String>();

		command.add("tar");
		command.add("-czf");
		command.add(resultDir.getName() + ".tar.gz");
		command.add(resultDir.getName());

		Process process = new ProcessBuilder(command).directory(resultRootDir).start();
		process.waitFor();

		// Display a summary
		System.out.println("-------------------------------------");
		System.out.println("Config name: " + configName);
		System.out.println("Completed runs: " + completed.size());
		System.out.println("Failed runs: " + failed.size());
		System.out.println("Total duration: " + DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, true));
		System.out.println("-------------------------------------");

		// We have failed runs, list them
		if (!failed.isEmpty()) {
			System.out.println("Failed runs (" + failed.size() + "):");
			System.out.println(StringUtils.join(failed, ','));
			System.out.println("-------------------------------------");
		}
	}

	public synchronized Runnable poll() {
		return queue.poll();
	}

	public synchronized void completed(Runnable runnable) {
		if (runnable instanceof SimulationRun) {
			SimulationRun run = (SimulationRun) runnable;
			completed.add(run);

			// TODO: queue a SimilationData instance
		}
	}

	public synchronized void failed(Runnable runnable) {
		System.out.println(runnable + " failed!");

		if (runnable instanceof SimulationRun)
			failed.add((SimulationRun) runnable);
	}

	public synchronized void finished(SimulationThread thread, long duration) {
		System.out.println(thread + " completed all simulations in " + DurationFormatUtils.formatDurationWords(duration, true, true) + ", terminating.");

		threads.remove(thread);

		// This was the final thread, so notify the main thread
		if (threads.isEmpty()) {
			finished = true;
			this.notify();
		}
	}
}
