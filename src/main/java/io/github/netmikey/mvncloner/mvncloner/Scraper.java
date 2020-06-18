package io.github.netmikey.mvncloner.mvncloner;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * Component that scrapes content from a remote maven repository using the
 * "index"-style HTML content pages and mirrors it onto the local filesystem.
 * 
 * @author mike
 */
@Component
public class Scraper {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Scraper.class);

    private static final Pattern FILE_URL_PATTERN = Pattern.compile("^.*/([^/]+\\.([^\\./]{1,4}))$");

    private static Set<String> EXTENSION_BLACKLIST = new HashSet<>(Arrays.asList("md5", "sha1", "asc"));

    private static Set<String> FILENAME_BLACKLIST = new HashSet<>(Arrays.asList("maven-metadata.xml"));

    @Value("${source.root-url}")
    private String rootUrl;

    @Value("${source.user:#{null}}")
    private String username;

    @Value("${source.password:#{null}}")
    private String password;

    @Value("${mirror-path:./mirror/}")
    private String rootMirrorPath;

    public void scrape() throws Exception {

        try (final WebClient webClient = new WebClient()) {
            webClient.getOptions().setJavaScriptEnabled(false);
            webClient.getOptions().setCssEnabled(false);

            if (username != null && password != null) {
                setCredentials(webClient, username, password);
            }

            processIndexUrl(webClient, rootUrl, Paths.get(rootMirrorPath));
        }
    }

    private static void setCredentials(WebClient webClient, String username, String password) {
        byte[] usernameAndPasswordBytes = (username + ":" + password).getBytes(StandardCharsets.UTF_8);
        String base64encodedUsernameAndPassword = Base64.getEncoder().encodeToString(usernameAndPasswordBytes);
        webClient.addRequestHeader("Authorization", "Basic " + base64encodedUsernameAndPassword);
    }

    private void processIndexUrl(WebClient webClient, String pageUrl, Path mirrorPath) throws IOException, URISyntaxException {
        LOG.debug("Switching to mirror directory: " + mirrorPath.toAbsolutePath().toString());
        Files.createDirectories(mirrorPath);

        LOG.debug("Getting source repo URL: " + pageUrl);
        HtmlPage page = webClient.getPage(pageUrl);

        List<String> recurseUrls = new ArrayList<>();

        List<HtmlAnchor> links = page.getAnchors();
        for (HtmlAnchor link : links) {
            String fullyQualifiedUrl = page.getFullyQualifiedUrl(link.getHrefAttribute()).toString();
            LOG.trace("   Found link: " + fullyQualifiedUrl);
            // Only consider links to artifacts or subdirectories
            if (fullyQualifiedUrl.startsWith(pageUrl)) {
                Matcher filePatternMatcher = FILE_URL_PATTERN.matcher(fullyQualifiedUrl);
                if (filePatternMatcher.matches()) {
                    // Looks like a link to a file
                    handleFileLink(webClient, mirrorPath, filePatternMatcher);
                } else {
                    // Looks like a link to another subdrectory: recurse
                    LOG.trace("      Mark for recursion.");
                    recurseUrls.add(fullyQualifiedUrl);
                }
            } else {
                // Looks like a link back or to some completely other page:
                // ignore it.
                LOG.trace("      Ignoring this link: destination outside of scope.");
            }
        }

        // Tail recursion
        for (String fullyQualifiedUrl : recurseUrls) {
            URI base = new URI(pageUrl);
            String relativePath = StringUtils.strip(base.relativize(new URI(fullyQualifiedUrl)).toString(), "/ ");
            LOG.debug("   Recursing into: " + relativePath);
            sleep(1000);
            processIndexUrl(webClient, fullyQualifiedUrl, mirrorPath.resolve(relativePath));
        }
    }

    private void handleFileLink(WebClient webClient, Path mirrorPath, Matcher filePatternMatcher) throws IOException {
        String fullyQualifiedUrl = filePatternMatcher.group(0);
        String filename = filePatternMatcher.group(1);
        String extension = filePatternMatcher.group(2);

        if (FILENAME_BLACKLIST.contains(filename.toLowerCase())) {
            LOG.trace("      Ignoring this link: filename in blacklist: " + filename);
        } else if (EXTENSION_BLACKLIST.contains(extension.toLowerCase())) {
            LOG.trace("      Ignoring this link: extension in blacklist: " + extension);
        } else {
            // Download file if it doesn't already exist
            Path targetFile = mirrorPath.resolve(filename);
            if (Files.exists(targetFile)) {
                LOG.debug("      File already exists, skipping download: " + targetFile);
            } else {
                sleep(500);
                LOG.info("      Downloading: " + fullyQualifiedUrl);
                Page page = webClient.getPage(fullyQualifiedUrl);
                Files.copy(page.getWebResponse().getContentAsStream(), targetFile);
                LOG.debug("         ... done.");
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Don't care.
        }
    }
}
