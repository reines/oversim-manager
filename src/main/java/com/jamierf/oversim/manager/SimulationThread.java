package com.jamierf.oversim.manager;

import org.apache.commons.lang.time.DurationFormatUtils;
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
				final long runStartTime = System.currentTimeMillis();

				manager.started(this, runnable);
				runnable.run();

                final long duration = System.currentTimeMillis() - runStartTime;
                manager.println(this + " completed " + runnable + " in " + DurationFormatUtils.formatDurationWords(duration, true, true) + ".");

				// Mark this simulation as completed
				manager.completed(runnable);
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
