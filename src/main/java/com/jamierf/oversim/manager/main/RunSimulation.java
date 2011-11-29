package com.jamierf.oversim.manager.main;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jamierf.oversim.manager.Manager;

public class RunSimulation {

	private static final Logger logger = LoggerFactory.getLogger(RunSimulation.class);

	public static final void main(String[] args) {
		try {
			if (args.length < 1) {
				System.err.println("Must provide at least 1 config to run.");
				System.exit(1);
			}

			// Load the config file
			final PropertiesConfiguration config = new PropertiesConfiguration("manager.ini");

			// TODO: Setup defaults/check for missing

			final Manager manager = new Manager(config);

			for (String configName : args)
				manager.addRunConfig(configName);

			manager.start();
		}
		catch (IOException e) {
			if (logger.isErrorEnabled())
				logger.error("Error loading OverSim", e);
		}
		catch (InterruptedException e) {
			if (logger.isErrorEnabled())
				logger.error("Error running OverSim", e);
		}
		catch (ConfigurationException e) {
			if (logger.isErrorEnabled())
				logger.error("Error loading configuration or malformed configuration", e);
		}
		catch (RuntimeException e) {
			if (logger.isErrorEnabled())
				logger.error("Error running manager", e);
		}
	}
}
