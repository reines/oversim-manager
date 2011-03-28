package com.jamierf.oversim.manager.main;

import java.io.IOException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import com.jamierf.oversim.manager.Manager;

public class Daemon {
	public static final void main(String[] args) {
		try {
			// Load the config file
			PropertiesConfiguration config = new PropertiesConfiguration("manager.ini");

			// TODO: Setup defaults/check for missing

			Manager manager = new Manager(config);
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
