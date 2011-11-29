package com.jamierf.oversim.manager.main;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jamierf.oversim.manager.Manager;

public class ParseData {

	private static final Logger logger = LoggerFactory.getLogger(ParseData.class);

	public static final void main(String[] args) {
		try {
			if (args.length != 2) {
				System.err.println("Usage: <config name> <data directory id>");
				System.exit(1);
			}

			// Load the config file
			final PropertiesConfiguration config = new PropertiesConfiguration("manager.ini");

			// TODO: Setup defaults/check for missing

			final Manager manager = new Manager(config);

			manager.addDataConfig(args[0], args[1]);

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
