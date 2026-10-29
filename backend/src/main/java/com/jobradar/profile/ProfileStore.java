package com.jobradar.profile;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** The one profile row (self-hosted: one user per install). */
@Component
public class ProfileStore {
    private static final JsonMapper JSON = new JsonMapper();

    private final JdbcTemplate jdbc;

    ProfileStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Profile get() {
        var rows = jdbc.queryForList("SELECT data::text FROM profile WHERE id = 1", String.class);
        return rows.isEmpty() || rows.getFirst() == null ? Profile.EMPTY : JSON.readValue(rows.getFirst(), Profile.class);
    }

    public void save(Profile p) {
        jdbc.update("INSERT INTO profile (id, data) VALUES (1, ?::jsonb) ON CONFLICT (id) DO UPDATE SET data = EXCLUDED.data",
                JSON.writeValueAsString(p));
    }

    /** Plain text the LLM scores against; empty until uploaded. */
    public String resume() {
        var rows = jdbc.queryForList("SELECT resume FROM profile WHERE id = 1", String.class);
        return rows.isEmpty() || rows.getFirst() == null ? "" : rows.getFirst();
    }

    public void saveResume(String text) {
        jdbc.update("INSERT INTO profile (id, resume) VALUES (1, ?) ON CONFLICT (id) DO UPDATE SET resume = EXCLUDED.resume",
                text.replace("\0", ""));
    }
}
