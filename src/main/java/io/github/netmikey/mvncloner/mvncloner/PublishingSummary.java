package io.github.netmikey.mvncloner.mvncloner;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Holds a summary of a publishing action.
 */
public class PublishingSummary {

    private AtomicInteger published;

    private AtomicInteger skippedAlreadyPresent;

    private AtomicInteger failed;

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
            .append("published", getPublished())
            .append("skippedAlreadyPresent", getSkippedAlreadyPresent())
            .append("failed", getFailed())
            .append("total", getTotal()).toString();
    }

    /**
     * Increment the `published` counter by 1.
     */
    public void incrementPublished() {
        published.incrementAndGet();
    }

    /**
     * Increment the `skippedAlreadyPresent` counter by 1.
     */
    public void incrementSkippedAlreadyPresent() {
        skippedAlreadyPresent.incrementAndGet();
    }

    /**
     * Increment the `failed` counter by 1.
     */
    public void incrementFailed() {
        failed.incrementAndGet();
    }

    /**
     * Get the sum of files processed.
     * 
     * @return The total number of files processed.
     */
    public int getTotal() {
        return published.get() + skippedAlreadyPresent.get() + failed.get();
    }

    /**
     * Get the published.
     * 
     * @return Returns the published.
     */
    public int getPublished() {
        return published.get();
    }

    /**
     * Get the skippedAlreadyPresent.
     * 
     * @return Returns the skippedAlreadyPresent.
     */
    public int getSkippedAlreadyPresent() {
        return skippedAlreadyPresent.get();
    }

    /**
     * Get the failed.
     * 
     * @return Returns the failed.
     */
    public int getFailed() {
        return failed.get();
    }

}
