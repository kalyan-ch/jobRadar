package com.jobradar;

import com.jobradar.domain.Job;
import com.jobradar.domain.Scored;
import com.jobradar.profile.ProfileStore;
import com.jobradar.store.JobStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Nothing listens on port 1, so every Claude call is refused. */
@SpringBootTest(properties = {"jobradar.anthropic-url=http://127.0.0.1:1", "jobradar.anthropic-key=test"})
@Import(TestDb.class)
class RunsScoringTest {

    @Autowired Runs runs;
    @Autowired ProfileStore profiles;
    @Autowired JobStore jobs;
    @Autowired JdbcTemplate jdbc;

    @Test
    void setupTracksEachChecklistStep() {
        jdbc.update("TRUNCATE jobs, runs, profile");   // the two tests share one database
        assertEquals(new Runs.Setup(true, false, false, false, 0, false, false), runs.setup());

        profiles.save(Fixtures.PROFILE);
        profiles.saveResume("resume");
        var job = new Job("setup", "co", "t", "l", "u", "d", null);
        jobs.sync("setup-test", List.of(job));
        jdbc.update("INSERT INTO runs (steps, skip_workday, triggered_by, state, jobs_fetched) VALUES ('fetch', false, 'api', 'done', 1)");
        assertEquals(new Runs.Setup(true, true, true, true, 1, false, false), runs.setup());

        jobs.saveScores(List.of(new Scored(job, 80, "fits", "no")));
        assertEquals(new Runs.Setup(true, true, true, true, 0, true, false), runs.setup());
    }

    @Test
    void unreachableClaudeFailsTheRunWithTheReason() throws InterruptedException {
        jdbc.update("TRUNCATE jobs, runs, profile");   // the two tests share one database
        profiles.save(Fixtures.PROFILE);
        profiles.saveResume("resume");

        var nothingToScore = finished(runs.start(List.of("score"), false, "api").id());
        assertEquals("done", nothingToScore.state());
        assertNull(nothingToScore.error());

        jobs.sync("test", List.of(new Job("a", "co", "t", "l", "u", "d", null)));
        var dead = finished(runs.start(List.of("score"), false, "api").id());
        assertEquals("failed", dead.state());
        assertEquals(0, dead.jobsScored());
        assertTrue(dead.error().contains("1 of 1 jobs not scored"), dead.error());
        assertTrue(dead.error().contains("First error: "), dead.error());
    }

    private Runs.Run finished(long id) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            var run = runs.recent(20).stream().filter(r -> r.id() == id).findFirst().orElseThrow();
            if (!run.state().equals("running")) return run;
            Thread.sleep(100);
        }
        return fail("run " + id + " still going after 20s");
    }
}
