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
import java.util.*;
import java.util.concurrent.*;

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

    @Value("${target.skip-existing:false}")
    private Boolean skipExisting;

    @Value("${target.abort-on-error:true}")
    private Boolean abortOnError;

    @Value("${target.upload-interval:1000}")
    private Integer uploadInterval;

    @Value("${target.concurrent-uploads:2}")
    private Integer concurrentUploads;

    @Value("${mirror-path:./mirror/}")
    private String rootMirrorPath;

    private ExecutorService runner;

    public void publish() throws Exception {
        runner = Executors.newFixedThreadPool(concurrentUploads);
        LOG.info("Publishing to " + rootUrl + " ...");
        HttpClient httpClient = HttpClient.newBuilder().build();
        publishDirectory(httpClient, rootUrl, Paths.get(rootMirrorPath).normalize());
        runner.shutdown();
        LOG.info("Publishing complete.");
    }

    public void publishDirectory(HttpClient httpClient, String repositoryUrl, Path mirrorPath)
        throws IOException, ExecutionException, InterruptedException {

        LOG.debug("Switching to mirror directory: " + mirrorPath.toAbsolutePath());

        List<Path> recursePaths = new LinkedList<>();
        Collection<Future<?>> futures = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mirrorPath)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    recursePaths.add(path);
                } else {
                    futures.add(runner.submit(() -> handleFile(httpClient, repositoryUrl, path)));
                }
            }
        }

        for (var future : futures) {
            future.get();
        }

        // Tail recursion
        for (Path recursePath : recursePaths) {
            String subpath = mirrorPath.relativize(recursePath).toString();
            publishDirectory(httpClient, appendUrlPathSegment(repositoryUrl, subpath), recursePath);
        }
    }

    private void handleFile(HttpClient httpClient, String repositoryUrl, Path path) {

        String filename = path.getFileName().toString();
        String targetUrl = repositoryUrl + filename;
        try {
            var baseReq = Utils.setCredentials(HttpRequest.newBuilder(), username, password)
                .uri(URI.create(targetUrl));

            if (skipExisting) {
                var getResponse = httpClient.send(baseReq.method("HEAD", BodyPublishers.noBody()).build(), BodyHandlers.discarding());
                if (getResponse.statusCode() != 404) {
                    LOG.info("Artifact {} already exists", targetUrl);
                    LOG.debug("   Response headers: " + getResponse.headers());
                    return;
                }
            }

            LOG.info("Uploading " + targetUrl);
            Utils.sleep(uploadInterval);

            var putRequest = baseReq.PUT(BodyPublishers.ofFile(path)).build();

            HttpResponse<String> putResponse = httpClient.send(putRequest, BodyHandlers.ofString());
            if (putResponse.statusCode() < 200 || putResponse.statusCode() > 299) {
                LOG.error("Error uploading " + targetUrl + " : Response code was " + putResponse.statusCode());
                LOG.debug("   Response headers: " + putResponse.headers());
                LOG.debug("   Response body: " + putResponse.body());
            }
        }catch (Error | InterruptedException e){
            throw new RuntimeException(e);
        } catch (Exception e) {
            LOG.error("Fail to send " + filename + " to " + targetUrl, e);
            if(abortOnError){
                throw new RuntimeException(e);
            }
        }
    }

    private String appendUrlPathSegment(String baseUrl, String segment) {
        StringBuilder result = new StringBuilder(baseUrl);

        if (!baseUrl.endsWith("/")) {
            result.append('/');
        }
        result.append(segment);
        result.append('/');

        return result.toString();
    }
}
