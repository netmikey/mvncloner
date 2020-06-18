package io.github.netmikey.mvncloner.mvncloner;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @author mike
 */
@Component
public class MvnCloner implements CommandLineRunner {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MvnCloner.class);

    @Autowired
    private Scraper scraper;

    @Override
    public void run(String... args) throws Exception {

        scraper.scrape();

        LOG.info("Done.");
    }
}
