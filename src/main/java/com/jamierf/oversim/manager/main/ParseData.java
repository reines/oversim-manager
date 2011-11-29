package com.jamierf.oversim.manager.main;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import com.jamierf.oversim.manager.Manager;

public class ParseData {
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
