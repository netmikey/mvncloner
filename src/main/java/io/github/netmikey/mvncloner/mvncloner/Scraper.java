package io.github.netmikey.mvncloner.mvncloner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.ProxyConfig;
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

    private static final Pattern FILE_URL_PATTERN = Pattern.compile("^.*/([^/]+\\.([^\\./]{1,6}))$");

    private static final Set<String> EXTENSION_BLACKLIST = Set.of("md5", "sha1", "asc", "sha256", "sha512");

    private static final Set<String> FILENAME_BLACKLIST = Set.of("maven-metadata.xml", "archetype-catalog.xml");

    @Value("${source.root-url}")
    private String rootUrl;

    @Value("${source.user:#{null}}")
    private String username;

    @Value("${source.password:#{null}}")
    private String password;

    @Value("${mirror-path:./mirror/}")
    private String rootMirrorPath;

    @Value("${source.download-interval:1000}")
    private Integer downloadInterval;

    public void mirror() throws Exception {
        Utils.withNewWebClient(rootUrl, username, password, webClient -> {
            LOG.info("Mirroring from " + rootUrl + " ...");
            try {
                processIndexUrl(webClient, rootUrl, Paths.get(rootMirrorPath));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
            LOG.info("Download complete.");
        });
    }

    private void processIndexUrl(WebClient webClient, String pageUrl, Path mirrorPath)
        throws IOException, URISyntaxException {
        LOG.debug("Switching to mirror directory: " + mirrorPath.toAbsolutePath().toString());
        Files.createDirectories(mirrorPath);

        LOG.debug("Getting source repo URL: " + pageUrl);
        HtmlPage page = webClient.getPage(pageUrl);

        List<String> recurseUrls = new LinkedList<>();

        String pageHost = new URL(pageUrl).getHost();

        List<HtmlAnchor> links = page.getAnchors();
        for (HtmlAnchor link : links) {
            String fullyQualifiedUrl = page.getFullyQualifiedUrl(link.getHrefAttribute()).toString();
            LOG.trace("   Found link: " + fullyQualifiedUrl);
            // Avoid crawling out into the open...
            if (new URL(fullyQualifiedUrl).getHost().equals(pageHost)) {
                Matcher filePatternMatcher = FILE_URL_PATTERN.matcher(fullyQualifiedUrl);
                if (filePatternMatcher.matches()) {
                    // Looks like a link to a file
                    handleFileLink(webClient, mirrorPath, filePatternMatcher);
                } else {
                    // Only consider links to artifacts or subdirectories
                    if (fullyQualifiedUrl.startsWith(pageUrl)) {
                        // Looks like a link to another subdrectory: recurse
                        LOG.trace("      Mark for recursion.");
                        recurseUrls.add(fullyQualifiedUrl);
                    } else {
                        // Looks like a link back or to some completely other
                        // page: ignore it.
                        LOG.trace("      Ignoring this link: destination outside of scope.");
                    }
                }
            } else {
                // Looks like a link to some completely other page: ignore it.
                LOG.trace("      Ignoring this link: destination outside of scope.");
            }
        }

        // Tail recursion
        for (String fullyQualifiedUrl : recurseUrls) {
            URI base = new URI(pageUrl);
            String relativePath = StringUtils.strip(base.relativize(new URI(fullyQualifiedUrl)).toString(), "/ ");
            LOG.debug("   Recursing into: " + relativePath);
            Utils.sleep(downloadInterval);
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
                Utils.sleep(downloadInterval);
                LOG.info("      Downloading: " + fullyQualifiedUrl);
                Page page = webClient.getPage(fullyQualifiedUrl);
                Files.copy(page.getWebResponse().getContentAsStream(), targetFile);
                LOG.debug("         ... done.");
            }
        }
    }
}
