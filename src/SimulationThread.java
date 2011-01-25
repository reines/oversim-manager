
public class SimulationThread extends Thread {

	protected final Manager manager;

	public SimulationThread(Manager manager) {
		this.manager = manager;
	}

	@Override
	public void run() {
		long startTime = System.currentTimeMillis();

		while (true) {
			Runnable runnable = null;
			synchronized (this) {
				runnable = manager.poll();
			}

			// The queue is empty, this thread is now finished
			if (runnable == null)
				break;

			try {
				System.out.println(this + " starting " + runnable + ".");
				runnable.run();

				// Mark this simulation as completed
				manager.completed(runnable);
			}
			catch (RuntimeException e) {
				System.err.println(e.getMessage());

				// Something went wrong, mark as failed
				manager.failed(runnable);
			}
		}

		manager.finished(this, System.currentTimeMillis() - startTime);
	}

	@Override
	public String toString() {
		return "SimulationThread(id = " + super.getId() + ")";
	}
}
