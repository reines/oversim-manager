package com.jamierf.oversim.manager;

import com.google.common.collect.Lists;
import com.jamierf.oversim.manager.runnable.Run;
import com.jamierf.oversim.manager.runnable.SimulationData;
import com.jamierf.oversim.manager.runnable.SimulationRun;
import com.jamierf.oversim.manager.util.DirectoryArchiver;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConversionException;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Manager {

	protected final File overSim;
	protected final File workingDir;
	protected final File resultRootDir;
	protected final String configFile;
	protected final List<SimulationConfig> configs;
	protected final Map<String, String> globalParameters;
	protected final List<SimulationThread> threads;
	protected final List<Run> queue;
	protected final String[] wantedScalars;
	protected final boolean deleteData;
	protected final StringBuilder buffer;
	protected final boolean shuffle;
	protected boolean paused;
	protected DirectoryArchiver archiver;
	protected long startTime;
	protected boolean finished;
	protected int pendingRuns;

	public Manager(Configuration config) throws IOException, ConfigurationException {
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
				throw new ConfigurationException("Malformed configuration, simulation.max-threads must be an integer!");
			}
		}

		// Should we shuffle runs after adding them?
		shuffle = config.getBoolean("simulation.shuffle-runs", true);

		buffer = new StringBuilder();

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
				throw new ConfigurationException("Malformed configuration, data.compression-type must be one of: " + StringUtils.join(DirectoryArchiver.ArchiveType.values(), ", ") + ".");
			}
		}

		deleteData = config.getBoolean("data.delete", false);

		finished = false;

		resultRootDir = new File(workingDir, globalParameters.containsKey("result-dir") ? globalParameters.get("result-dir") : "results");
		if (!resultRootDir.isDirectory())
			throw new ConfigurationException("Invalid result directory: " + resultRootDir.getCanonicalPath());

        final String executableName = config.getString("simulation.executable-name", "OverSim");

		// Find OverSim - attempt to use the RELEASE version by default
		final File release = new File(workingDir, "../out/gcc-release/src/" + executableName);
		final File debug = new File(workingDir, "../out/gcc-debug/src/" + executableName);
		final File link = new File(workingDir, "../src/" + executableName);
		if (release.exists()) {
			this.println("Using OverSim in RELEASE mode.");
			overSim = release;
		}
		else if (debug.exists()) {
			this.println("Using OverSim in DEBUG mode.");
			overSim = debug;
		}
		else if (link.exists())
			overSim = link;
		else
			throw new FileNotFoundException("Unable to locate OverSim executable.");

		threads = new ArrayList<SimulationThread>(maxThreads);
		queue = Lists.newArrayList();

		pendingRuns = 0;
		paused = false;

		// Create threads
		for (int i = 0;i < maxThreads;i++) {
			final SimulationThread thread = new SimulationThread(this);
			threads.add(thread);
		}

		this.println("Initialized " + threads.size() + " threads.");
	}

	public synchronized void setPaused(boolean paused) {
		this.paused = paused;
	}

	public boolean isPaused() {
		return paused;
	}

	public synchronized void addRunConfig(String configName, String id) throws IOException {
		final int totalRunCount = this.countRuns(configName); // Fetch the total run count
		if (totalRunCount == 0)
			throw new RuntimeException("Invalid config name, 0 runs found.");

		final SimulationConfig config = new SimulationConfig(configFile, configName, resultRootDir, id, globalParameters, totalRunCount);
		pendingRuns += totalRunCount;

		// Create the queue of simulation runs
		for (int i = 0;i < totalRunCount;i++) {
			final SimulationRun run = new SimulationRun(i, workingDir, overSim, config);
            if (run.resultsExist()) {
                this.completed(run);
            }
            // Only queue the run if the results don't already exist
            else {
			    queue.add(run);
            }
		}

		// Shuffle the queue to help prevent bunching of memory intensive configurations
		if (shuffle) {
			Collections.shuffle(queue);
        }

		this.println("Added configuration: " + config + " with " + totalRunCount + " runs");
		this.println("Result dir: " + config.getResultDir().getCanonicalPath());
		this.println("Pending: " + pendingRuns + " runs");
		this.println("Queue size: " + queue.size());

		configs.add(config);

		this.notifyAll();
	}

	public synchronized void addDataConfig(String configName, String id) throws IOException {
		final SimulationConfig config = new SimulationConfig(configFile, configName, resultRootDir, id);

		final FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith(".sca");
			}
		};

		final String[] files = config.getResultDir().list(filter);
		final Pattern pattern = Pattern.compile("^" + Pattern.quote(configName) + "-(\\d+)\\.sca$");

		// Create the queue of simulation data
		for (String file : files) {
			final Matcher m = pattern.matcher(file);
			if (!m.matches()) {
				continue;
            }

			final int i = Integer.parseInt(m.group(1));
			final SimulationData data = new SimulationData(i, wantedScalars, config);

			queue.add(data);
			config.pendingRuns++;
		}

		pendingRuns += config.pendingRuns;

		// Shuffle the queue to help prevent bunching of memory intensive configurations
		if (shuffle) {
			Collections.shuffle(queue);
        }

		this.println("Added result: " + config + " with " + config.completedRuns + " results.");
		this.println("Result dir: " + config.getResultDir().getCanonicalPath());
		this.println("Queue size now: " + queue.size());

		configs.add(config);

		this.notifyAll();
	}

	protected int countRuns(String configName) throws IOException {
		final List<String> command = new LinkedList<String>();

		command.add(overSim.getCanonicalPath());
		command.add("-f" + configFile);
		command.add("-x" + configName);

		final Process process = new ProcessBuilder(command).directory(workingDir).start();

		BufferedReader in = null;
		int runs = 0;

		try {
			in = new BufferedReader(new InputStreamReader(process.getInputStream()));

			final Pattern p = Pattern.compile("Number of runs: (\\d+)");
			for (String line;(line = in.readLine()) != null;) {
				final Matcher m = p.matcher(line);
				if (m.find()) {
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
		for (SimulationThread thread : threads) {
			thread.start();
        }

		while (!finished) {
			this.wait();
        }

		this.println("All runs completed, terminating.");
		this.shutdown();
	}

	public synchronized Run poll() throws InterruptedException {
		while (queue.isEmpty()) {
			this.wait();
        }

		return queue.remove(0);
	}

	public synchronized void started(SimulationThread thread, Run runnable) {
		this.println(thread + " starting " + runnable + ". (" + queue.size() + " left in global queue, " + pendingRuns + " globally pending runs, " + runnable.getConfig().pendingRuns + " pending runs)");
	}

	public synchronized void completed(Run runnable) {
		SimulationConfig config = null;
		if (runnable instanceof SimulationRun) {
			final SimulationRun run = (SimulationRun) runnable;
			config = run.getConfig();

			config.pendingRuns--;
			pendingRuns--;

			config.completedRuns++;

			// Queue a data processing instance for this run
			queue.add(new SimulationData(run.getRunId(), wantedScalars, config));
			this.notifyAll();

			config.pendingRuns++;
			pendingRuns++;
		}
		else if (runnable instanceof SimulationData) {
			config = runnable.getConfig();

			config.pendingRuns--;
			pendingRuns--;
		}

		if (config != null) {
			checkForCompletion(config);
        }
	}

	public synchronized void failed(SimulationThread thread, Runnable runnable) {
		this.println(thread + " failed " + runnable);

		SimulationConfig config = null;
		if (runnable instanceof SimulationRun) {
			final SimulationRun run = (SimulationRun) runnable;
			config = run.getConfig();

			config.pendingRuns--;
			pendingRuns--;

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

		if (pendingRuns == 0) {
			finished = true;
			this.notifyAll();
		}
	}

	public String getBuffer() {
		return buffer.toString().trim();
	}

	public synchronized final void println(Object o) {
		final String line = o.toString();

		System.out.println(line);

		buffer.append(line);
		buffer.append('\n');
	}

	public synchronized void shutdown() {
		this.println("Shutdown requested.");

		System.exit(0);
	}
}
