package io.github.netmikey.mvncloner.mvncloner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes from the mirror directory into a remote target maven repository.
 * 
 * @author mike
 */
@Component
public class Publisher {

    private static final Logger LOG = LoggerFactory.getLogger(Publisher.class);

    @Value("${target.root-url}")
    private String rootUrl;

    @Value("${target.user:#{null}}")
    private String username;

    @Value("${target.password:#{null}}")
    private String password;

    @Value("${mirror-path:./mirror/}")
    private String rootMirrorPath;

    @Value("${target.publisher-threads:10}")
    private Integer publisherThreads;

    public void publish() throws Exception {
        LOG.info("Publishing to " + rootUrl + " ...");
        ThreadPoolExecutor requestThreadPool = (ThreadPoolExecutor)Executors.newFixedThreadPool(this.publisherThreads);
        HttpClient httpClient = HttpClient.newBuilder().executor(requestThreadPool).build();
        publishDirectory(httpClient, rootUrl, Paths.get(rootMirrorPath).normalize());
        LOG.info("Publishing complete.");
        try {
            requestThreadPool.awaitTermination(600L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOG.error(e.getMessage());
        }
        requestThreadPool.shutdown();
    }

    public void publishDirectory(HttpClient httpClient, String repositoryUrl, Path mirrorPath)
        throws IOException, InterruptedException {

        LOG.debug("Switching to mirror directory: " + mirrorPath.toAbsolutePath());

        List<Path> recursePaths = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mirrorPath)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    recursePaths.add(path);
                } else {
                    handleFile(httpClient, repositoryUrl, path);
                }
            }
        }

        // Tail recursion
        for (Path recursePath : recursePaths) {
            String subpath = mirrorPath.relativize(recursePath).toString();
            publishDirectory(httpClient, appendUrlPathSegment(repositoryUrl, subpath), recursePath);
        }
    }

    private void handleFile(HttpClient httpClient, String repositoryUrl, Path path)
        throws IOException, InterruptedException {

        String filename = path.getFileName().toString();
        String targetUrl = repositoryUrl + filename;
        LOG.info("Uploading " + targetUrl);

//        Utils.sleep(Long.parseLong(this.sleepTimeInMS));
        byte[] payload = Files.readAllBytes(path);
        HttpRequest request = Utils.setCredentials(HttpRequest.newBuilder(), username, password)
            .uri(URI.create(targetUrl))
            .timeout(Duration.ofMinutes(2))
            .PUT(BodyPublishers.ofByteArray(payload))
            .build();
        httpClient.sendAsync(request, BodyHandlers.ofString())
            .thenApply(HttpResponse::statusCode)
            .thenAccept(Publisher::getStatusMessage)
            .join();
    }

    private String appendUrlPathSegment(String baseUrl, String segment) {
        StringBuffer result = new StringBuffer(baseUrl);

        if (!baseUrl.endsWith("/")) {
            result.append('/');
        }
        result.append(segment);
        result.append('/');

        return result.toString();
    }

    private static void getStatusMessage(int statusCode) {
        if (statusCode >= 200 && statusCode <= 299) {
            LOG.info("Uploaded Successfully!");
        } else if (statusCode == 403) {
            LOG.info("Already uploaded");
        } else {
            LOG.error("Something bad happened during upload: " + statusCode);
        }
    }
}
