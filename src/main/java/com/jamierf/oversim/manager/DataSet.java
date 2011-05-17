package com.jamierf.oversim.manager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;

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
			Collection<Object> row = new LinkedList<Object>();

			out = new PrintWriter(new FileWriter(csvFile), true);

			// Write the header
			row.clear();
			for (String header : headers) {
				row.add(header + ".mean");
				row.add(header + ".stddev");
			}
			out.println(StringUtils.join(row, ','));

			// Combine all sets and produce summary statistics
			for (Queue<String[]> queue : data.values()) {
				if (queue.isEmpty())
					continue;

				SummaryStatistics[] stats = new SummaryStatistics[queue.peek().length];
				for (int i = 0;i < stats.length;i++)
					stats[i] = new SummaryStatistics();

				for (String[] next;(next = queue.poll()) != null;) {
					for (int i = 0;i < stats.length;i++) {
						try {
							double value = Double.parseDouble(next[i]);
							stats[i].addValue(value);
						}
						catch (NumberFormatException e) { }
					}
				}

				// Output all the statistics into a row
				row.clear();
				for (SummaryStatistics stat : stats) {
					row.add(stat.getMean());
					row.add(stat.getStandardDeviation());
				}
				out.println(StringUtils.join(row, ','));
			}
		}
		finally {
			if (out != null)
				out.close();
		}
	}
}
