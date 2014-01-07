package com.jamierf.oversim.manager.main;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.jamierf.oversim.manager.Manager;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Deque;

public class RunSimulation {

	private static final Logger logger = LoggerFactory.getLogger(RunSimulation.class);

    private static Optional<String> extractId(String[] args) {
        try {
            final long id = Long.parseLong(args[args.length - 1]);
            return Optional.of(String.valueOf(id));
        }
        catch (NumberFormatException e) {
            return Optional.absent();
        }
    }

	public static void main(String[] args) {
		try {
			if (args.length < 1) {
				System.err.println("Must provide at least 1 config to run.");
				System.exit(1);
			}

			// Load the config file
			final PropertiesConfiguration config = new PropertiesConfiguration("manager.ini");

			// TODO: Setup defaults/check for missing

			final Manager manager = new Manager(config);

            final Deque<String> configNames = Lists.newLinkedList(Arrays.asList(args));
            final Optional<String> idOptional = extractId(args);
            if (idOptional.isPresent()) {
                configNames.removeLast();
            }

            final String id = idOptional.or(String.valueOf(System.currentTimeMillis() / 1000));
			for (String configName : configNames) {
				manager.addRunConfig(configName, id);
            }

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
