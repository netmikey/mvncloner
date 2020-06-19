package io.github.netmikey.mvncloner.mvncloner;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Autowired
    private Publisher publisher;

    @Value("${actions:mirror,publish}")
    private Set<String> actions;

    @Override
    public void run(String... args) throws Exception {

        if (actions.contains("mirror")) {
            scraper.mirror();
        }
        if (actions.contains("publish")) {
            publisher.publish();
        }

        LOG.info("Done.");
    }
}
