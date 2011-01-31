package com.jamierf.oversim.manager;

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

import com.jamierf.oversim.manager.runnable.SimulationData;
import com.jamierf.oversim.manager.runnable.SimulationRun;

public class Manager {

	public static final void main(String[] args) {
		try {
			if (args.length < 1) {
				System.err.println("Must provide at least 1 config to run.");
				System.exit(1);
			}

			// Load the config file
			PropertiesConfiguration config = new PropertiesConfiguration("manager.ini");

			// TODO: Setup defaults/check for missing

			Manager manager = new Manager(config);

			for (String configName : args)
				manager.addConfig(configName);

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
	protected final String configFile;
	protected final List<SimulationConfig> configs;
	protected final Map<String, String> globalParameters;
	protected final List<SimulationThread> threads;
	protected final Queue<Runnable> queue;
	protected final Queue<SimulationRun> completed;
	protected final Queue<SimulationRun> failed;
	protected final String[] wantedScalars;
	protected long startTime;
	protected boolean finished;

	public Manager(Configuration config) throws IOException {
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

		configs = new LinkedList<SimulationConfig>();

		// Fetch a list of scalars that we care about, then quote them so they are usable inside a regex
		wantedScalars = config.getStringArray("data.scalar");
		for (int i = 0;i < wantedScalars.length;i++)
			wantedScalars[i] = Pattern.quote(wantedScalars[i]);

		// TODO: Load in any override parameters
		globalParameters = new HashMap<String, String>();

		// Set the working directory and config file
		workingDir = new File(config.getString("simulation.working-dir", "."));
		configFile = config.getString("simulation.config-file", "omnetpp.ini");

		completed = new LinkedList<SimulationRun>();
		failed = new LinkedList<SimulationRun>();

		finished = false;

		resultRootDir = new File(workingDir, globalParameters.containsKey("result-dir") ? globalParameters.get("result-dir") : "results");
		if (!resultRootDir.isDirectory())
			throw new RuntimeException("Invalid result directory: " + resultRootDir.getAbsolutePath());

		// Find OverSim - attempt to use the RELEASE version by default
		overSim = Manager.findOverSim(workingDir);

		threads = new ArrayList<SimulationThread>(maxThreads);
		queue = new LinkedList<Runnable>();

		// Create threads
		for (int i = 0;i < maxThreads;i++) {
			SimulationThread thread = new SimulationThread(this);
			threads.add(thread);
		}

		System.out.println("Initialized " + threads.size() + " threads.");
	}

	public synchronized void addConfig(String configName) throws IOException {
		SimulationConfig config = new SimulationConfig(configFile, configName, resultRootDir, globalParameters);

		int totalRunCount = this.countRuns(configName); // Fetch the total run count
		if (totalRunCount == 0)
			throw new RuntimeException("Invalid config name, 0 runs found.");

		// Create the queue of simulation runs
		for (int i = 0;i < totalRunCount;i++) {
			SimulationRun run = new SimulationRun(i, workingDir, overSim, config);
			queue.add(run);
		}

		System.out.println("Added configuration: " + config);
		System.out.println("with result dir: " + config.getResultDir().getAbsolutePath());

		configs.add(config);
	}

	protected int countRuns(String configName) throws IOException
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

		if (queue.isEmpty())
			throw new RuntimeException("Queue is empty, nothing to do.");

		startTime = System.currentTimeMillis();

		// Start all our threads
		for (SimulationThread thread : threads)
			thread.start();

		while (!finished)
			this.wait();

		for (SimulationConfig config : configs) {
			// All child threads have now finished, so lets process the data
			System.out.println("-------------------------------------");

			// If we have any data, output a CSV of it
			if (config.hasData()) {
				System.out.println("Creating CSV file at: " + config.getResultDir().getName() + ".csv");

				// Save the collated data to a CSV file
				config.writeCSV(new File(resultRootDir, config.getResultDir().getName() + ".csv"));
			}

			System.out.println("Compressing raw data to: " + config.getResultDir().getName() + ".tar.gz");

			// Save the raw results into an archive
			List<String> command = new LinkedList<String>();

			command.add("tar");
			command.add("-czf");
			command.add(config.getResultDir().getName() + ".tar.gz");
			command.add(config.getResultDir().getName());

			Process process = new ProcessBuilder(command).directory(resultRootDir).start();
			process.waitFor();

			// Display a summary
			System.out.println("-------------------------------------");
			System.out.println("Config: " + config);
			System.out.println("Total duration: " + DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, true));
			System.out.println("Completed runs: " + completed.size());
			System.out.println("Failed runs: " + failed.size());

			// We have failed runs, list them
			if (!failed.isEmpty())
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

			// Queue a data processing instance for this run
			SimulationData data = new SimulationData(run.getRunId(), wantedScalars, run.getConfig());
			queue.add(data);
		}
	}

	public synchronized void failed(Runnable runnable) {
		System.out.println(runnable + " failed!");

		if (runnable instanceof SimulationRun)
			failed.add((SimulationRun) runnable);
	}

	public synchronized void finished(SimulationThread thread, long duration) {
		threads.remove(thread);

		// This was the final thread, so notify the main thread
		if (threads.isEmpty()) {
			finished = true;
			this.notify();
		}
	}
}
