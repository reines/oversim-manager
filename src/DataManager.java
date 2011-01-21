import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

public class DataManager {

	protected final Set<String> headers;
	protected final Map<String, Queue<String[]>> data;
	protected File resultDir;

	public DataManager() {
		headers = new HashSet<String>();
		data = new HashMap<String, Queue<String[]>>();
	}

	public void consumeData(SimulationRun simulation) throws IOException {
		File sca = new File(resultDir, simulation.getConfigName() + "-" + simulation.getRunId() + ".sca");
		if (!sca.exists())
			throw new FileNotFoundException("Unable to find scalar results for: " + simulation);

		Map<String, String> attributes = new HashMap<String, String>();
		SortedMap<String, String> scalars = new TreeMap<String, String>();

		BufferedReader in = null;

		try {
			in = new BufferedReader(new FileReader(sca));

			// Read the version number and check it is what we expect
			String version = in.readLine();
			if (!version.matches("^version\\s2$"))
				throw new RuntimeException("Unrecognised scalar result file version.");

			@SuppressWarnings("unused")
			String runIdentifier = in.readLine();

			// Read the header
			Pattern headerPattern = Pattern.compile("^attr\\s+(\\w+)\\s+(.+)$");
			for (String line;(line = in.readLine()) != null;) {
				// When we reach an empty line it signifies the end of the header
				if (line.isEmpty())
					break;

				Matcher m = headerPattern.matcher(line);
				if (!m.find())
					continue;

				attributes.put(m.group(1), m.group(2));
			}

			// Confirm we have iterationvars, otherwise we don't have anything to use as a unique identifier
			if (!attributes.containsKey("iterationvars"))
				throw new RuntimeException("Malformed scalar file, no iterationvars found: " + sca.getAbsolutePath());

			// Read the scalars
			Pattern scalarPattern = Pattern.compile("^scalar\\s+([\\w\\.]+)\\s+\"([^\"]+)\"\\s+(.+)$");
			for (String line;(line = in.readLine()) != null;) {
				// When we reach an empty line it signifies the end of the scalars
				if (line.isEmpty())
					break;

				Matcher m = scalarPattern.matcher(line);
				if (!m.find())
					continue;

				// TODO: If we can filter which scalars we care about here we will save some memory

				// Record the scalar
				scalars.put(m.group(2), m.group(3));
			}

			// Add the iterationvars as scalars
			Matcher m = Pattern.compile("\\$([^=]+)=([^,\"]+)(?:[,\"]|$)").matcher(attributes.get("iterationvars"));
			while (m.find()) {
				String value = m.group(2);
				// If the value has a unit, remove it
				// TODO: There are more valid units than just "s"
				if (value.matches("^[\\d\\.]+s$"))
					value = value.substring(0, value.length() - 1);

				// TODO: the headers don't seem to be created properly??
				scalars.put(m.group(1), value);
			}

			// TODO: Handle any vectors?
		}
		finally {
			if (in != null)
				in.close();
		}

		synchronized (this) {
			// Use iterationvars as a unique identifier so that all repetitions are grouped together
			String uid = attributes.get("iterationvars");

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

	public synchronized void writeCSV(File csvFile) throws IOException {
		if (headers.isEmpty())
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
