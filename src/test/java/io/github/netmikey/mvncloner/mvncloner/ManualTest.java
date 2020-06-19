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
            "--source.root-url=https://sourcerepo/nexus/content/repositories/my-source-repo/",
            "--mirror-path=./build/mirror/",
            "--source.user=source-user",
            "--source.password=source-pwd",
            "--target.root-url=https://targetrepo/repository/my-target-repo/",
            "--target.user=target-user",
            "--target.password=target-pwd",
    })
public class ManualTest {

    @Test
    public void testSomething() throws Exception {
        // MvnCleaner executed by spring
    }
}
