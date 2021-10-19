package io.github.netmikey.mvncloner.mvncloner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.Runnable;
import java.util.concurrent.ThreadPoolExecutor;
import java.lang.RuntimeException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.UnexpectedPage;
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

    private static Set<String> EXTENSION_BLACKLIST = new HashSet<>(
        Arrays.asList("md5", "sha1", "asc", "sha256", "sha512"));

    private static Set<String> FILENAME_BLACKLIST = new HashSet<>(
        Arrays.asList("maven-metadata.xml", "archetype-catalog.xml", 
        "nexus-maven-repository-index.gz", "nexus-maven-repository-index.properties"));

    @Value("${source.root-url}")
    private String rootUrl;

    @Value("${source.user:#{null}}")
    private String username;

    @Value("${source.password:#{null}}")
    private String password;

    @Value("${source.scraper-threads:10}")
    private Integer scraperThreads;

    @Value("${mirror-path:./mirror/}")
    private String rootMirrorPath;

    public void mirror() throws Exception {

        try (final WebClient webClient = new WebClient()) {
            webClient.getOptions().setJavaScriptEnabled(false);
            webClient.getOptions().setCssEnabled(false);
            // Set proxy
            // Optional<Proxy> proxy = ProxySelector.getDefault().select(new URI(rootUrl)).stream().findFirst();
            // proxy.ifPresent(theProxy -> {
            //     InetSocketAddress proxyAddress = (InetSocketAddress) theProxy.address();
            //     if (proxyAddress != null) {
            //         webClient.getOptions()
            //             .setProxyConfig(new ProxyConfig(proxyAddress.getHostName(), proxyAddress.getPort()));
            //     }
            // });
            // Set credentials
            Utils.setCredentials(webClient, username, password);
            ThreadPoolExecutor requestThreadPool = (ThreadPoolExecutor)webClient.getExecutor();
            requestThreadPool.setCorePoolSize(this.scraperThreads);
            requestThreadPool.setMaximumPoolSize(this.scraperThreads);

            LOG.info("Mirroring from " + rootUrl + " ...");

            processIndexUrl(webClient, rootUrl, Paths.get(rootMirrorPath));
            requestThreadPool.shutdown();
            try {
                // wait for long downloads to finish
                requestThreadPool.awaitTermination(20L, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                LOG.error(e.getMessage());
            }

            LOG.info("Download complete.");
        }
    }

    private void processIndexUrl(WebClient webClient, String pageUrl, Path mirrorPath)
        throws IOException, URISyntaxException {
        LOG.debug("Switching to mirror directory: " + mirrorPath.toAbsolutePath().toString());
        Files.createDirectories(mirrorPath);

        LOG.debug("Getting source repo URL: " + pageUrl);
        Page rawPage = webClient.getPage(pageUrl);
        HtmlPage page = null;
        if (!HtmlPage.class.isInstance(rawPage)) {
            LOG.error("unexpected response from urlL: " + pageUrl.toString());
            Files.copy(rawPage.getWebResponse().getContentAsStream(), Paths.get("./mirror/wtfisthis"));
            throw new RuntimeException("got an unexpected Page type: " + page.toString());
        } else {
            page = (HtmlPage)rawPage;
        }

        List<String> recurseUrls = new ArrayList<>();

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
            // Utils.sleep(Long.parseLong(this.sleepTimeInMS));
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
                LOG.info("      File already exists, skipping download: " + targetFile);
            } else {
                // Utils.sleep(Long.parseLong(this.sleepTimeInMS));
                webClient.getExecutor().execute(
                    new RunnableDownloader(fullyQualifiedUrl, this.username, this.password, targetFile));
            }
        }
    }

    private class RunnableDownloader implements Runnable {
        private String url = null;
        private String username = null;
        private String password = null;
        private Path targetFile = null;

        public RunnableDownloader(String url, String username, String password, Path targetFile) {
            this.url = url;
            this.username = username;
            this.password = password;
            this.targetFile = targetFile;
        }

        public void run() {
            try (final WebClient webClient = new WebClient()) {
                LOG.info("      Downloading: " + url);
                webClient.getOptions().setJavaScriptEnabled(false);
                webClient.getOptions().setCssEnabled(false);
                // Set credentials
                Utils.setCredentials(webClient, username, password);
                Page page = webClient.getPage(url);
                Files.copy(page.getWebResponse().getContentAsStream(), targetFile);
                LOG.debug("         ... done.");
            } catch (IOException exception) {
                LOG.error(exception.getMessage());
            }
        }
    }
}
