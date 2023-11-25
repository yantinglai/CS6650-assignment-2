package cs6650;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


public class LoadTestClient {
    private static final AtomicLong numOfSuccessfulRequests = new AtomicLong(0);
    private static final AtomicLong numOfFailedRequests = new AtomicLong(0);
    private static final String OUTPUT_FILE = "output.csv";
    private static BufferedWriter writer;
    private static final int WARM_UP_THREADS = 10;
    private static final int API_CALLS_PER_THREAD = 1000;
    private static final Queue<String> globalQueue = new ConcurrentLinkedQueue<>();


    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.out.println("Usage: Client.LoadTestClient <threadGroupSize> <numThreadGroups> <delay> <IPAddr>");
            return;
        }

        int threadGroupSize = Integer.parseInt(args[0]);
        int numThreadGroups = Integer.parseInt(args[1]);
        int delay = Integer.parseInt(args[2]);
        String ipAddr = args[3];

        // Warmup
        ExecutorService warmupExecutor = Executors.newFixedThreadPool(WARM_UP_THREADS);
        for (int i = 0; i < WARM_UP_THREADS; i++) {
            warmupExecutor.submit(new Worker(ipAddr, 100, globalQueue, numOfSuccessfulRequests, numOfFailedRequests));
        }
        warmupExecutor.shutdown();
        warmupExecutor.awaitTermination(1, TimeUnit.HOURS);

        System.out.println("Warmup Phase finished, Load Test URL is " + ipAddr);

        long startTime = System.currentTimeMillis();

        // Main Thread
        ExecutorService mainExecutor = Executors.newFixedThreadPool(threadGroupSize * numThreadGroups);
        for (int i = 0; i < numThreadGroups; i++) {
            for (int j = 0; j < threadGroupSize; j++) {
                mainExecutor.submit(new Worker(ipAddr, API_CALLS_PER_THREAD, globalQueue, numOfSuccessfulRequests, numOfFailedRequests));
            }
            if (i < numThreadGroups - 1) {
                Thread.sleep(delay * 1000L);
            }
        }
        mainExecutor.shutdown();
        mainExecutor.awaitTermination(1, TimeUnit.HOURS);

        long endTime = System.currentTimeMillis();
        double wallTime = (endTime - startTime) / 1000;
        long totalRequests = (long) threadGroupSize * numThreadGroups * API_CALLS_PER_THREAD;
        double throughput = totalRequests  / wallTime;

        System.out.println("Wall Time: " + wallTime + " seconds");
        System.out.println("Throughput: " + throughput + " requests per second");
        System.out.println("Successful Requests: " + numOfSuccessfulRequests.get());
        System.out.println("Failed Requests: " + numOfFailedRequests.get());
        writeToFile();
        Record.calculateStats("output.csv");
    }

    private static void writeToFile() throws IOException {
        writer = new BufferedWriter(new FileWriter(OUTPUT_FILE, true));
        for (String record : globalQueue) {
            try {
                writer.write(record);
                writer.flush();
            } catch (IOException e) {
                System.err.println("Failed to log request: " + e.getMessage());
            }
        }
    }
}

class Worker implements Runnable {
    private final String ipAddr;
    private final int numberOfApiCalls;
    private final APIClient client;

    public Worker(String ipAddr, int numberOfApiCalls, Queue<String> queue, AtomicLong success, AtomicLong fail) throws IOException {
        this.ipAddr = ipAddr;
        this.numberOfApiCalls = numberOfApiCalls;
        this.client = new APIClient(ipAddr, queue, success, fail);
    }

    @Override
    public void run() {
        for (int i = 0; i < numberOfApiCalls; i++) {
            client.post();
            client.get();
        }
    }
}

class APIClient {
    private final String ipAddr;
    private final HttpClient httpClient;
    private final AtomicLong numOfSuccessfulRequests;
    private final AtomicLong numOfFailedRequests;
    private byte[] imageBytes;
    private final Queue<String> records;
    private static final String MULTIPART_BOUNDARY = "boundary";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String MULTIPART_FORM_DATA = "multipart/form-data;boundary=" + MULTIPART_BOUNDARY;


    public APIClient(String ipAddr, Queue<String> queue, AtomicLong numOfSuccessfulRequests, AtomicLong numOfFailedRequests) throws IOException {
        this.ipAddr = ipAddr;
        this.httpClient = HttpClient.newHttpClient();
        this.records = queue;
        this.numOfSuccessfulRequests = numOfSuccessfulRequests;
        this.numOfFailedRequests = numOfFailedRequests;

    }

    public void post() {
        String albumDataJson = albumDataResponseObject();
        String albumDataPart = createAlbumData(albumDataJson);
        String imagePart = createImage();

        HttpRequest request = buildPostRequest(albumDataPart, imagePart);
        executeAndLogRequest(request, 1);
    }

    public void get() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ipAddr))
                .GET()
                .build();
        executeAndLogRequest(request, 0);
    }

    private String albumDataResponseObject() {
        return "{\n" +
                "    \"artist\": \"Sundri Bun\",\n" +
                "    \"title\": \"Why does it not work!\",\n" +
                "    \"year\": \"2023\"\n" +
                "}";
    }

    private String createAlbumData(String jsonData) {
        return "--" + MULTIPART_BOUNDARY + "\r\n" +
                "Content-Disposition: form-data; name=\"albumData\"\r\n\r\n" +
                jsonData + "\r\n";
    }

    private String createImage() {
        loadImageBytes();
        return "--" + MULTIPART_BOUNDARY + "\r\n" +
                "Content-Disposition: form-data; name=\"image\"; filename=\"filename.jpg\"\r\n" +
                CONTENT_TYPE + ": image/jpeg\r\n\r\n";
    }

    private void loadImageBytes() {
        if (imageBytes == null) {
            try {
                imageBytes = Files.readAllBytes(Path.of("/path/to/image.jpg"));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read image file", e);
            }
        }
    }

    private HttpRequest buildPostRequest(String albumDataPart, String imagePart) {
        List<byte[]> byteList = Arrays.asList(
                albumDataPart.getBytes(),
                imagePart.getBytes(),
                imageBytes,
                ("\r\n--" + MULTIPART_BOUNDARY + "--\r\n").getBytes()
        );

        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArrays(byteList);

        return HttpRequest.newBuilder()
                .uri(URI.create(ipAddr))
                .POST(publisher)
                .header(CONTENT_TYPE, MULTIPART_FORM_DATA)
                .build();
    }


    private void executeAndLogRequest(HttpRequest request, int method) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                long end = System.currentTimeMillis();
                long latency = end - start;
                logRequestMetrics(start, method, latency, statusCode);

                // Handle response status code
                if (statusCode >= 200 && statusCode < 400) {
                    numOfSuccessfulRequests.incrementAndGet();
                    break; // Successful request, break out of the loop
                } else if (statusCode >= 400) {
                    numOfFailedRequests.incrementAndGet();
                    // Log error or handle client error response
                    break; // Error encountered, break out of the loop
                }
            } catch (Exception e) {
                // Log exception or handle the request failure
            }
        }
    }

    private void logRequestMetrics(long startTime, int method, long latency, int statusCode) {
        String record = String.format("%d,%s,%d,%d%n", startTime, method, latency, statusCode);
        this.records.add(record);
    }

}

