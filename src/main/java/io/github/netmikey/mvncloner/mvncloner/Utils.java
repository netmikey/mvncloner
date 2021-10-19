package io.github.netmikey.mvncloner.mvncloner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest.Builder;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import com.gargoylesoftware.htmlunit.WebClient;

/**
 * @author mike
 */
public class Utils {

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

    public static String computeFileSHA1( File file ) throws IOException
    {
        String sha1 = null;
        MessageDigest digest;
        try
        {
            digest = MessageDigest.getInstance( "SHA-1" );
        }
        catch ( NoSuchAlgorithmException e1 )
        {
            throw new IOException( "Impossible to get SHA-1 digester", e1 );
        }
        try (InputStream input = new FileInputStream( file );
             DigestInputStream digestStream = new DigestInputStream( input, digest ) )
        {
            while(digestStream.read() != -1){
                // read file stream without buffer
            }
            MessageDigest msgDigest = digestStream.getMessageDigest();
            StringBuilder result = new StringBuilder();
            for (byte aByte : msgDigest.digest()) {
                result.append(String.format("%02x", aByte));
            }
            sha1 = result.toString();
        }
        return sha1;
    }
}
