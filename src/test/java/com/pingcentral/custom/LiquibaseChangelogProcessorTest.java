package com.pingcentral.custom;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class LiquibaseChangelogProcessorTest {

    @Test
    public void testProcessChangelogJsonFile() {
        LiquibaseChangelogProcessor lcp = new LiquibaseChangelogProcessor(
                "src/test/resources/changelog-pc-2.1.0-2.2.0.json",
                "mysql",
                "postgresql"
        );
        try {
            lcp.processLiquibaseFiles();
            assertNotNull(lcp.getChangelogAsJson());
        } catch (Exception e){
            fail(e.getMessage());
        }
    }
}
