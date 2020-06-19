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

    public void publish() throws Exception {
        LOG.info("Publishing to " + rootUrl + " ...");
        HttpClient httpClient = HttpClient.newBuilder().build();
        publishDirectory(httpClient, rootUrl, Paths.get(rootMirrorPath).normalize());
        LOG.info("Publishing complete.");
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

        Utils.sleep(1000);
        HttpRequest request = Utils.setCredentials(HttpRequest.newBuilder(), username, password)
            .uri(URI.create(targetUrl))
            .PUT(BodyPublishers.ofInputStream(() -> {
                try {
                    return Files.newInputStream(path, StandardOpenOption.READ);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }))
            .build();
        HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            LOG.error("Error uploading " + targetUrl + " : Response code was " + response.statusCode());
            LOG.debug("   Response headers: " + response.headers());
            LOG.debug("   Response body: " + response.body());
        }
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
}
