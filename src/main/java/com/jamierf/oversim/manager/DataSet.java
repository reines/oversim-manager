package com.jamierf.oversim.manager;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;

public class DataSet {

	protected final SortedSet<String> headers;
	protected final Map<String, Queue<String[]>> data;

	public DataSet() {
		headers = new TreeSet<String>();
		data = new HashMap<String, Queue<String[]>>();
	}

	public void mergeData(String uid, SortedMap<String, String> scalars) {
		synchronized (this) {
			// Fetch the queue of data for this set of parameters
			Queue<String[]> queue = data.get(uid);
			if (queue == null) {
				// If we already have a queue yet, create one
				queue = new LinkedList<String[]>();
				data.put(uid, queue);
			}

			// If this is the first call, note the data headers
			if (headers.isEmpty())
				headers.addAll(scalars.keySet());

			// Add this data to the end of the queue
			queue.add(scalars.values().toArray(new String[scalars.size()]));
		}
	}

	public synchronized boolean hasData() {
		return !headers.isEmpty();
	}

	public synchronized void writeCSV(File csvFile) throws IOException {
		if (!this.hasData())
			throw new RuntimeException("Cannot write empty data set to CSV.");

		PrintWriter out = null;

		try {
			out = new PrintWriter(new FileWriter(csvFile), true);

			// Write the header
			out.println(StringUtils.join(headers, ','));

			// Reduce all the queues down to a single set of scalars
			for (Map.Entry<String, Queue<String[]>> entry : data.entrySet()) {
				Queue<String[]> queue = entry.getValue();
				int queueSize = queue.size();

				// Remove the head of the queue, we will use this to store the final results
				String[] scalars = queue.poll();

				// If the queue had more than 1 set of scalars in it, we need to process them
				if (queueSize > 1) {
					for (String[] next;(next = queue.poll()) != null;) {
						for (int i = 0;i < scalars.length;i++) {
							try {
								double value = Double.parseDouble(scalars[i]) + Double.parseDouble(next[i]);
								scalars[i] = String.valueOf(value);
							}
							catch (NumberFormatException e) { }
						}
					}

					// For each value in scalars, divide it by queueSize to get the mean again
					for (int i = 0;i < scalars.length;i++) {
						try {
							double value = Double.parseDouble(scalars[i]);
							scalars[i] = String.valueOf(value / queueSize);
						}
						catch (NumberFormatException e) { }
					}
				}

				// Put the collection into a CSV record
				out.println(StringUtils.join(scalars, ','));
			}
		}
		finally {
			if (out != null)
				out.close();
		}
	}
}
