package com.jamierf.oversim.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimulationThread extends Thread {

	private static final Logger logger = LoggerFactory.getLogger(SimulationThread.class);

	protected final Manager manager;

	public SimulationThread(Manager manager) {
		this.manager = manager;
	}

	@Override
	public void run() {
		while (true) {
			Runnable runnable = null;

			try {
				runnable = manager.poll();
			}
			catch (InterruptedException e1) { }

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
				if (logger.isWarnEnabled())
					logger.warn(this + " failed", e);

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
