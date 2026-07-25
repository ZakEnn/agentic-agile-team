package com.agile.team;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the full application context must start.
 * <p>
 * Runs on the {@code test} profile (H2, Flyway disabled) so it needs no
 * hand-run MySQL and no Docker — an M0 exit criterion. Previously this test
 * required a developer to have MySQL listening on localhost:3306, which meant
 * CI could never be green on a clean checkout.
 */
@SpringBootTest
@ActiveProfiles("test")
class TeamApplicationTests {

	@Test
	void contextLoads() {
	}

}
