package com.jamierf.oversim.manager.main;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import com.jamierf.oversim.manager.Manager;

public class RunSimulation {
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (ConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (RuntimeException e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}
	}
}
