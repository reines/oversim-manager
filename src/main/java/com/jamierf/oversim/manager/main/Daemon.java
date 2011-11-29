package com.jamierf.oversim.manager.main;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jamierf.oversim.manager.Manager;

public class Daemon {

	private static final Logger logger = LoggerFactory.getLogger(Daemon.class);

	public static final void main(String[] args) {
		try {
			// Load the config file
			final PropertiesConfiguration config = new PropertiesConfiguration("manager.ini");

			// TODO: Setup defaults/check for missing

			final Manager manager = new Manager(config);
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
