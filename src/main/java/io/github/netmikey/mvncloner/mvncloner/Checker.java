package io.github.netmikey.mvncloner.mvncloner;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;

/**
 * Checks the connections that will be used by the Scraper and the Publisher.
 */
@Component
public class Checker {

    private static final Logger LOG = LoggerFactory.getLogger(Checker.class);

    @Value("${source.root-url}")
    private String sourceRootUrl;

    @Value("${source.user:#{null}}")
    private String sourceUsername;

    @Value("${source.password:#{null}}")
    private String sourcePassword;

    @Value("${target.root-url}")
    private String targetRootUrl;

    @Value("${target.user:#{null}}")
    private String targetUsername;

    @Value("${target.password:#{null}}")
    private String targetPassword;

    public void check() {
        checkSource();
        checkTarget();
    }

    private void checkSource() {
        try {
            Utils.withNewWebClient(sourceRootUrl, sourceUsername, sourcePassword, webClient -> {
                try {
                    var page = webClient.getPage(sourceRootUrl);
                    LOG.info("Source connection check succeeded! ({}, responded with {})", sourceRootUrl,
                        page.getWebResponse().getStatusCode());
                } catch (MalformedURLException e) {
                    throw new IllegalStateException("Connection check failed: malformed source url " + sourceRootUrl
                        + " caused: " + e.getMessage(), e);
                } catch (IOException e) {
                    throw new IllegalStateException("Connection check failed: source at " + sourceRootUrl
                        + " caused IOException: " + e.getMessage(), e);
                } catch (FailingHttpStatusCodeException e) {
                    throw new IllegalStateException("Connection check failed: source at " + sourceRootUrl
                        + " responded with http status code " + e.getStatusCode() + ": " + e.getMessage(), e);
                }
            });
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Connection check failed: malformed source url " + sourceRootUrl
                + " caused: " + e.getMessage(), e);
        }
    }

    private void checkTarget() throws IllegalStateException {
        HttpClient httpClient = HttpClient.newBuilder().build();
        try {
            var baseReq = Utils.setCredentials(HttpRequest.newBuilder(), targetUsername, targetPassword)
                .uri(new URI(targetRootUrl));
            var response = httpClient.send(baseReq.method("HEAD", BodyPublishers.noBody()).build(), BodyHandlers.discarding());

            if (response.statusCode() != 200) {
                throw new IllegalStateException("Connection check failed: target at " + targetRootUrl
                    + " responded with http status code " + response.statusCode());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Connection check failed: target at " + targetRootUrl
                + " caused IOException: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Connection check failed: interrupted while checking target at " + targetRootUrl
                + ": " + e.getMessage(), e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Connection check failed: malformed target url " + targetRootUrl
                + " caused: " + e.getMessage(), e);
        }
    }
}
