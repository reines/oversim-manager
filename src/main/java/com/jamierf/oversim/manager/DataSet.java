package com.jamierf.oversim.manager;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class DataSet {

    protected final SortedSet<String> headers;
	protected final SortedMap<String, SortedMap<String, Queue<String>>> data;

	public DataSet() {
        headers = Sets.newTreeSet();
		data = Maps.newTreeMap();
	}

	public void mergeData(String uid, SortedMap<String, String> scalars) {
		synchronized (this) {
			// Fetch the queue of data for this set of parameters
            SortedMap<String, Queue<String>> map = data.get(uid);
			if (map == null) {
				// If we don't already have a queue yet, create one
				map = Maps.newTreeMap();
				data.put(uid, map);
			}

            headers.addAll(scalars.keySet());
            for (Map.Entry<String, String> scalar : scalars.entrySet()) {
                Queue<String> queue = map.get(scalar.getKey());
                if (queue == null) {
                    queue = Lists.newLinkedList();
                    map.put(scalar.getKey(), queue);
                }

                queue.add(scalar.getValue());
            }
		}
	}

	public synchronized boolean hasData() {
		return !data.isEmpty();
	}

	public synchronized void writeCSV(File csvFile) throws IOException {
		if (!this.hasData()) {
			throw new RuntimeException("Cannot write empty data set to CSV.");
        }

		PrintWriter out = null;

		try {
			final Collection<Object> row = Lists.newLinkedList();

			out = new PrintWriter(new FileWriter(csvFile), true);

			// Write the header
			row.clear();
			row.add("uid");

			for (String header : headers) {
				row.add(header + ".mean");
				row.add(header + ".stddev");
			}

			out.println(StringUtils.join(row, ','));

			// Combine all sets and produce summary statistics
			for (Map.Entry<String, SortedMap<String, Queue<String>>> entry : data.entrySet()) {
				final String uid = entry.getKey();
				final SortedMap<String, Queue<String>> map = entry.getValue();

				if (map.isEmpty()) {
					continue;
                }

				final List<SummaryStatistics> stats = Lists.newLinkedList();

                for (String header : headers) {
                    final SummaryStatistics summaryStats = new SummaryStatistics();
                    stats.add(summaryStats);

                    final Queue<String> values = map.get(header);
                    if (values != null) {
                        for (String value : values) {
                            try {
                                summaryStats.addValue(Double.parseDouble(value));
                            }
                            catch (NumberFormatException e) { }
                        }
                    }
                }

				// Output all the statistics into a row
				row.clear();
				row.add(uid.replaceAll(",", ""));

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
