import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class SimulationThread extends Thread {

	protected final Manager manager;
	protected final File workingDir;
	protected final HashMap<String, String> parameters;
	protected final Queue<SimulationRun> runs;
	protected final Queue<SimulationRun> completed;
	protected final Queue<SimulationRun> failed;

	public SimulationThread(Manager manager, File workingDir, HashMap<String, String> parameters) {
		this.manager = manager;
		this.workingDir = workingDir;
		this.parameters = parameters;

		runs = new LinkedList<SimulationRun>();
		completed = new LinkedList<SimulationRun>();
		failed = new LinkedList<SimulationRun>();
	}

	public synchronized void queue(SimulationRun run) {
		runs.add(run);
	}

	@Override
	public void run() {
		while (true) {
			try {
				SimulationRun simulation = null;
				synchronized (this) {
					simulation = runs.poll();
				}

				// The queue is empty, this thread is now finished
				if (simulation == null)
					break;

				System.out.println(this + " starting " + simulation + ".");
				int result = simulation.run(workingDir, parameters);

				// If the exit value wasn't 0 then something went wrong
				if (result != 0) {
					failed.add(simulation);
					continue;
				}

				// Mark this simulation as completed
				completed.add(simulation);
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

		// TODO: Collate the data, then pass it to the manager via notifyCompletion

		manager.notifyCompletion(this, completed, failed);
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
