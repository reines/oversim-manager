import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import org.apache.commons.lang.time.DurationFormatUtils;

public class SimulationThread extends Thread {

	protected final File overSim;
	protected final Manager manager;
	protected final File workingDir;
	protected final Map<String, String> parameters;
	protected final Queue<SimulationRun> runs;
	protected final Queue<SimulationRun> completed;
	protected final Queue<SimulationRun> failed;

	public SimulationThread(Manager manager, File workingDir, Map<String, String> parameters, File overSim) {
		this.manager = manager;
		this.workingDir = workingDir;
		this.parameters = parameters;
		this.overSim = overSim;

		runs = new LinkedList<SimulationRun>();
		completed = new LinkedList<SimulationRun>();
		failed = new LinkedList<SimulationRun>();
	}

	public synchronized void queue(SimulationRun run) {
		runs.add(run);
	}

	@Override
	public void run() {
		long startTime = System.currentTimeMillis();

		while (true) {
			try {
				SimulationRun simulation = null;
				synchronized (this) {
					simulation = runs.poll();
				}

				// The queue is empty, this thread is now finished
				if (simulation == null)
					break;

				try {
					System.out.println(this + " starting " + simulation + ".");
					long duration = simulation.run(workingDir, parameters, overSim);

					// Mark this simulation as completed
					System.out.println(this + " completed " + simulation + " in " + DurationFormatUtils.formatDurationWords(duration, true, true) + ".");
					completed.add(simulation);
				}
				catch (RuntimeException e) {
					System.err.println(e.getMessage());

					// Something went wrong, mark as failed
					System.out.println(this + " failed " + simulation + ".");
					failed.add(simulation);
				}
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// For all the completed simulations, consume their data
		for (SimulationRun simulation : completed) {
			try {
				manager.consumeData(simulation);
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		manager.notifyCompletion(this, System.currentTimeMillis() - startTime, completed, failed);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SimulationThread))
			return false;

		SimulationThread thread = (SimulationThread) o;
		return this.getId() == thread.getId();
	}

	@Override
	public String toString() {
		return "SimulationThread(id = " + super.getId() + "; remaining = " + runs.size() + "; completed = " + completed.size() + "; failed = " + failed.size() + ";)";
	}
}
