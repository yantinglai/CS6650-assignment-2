package cs6650;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class Record {

    private enum RequestMethod {
        GET, POST
    }

    public static void calculateStats(String filePath) {
        try {
            List<Integer> getLatencies = new ArrayList<>();
            List<Integer> postLatencies = new ArrayList<>();

            parseCsvFile(filePath, getLatencies, postLatencies);

            displayStatistics("Get", getLatencies);
            displayStatistics("Post", postLatencies);

        } catch (Exception e) {
            System.err.println("Error calculating statistics: " + e.getMessage());
        }
    }

    private static void parseCsvFile(String filePath, List<Integer> getLatencies, List<Integer> postLatencies) throws Exception {
        try (Reader in = new FileReader(filePath)) {
            Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);

            for (CSVRecord record : records) {
                int methodType = Integer.parseInt(record.get(1));
                int latency = Integer.parseInt(record.get(2));

                if (RequestMethod.values()[methodType] == RequestMethod.GET) {
                    getLatencies.add(latency);
                } else {
                    postLatencies.add(latency);
                }
            }
        }
    }

    private static void displayStatistics(String requestType, List<Integer> latencies) {
        double[] latencyArray = latencies.stream().mapToDouble(d -> d).toArray();
        DescriptiveStatistics statistics = new DescriptiveStatistics(latencyArray);

        System.out.println(requestType + ": ");
        System.out.println("Median: " + statistics.getPercentile(50) + " ms");
        System.out.println("Mean: " + statistics.getMean() + " ms");
        System.out.println("99%: " + statistics.getPercentile(99) + " ms");
        System.out.println("Min: " + statistics.getMin() + " ms");
        System.out.println("Max: " + statistics.getMax() + " ms\n");
    }
}
