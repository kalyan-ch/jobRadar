package com.jobradar.store;

import com.jobradar.domain.Job;
import com.jobradar.domain.Scored;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** The jobs table: every prefiltered job and its LLM score. Soft-deleted for a dashboard. */
@Component
public class JobStore {
    // now() is the transaction start, so within one sync "last_seen < now()" means "not in today's fetch".
    // Score columns are left alone: a job already judged is never re-scored.
    private static final String UPSERT = """
            INSERT INTO jobs (id, source, company, title, location, url, description, posted_at, first_seen, last_seen)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (id) DO UPDATE SET source = EXCLUDED.source, company = EXCLUDED.company,
                title = EXCLUDED.title, location = EXCLUDED.location, url = EXCLUDED.url,
                description = EXCLUDED.description, posted_at = EXCLUDED.posted_at,
                last_seen = now(), removed_at = NULL""";
    private static final String REMOVE_MISSING =
            "UPDATE jobs SET removed_at = now() WHERE source = ? AND removed_at IS NULL AND last_seen < now()";

    private final JdbcTemplate jdbc;

    JobStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** One board's fetch. Boards whose fetch failed are never synced, so they keep their jobs. */
    @Transactional
    public void sync(String source, List<Job> jobs) {
        jdbc.batchUpdate(UPSERT, jobs.stream().map(j -> new Object[]{
                j.id(), source, clean(j.company()), clean(j.title()), clean(j.location()), clean(j.url()),
                clean(j.descriptionText()), j.postedAt() == null ? null : j.postedAt().atOffset(ZoneOffset.UTC)
        }).toList());
        jdbc.update(REMOVE_MISSING, source);
    }

    /** Live jobs the LLM hasn't judged yet; a failed batch stays here and is retried next run. */
    public List<Job> unscored() {
        return jdbc.query("SELECT * FROM jobs WHERE removed_at IS NULL AND scored_at IS NULL", (rs, i) -> job(rs));
    }

    public void saveScores(List<Scored> scored) {
        jdbc.batchUpdate("UPDATE jobs SET score = ?, reason = ?, visa_sponsorship = ?, scored_at = now() WHERE id = ?",
                scored.stream().map(s -> new Object[]{s.score(), clean(s.reason()), s.visaSponsorship(), s.job().id()}).toList());
    }

    /** Dashboard row: no description, so 100 rows stay small. score is null until the LLM has judged it. */
    public record Top(String id, String company, String title, String location, String url, Instant postedAt,
                      Integer score, String reason, String visaSponsorship) {}

    /** Newest live jobs, scored or not. Undated boards sort by when we first saw the job. company null = all. */
    public List<Top> latest(int limit, String company) {
        return tops("", "ORDER BY COALESCE(posted_at, first_seen) DESC", limit, company);
    }

    /** Highest-scored live jobs. Ties go to the newer posting. */
    public List<Top> top(int limit, String company) {
        return tops("AND score IS NOT NULL", "ORDER BY score DESC, COALESCE(posted_at, first_seen) DESC", limit, company);
    }

    /** Companies with live jobs, most jobs first, for the dashboard's filter. Case-folded: boards disagree on case. */
    public record Company(String name, int jobs) {}

    public List<Company> companies() {
        return jdbc.query("""
                SELECT min(company) AS name, count(*) AS jobs FROM jobs WHERE removed_at IS NULL AND company <> ''
                GROUP BY lower(company) ORDER BY jobs DESC, name""",
                (rs, i) -> new Company(rs.getString("name"), rs.getInt("jobs")));
    }

    private List<Top> tops(String where, String order, int limit, String company) {
        var byCompany = company == null || company.isBlank() ? "" : "AND lower(company) = lower(?) ";
        var args = byCompany.isEmpty() ? new Object[]{limit} : new Object[]{company, limit};
        return jdbc.query("SELECT * FROM jobs WHERE removed_at IS NULL " + where + " " + byCompany + order + " LIMIT ?",
                (rs, i) -> {
                    var j = job(rs);
                    return new Top(j.id(), j.company(), j.title(), j.location(), j.url(), j.postedAt(),
                            rs.getObject("score", Integer.class), rs.getString("reason"), rs.getString("visa_sponsorship"));
                }, args);
    }

    private static Job job(ResultSet rs) throws SQLException {
        var posted = rs.getObject("posted_at", OffsetDateTime.class);
        return new Job(rs.getString("id"), rs.getString("company"), rs.getString("title"),
                rs.getString("location"), rs.getString("url"), rs.getString("description"),
                posted == null ? null : posted.toInstant());
    }

    /** Postgres TEXT rejects NUL; one scraped NUL would otherwise roll back the whole sync. */
    private static String clean(String s) { return s == null ? null : s.replace("\0", ""); }
}
