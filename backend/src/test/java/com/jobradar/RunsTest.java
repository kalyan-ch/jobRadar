package com.jobradar;

import com.jobradar.profile.ProfileStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(TestDb.class)
class RunsTest {

    @Autowired Runs runs;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProfileStore profiles;

    @Test
    void rejectsUnknownStepsAndASecondConcurrentRun() {
        assertThrows(IllegalArgumentException.class, () -> runs.start(List.of("fetch", "bogus"), false, "api"));
        assertThrows(IllegalArgumentException.class, () -> runs.start(List.of(), false, "api"));
        profiles.save(Fixtures.PROFILE);
        assertThrows(IllegalArgumentException.class, () -> runs.start(List.of("score"), false, "api"), "no resume yet");

        // A row already running (inserted directly, so no real fetch/LLM work starts) blocks the next start.
        jdbc.update("INSERT INTO runs (steps, skip_workday, triggered_by, state) VALUES ('score', false, 'api', 'running')");
        try {
            assertThrows(DuplicateKeyException.class, () -> runs.start(List.of("fetch"), false, "api"));
            assertEquals(1, runs.recent(20).stream().filter(r -> r.state().equals("running")).count());
        } finally {
            jdbc.update("UPDATE runs SET state = 'done' WHERE state = 'running'");
        }
    }
}
