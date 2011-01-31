package com.jamierf.oversim.manager;

import org.apache.commons.lang.time.DurationFormatUtils;

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
				long runStartTime = System.currentTimeMillis();

				System.out.println(this + " starting " + runnable + ".");
				runnable.run();

				// Mark this simulation as completed
				manager.completed(runnable);
				System.out.println(this + " completed " + runnable + " in " + DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - runStartTime, true, true) + ".");
			}
			catch (RuntimeException e) {
				System.err.println(e.getMessage());

				// Something went wrong, mark as failed
				manager.failed(runnable);
				System.out.println(this + " failed " + runnable);
			}
		}

		manager.finished(this, System.currentTimeMillis() - startTime);
	}

	@Override
	public String toString() {
		return "SimulationThread(" + super.getId() + ")";
	}
}
