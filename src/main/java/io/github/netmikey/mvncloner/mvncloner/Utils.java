package io.github.netmikey.mvncloner.mvncloner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest.Builder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Consumer;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.ProxyConfig;
import com.gargoylesoftware.htmlunit.WebClient;

/**
 * @author mike
 */
public class Utils {

    /**
     * Create a new {@link WebClient} instance, configure credentials and proxy
     * and execute the specified callback on it.
     * 
     * @param rootUrl
     *            The rootUrl the WebClient will be targeting. Used for chosing
     *            the right proxy.
     * @param username
     *            The username to be used.
     * @param password
     *            The password to be used.
     * @param callback
     *            The callback function.
     * @throws URISyntaxException
     *             If the specified rootUrl is not valid.
     */
    public static void withNewWebClient(String rootUrl, String username, String password, Consumer<WebClient> callback)
        throws URISyntaxException {

        try (final WebClient webClient = new WebClient()) {
            webClient.getOptions().setJavaScriptEnabled(false);
            webClient.getOptions().setCssEnabled(false);

            // Set proxy
            Optional<Proxy> proxy = ProxySelector.getDefault().select(new URI(rootUrl)).stream().findFirst();
            proxy.ifPresent(theProxy -> {
                InetSocketAddress proxyAddress = (InetSocketAddress) theProxy.address();
                if (proxyAddress != null) {
                    webClient.getOptions()
                        .setProxyConfig(new ProxyConfig(proxyAddress.getHostName(), proxyAddress.getPort()));
                }
            });
            // Set credentials
            Utils.setCredentials(webClient, username, password);

            callback.accept(webClient);
        }
    }

    /**
     * Set an authentication header with the specified credentials for all
     * requests of the web client if username and password are not
     * <code>null</code>.
     * 
     * @param webClient
     *            The web client to set the header on.
     * @param username
     *            The username to be used.
     * @param password
     *            The password to be used.
     */
    public static void setCredentials(WebClient webClient, String username, String password) {
        if (username != null && password != null) {
            webClient.addRequestHeader("Authorization", authorizationHeaderValue(username, password));
        }
    }

    /**
     * Set an authentication header with the specified credentials on the
     * HttpRequest Builder if username and password are not <code>null</code>.
     * 
     * @param requestBuilder
     *            The request builder to set the header on.
     * @param username
     *            The username to be used.
     * @param password
     *            The password to be used.
     */
    public static Builder setCredentials(Builder requestBuilder, String username, String password) {
        if (username != null && password != null) {
            requestBuilder.setHeader("Authorization", authorizationHeaderValue(username, password));
        }
        return requestBuilder;
    }

    private static String authorizationHeaderValue(String username, String password) {
        byte[] usernameAndPasswordBytes = (username + ":" + password).getBytes(StandardCharsets.UTF_8);
        String base64encodedUsernameAndPassword = Base64.getEncoder().encodeToString(usernameAndPasswordBytes);
        return "Basic " + base64encodedUsernameAndPassword;
    }

    /**
     * Failsafe sleep.
     * 
     * @param millis
     *            the length of time to sleep in milliseconds.
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // Don't care.
        }
    }
}
