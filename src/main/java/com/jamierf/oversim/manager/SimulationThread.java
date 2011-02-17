package com.jamierf.oversim.manager;

import org.apache.commons.lang.time.DurationFormatUtils;

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

				System.out.println(this + " starting " + runnable + ".");
				runnable.run();

				// Mark this simulation as completed
				System.out.println(this + " completed " + runnable + " in " + DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - runStartTime, true, true) + ".");
				manager.completed(runnable);
			}
			catch (Exception e) {
				System.err.println(e.getMessage());

				// Something went wrong, mark as failed
				System.out.println(this + " failed " + runnable);
				manager.failed(runnable);
			}
		}
	}

	@Override
	public String toString() {
		return "SimulationThread(" + super.getId() + ")";
	}
}
