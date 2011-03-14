package com.jamierf.oversim.manager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import com.jamierf.oversim.manager.util.DirectoryArchiver;
import com.jamierf.oversim.manager.webui.ServerCommand;
import com.jamierf.oversim.manager.webui.WebUI;

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
	protected final List<Runnable> queue;
	protected final String[] wantedScalars;
	protected final boolean deleteData;
	protected final WebUI web;
	protected DirectoryArchiver archiver;
	protected long startTime;
	protected boolean finished;
	protected int pendingRuns;

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
				throw new RuntimeException("Malformed configuration, simulation.max-threads must be an integer!");
			}
		}

		web = new WebUI(config.getInt("webui.port", 8090), this);
		web.start();

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

		archiver = null;
		if (config.getBoolean("data.compress", false)) {
			try {
				DirectoryArchiver.ArchiveType type = DirectoryArchiver.ArchiveType.valueOf(config.getString("data.compression-type", "TAR_GZIP"));
				archiver = new DirectoryArchiver(type);
			}
			catch (IllegalArgumentException e) {
				throw new RuntimeException("Malformed configuration, data.compression-type must be one of: " + StringUtils.join(DirectoryArchiver.ArchiveType.values(), ", ") + ".");
			}
		}

		deleteData = config.getBoolean("data.delete", false);

		finished = false;

		resultRootDir = new File(workingDir, globalParameters.containsKey("result-dir") ? globalParameters.get("result-dir") : "results");
		if (!resultRootDir.isDirectory())
			throw new RuntimeException("Invalid result directory: " + resultRootDir.getCanonicalPath());

		// Find OverSim - attempt to use the RELEASE version by default
		overSim = Manager.findOverSim(workingDir);

		threads = new ArrayList<SimulationThread>(maxThreads);
		queue = new ArrayList<Runnable>();

		pendingRuns = 0;

		// Create threads
		for (int i = 0;i < maxThreads;i++) {
			SimulationThread thread = new SimulationThread(this);
			threads.add(thread);
		}

		this.println("Initialized " + threads.size() + " threads.");

		this.println("WebUI started at: " + web.getUri());
	}

	public synchronized void addConfig(String configName) throws IOException {
		int totalRunCount = this.countRuns(configName); // Fetch the total run count
		if (totalRunCount == 0)
			throw new RuntimeException("Invalid config name, 0 runs found.");

		SimulationConfig config = new SimulationConfig(configFile, configName, resultRootDir, globalParameters, totalRunCount);
		pendingRuns += totalRunCount;

		// Create the queue of simulation runs
		for (int i = 0;i < totalRunCount;i++) {
			SimulationRun run = new SimulationRun(i, workingDir, overSim, config);
			queue.add(run);
		}

		// Shuffle the queue to help prevent bunching of memory intensive configurations
		Collections.shuffle(queue);

		this.println("Added configuration: " + config + " with " + totalRunCount + " runs");
		this.println("Result dir: " + config.getResultDir().getCanonicalPath());

		configs.add(config);

		ServerCommand command = new ServerCommand(ServerCommand.Type.ADDED_CONFIG);

		command.add("config", config.toString());
		command.add("totalRunCount", totalRunCount);
		command.add("resultDir", config.getResultDir().getCanonicalPath());

		web.sendMessage(command);

		this.notifyAll();
	}

	protected int countRuns(String configName) throws IOException
	{
		List<String> command = new LinkedList<String>();

		command.add(overSim.getCanonicalPath());
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

	public synchronized void start() throws IOException, InterruptedException {
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

		this.println("All runs completed, terminating.");
		this.shutdown();
	}

	public synchronized Runnable poll() throws InterruptedException {
		while (queue.isEmpty())
			this.wait();

		return queue.remove(0);
	}

	public synchronized void started(SimulationThread thread, Runnable runnable) {
		this.println(thread + " starting " + runnable + ".");

		if (runnable instanceof SimulationRun) {
			SimulationRun run = (SimulationRun) runnable;

			ServerCommand command = new ServerCommand(ServerCommand.Type.STARTED_RUN);

			command.add("config", run.getConfig().toString());
			command.add("run", run.getRunId());

			web.sendMessage(command);
		}
	}

	public synchronized void completed(SimulationThread thread, Runnable runnable, long duration) {
		this.println(thread + " completed " + runnable + " in " + DurationFormatUtils.formatDurationWords(duration, true, true) + ".");

		SimulationConfig config = null;
		if (runnable instanceof SimulationRun) {
			SimulationRun run = (SimulationRun) runnable;
			config = run.getConfig();

			config.pendingRuns--;
			pendingRuns--;

			config.completedRuns++;

			// Queue a data processing instance for this run
			queue.add(new SimulationData(run.getRunId(), wantedScalars, run.getConfig()));
			this.notifyAll();

			ServerCommand command = new ServerCommand(ServerCommand.Type.COMPLETED_RUN);

			command.add("config", config.toString());
			command.add("run", run.getRunId());
			command.add("duration", duration);

			web.sendMessage(command);

			config.pendingRuns++;
			pendingRuns++;
		}
		else if (runnable instanceof SimulationData) {
			config = ((SimulationData) runnable).getConfig();

			config.pendingRuns--;
			pendingRuns--;
		}

		if (config != null)
			checkForCompletion(config);
	}

	public synchronized void failed(SimulationThread thread, Runnable runnable) {
		this.println(thread + " failed " + runnable);

		SimulationConfig config = null;
		if (runnable instanceof SimulationRun) {
			SimulationRun run = (SimulationRun) runnable;
			config = run.getConfig();

			config.pendingRuns--;
			pendingRuns--;

			ServerCommand command = new ServerCommand(ServerCommand.Type.FAILED_RUN);

			command.add("config", config.toString());
			command.add("run", run.getRunId());

			web.sendMessage(command);

			config.failedRuns++;
		}
		else if (runnable instanceof SimulationData) {
			config = ((SimulationData) runnable).getConfig();

			config.pendingRuns--;
			pendingRuns--;
		}

		if (config != null)
			checkForCompletion(config);
	}

	protected synchronized void checkForCompletion(SimulationConfig config) {
		if (config.pendingRuns == 0) {
			try {
				config.processData(this, resultRootDir, archiver, deleteData);
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		ServerCommand command = new ServerCommand(ServerCommand.Type.COMPLETED_CONFIG);

		command.add("config", config.toString());

		web.sendMessage(command);

		if (pendingRuns == 0) {
			finished = true;
			this.notifyAll();
		}
	}

	public synchronized void println(Object o) {
		String line = o.toString();

		System.out.println(line);

		if (web == null)
			return;

		ServerCommand command = new ServerCommand(ServerCommand.Type.DISPLAY_LOG);
		command.add("line", line);

		web.sendMessage(command);
	}

	public synchronized void shutdown() {
		this.println("Shutdown requested.");

		web.sendMessage(new ServerCommand(ServerCommand.Type.SHUTDOWN));
		try { web.stop(); } catch (IOException e) { }

		System.exit(0);
	}
}
