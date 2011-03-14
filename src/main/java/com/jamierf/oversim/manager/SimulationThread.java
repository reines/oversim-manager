package com.jamierf.oversim.manager;


public class SimulationThread extends Thread {

	protected final Manager manager;

	public SimulationThread(Manager manager) {
		this.manager = manager;
	}

	public void run() {
		while (true) {
			Runnable runnable = null;

			try {
				runnable = manager.poll();
			}
			catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			// The queue is empty, this thread is now finished
			if (runnable == null)
				break;

			try {
				long runStartTime = System.currentTimeMillis();

				manager.started(this, runnable);
				runnable.run();

				// Mark this simulation as completed
				manager.completed(this, runnable, System.currentTimeMillis() - runStartTime);
			}
			catch (Exception e) {
				System.err.println(e.getMessage());

				// Something went wrong, mark as failed
				manager.failed(this, runnable);
			}
		}
	}

	@Override
	public String toString() {
		return "SimulationThread(" + super.getId() + ")";
	}
}
