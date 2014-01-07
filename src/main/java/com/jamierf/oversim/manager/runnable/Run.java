package com.jamierf.oversim.manager.runnable;

import com.jamierf.oversim.manager.SimulationConfig;

public interface Run extends Runnable {

    public SimulationConfig getConfig();

}
