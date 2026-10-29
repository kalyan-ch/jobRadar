-- Every job any board has listed. removed_at is set when its board stops listing it, cleared if it comes back.
CREATE TABLE IF NOT EXISTS jobs (
    id          TEXT PRIMARY KEY,
    source      TEXT NOT NULL,          -- board/slug or search/title/location; the unit a sync adds/removes within
    company     TEXT NOT NULL,
    title       TEXT NOT NULL,
    location    TEXT,
    url         TEXT,
    description TEXT,
    posted_at   TIMESTAMPTZ,
    first_seen  TIMESTAMPTZ NOT NULL,
    last_seen   TIMESTAMPTZ NOT NULL,
    removed_at  TIMESTAMPTZ
);

-- ponytail: CREATE ... IF NOT EXISTS is the whole migration story; move to Flyway at the first schema change.
ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS score       INT,
    ADD COLUMN IF NOT EXISTS reason      TEXT,
    ADD COLUMN IF NOT EXISTS visa_sponsorship TEXT CHECK (visa_sponsorship IN ('yes', 'no', 'unknown')),
    ADD COLUMN IF NOT EXISTS scored_at   TIMESTAMPTZ;   -- null = LLM hasn't judged it (or its batch failed)

-- One row per pipeline run (API or 7am schedule). Counts stay null for steps the run didn't include.
CREATE TABLE IF NOT EXISTS runs (
    id               BIGSERIAL PRIMARY KEY,
    steps            TEXT NOT NULL,          -- in run order: fetch,score,check-slugs
    skip_workday     BOOLEAN NOT NULL,
    triggered_by     TEXT NOT NULL,          -- api | schedule
    state            TEXT NOT NULL CHECK (state IN ('running', 'done', 'failed')),
    step             TEXT,                   -- step in progress while running
    sources          INT,                    -- fetches in this run: company boards + search queries
    sources_failed   INT,                    -- fetch errored; for check-slugs also 0 jobs
    jobs_fetched     INT,
    jobs_kept        INT,                    -- past prefilter and saved
    jobs_scored      INT,
    bad_slugs        TEXT,                   -- check-slugs output, one per line
    error            TEXT,
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ
);
-- At most one running row: a second start fails on insert, so two runs never share the LLM.
CREATE UNIQUE INDEX IF NOT EXISTS runs_one_running ON runs ((true)) WHERE state = 'running';
-- Runs on every start: a row still "running" means the server stopped mid-run.
UPDATE runs SET state = 'failed', error = 'server stopped mid-run', finished_at = now() WHERE state = 'running';

-- What the user wants (JSON, see Profile.java) and their resume as plain text. One row: one user per install.
CREATE TABLE IF NOT EXISTS profile (
    id     INT PRIMARY KEY CHECK (id = 1),
    data   JSONB,
    resume TEXT
);
CREATE INDEX IF NOT EXISTS jobs_company ON jobs (lower(company)) WHERE removed_at IS NULL;
