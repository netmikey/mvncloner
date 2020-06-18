package io.github.netmikey.mvncloner.mvncloner;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Test the tool manually from the IDE.
 * 
 * @author mike
 */
@Disabled("To be executed manually from the IDE")
@SpringBootTest(
    args = {
            "--source.root-url=https://[maven-repo]/maven2/[some-path]/",
            "--mirror-path=./build/mirror/" })
public class ManualTest {

    @Test
    public void testSomething() throws Exception {
        // MvnCleaner executed by spring
    }
}
