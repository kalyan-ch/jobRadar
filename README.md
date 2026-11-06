# JobRadar

Self-hosted job search. Set the titles, locations and pay you want, upload your resume, and JobRadar pulls jobs from
company career boards and job search APIs, drops the obvious misses, and has Claude score the rest against your resume.
Your data is stored on your machine, but your resume, preferences and each job posting are sent to the Anthropic API for scoring.

```
backend/   Spring Boot API: fetch, filter, score, schedule (Java 25)
ui/        Next.js dashboard: jobs, runs, setup
```

## Setup

On Windows, follow these steps with the changes in [Windows setup](#windows-setup).

### 1. Install the prerequisites

| Tool | Check that it works |
|---|---|
| [Docker](https://docs.docker.com/get-docker/), which must be running | `docker ps` |
| JDK 25 (Gradle won't download it for you) | `java -version` |
| [Node.js](https://nodejs.org/) 20 or newer | `node --version` |
| [Git](https://git-scm.com/downloads) | `git --version` |

### 2. Get the code

```
git clone https://github.com/kalyan-ch/jobRadar.git
cd jobRadar
```

### 3. Create an Anthropic API key

1. Sign in to the [Claude Console](https://console.anthropic.com/) and add credit to the account. Scoring is billed to it; see [What scoring costs](#what-scoring-costs).
2. Create a key under [API keys](https://console.anthropic.com/settings/keys) and copy it. It's only shown once.

### 4. Add your secrets

Create your own secrets file from the template:

```
cd backend
cp secrets.env.example secrets.env
```

Open `backend/secrets.env` and paste your key after the `=`, with no quotes:

```
ANTHROPIC_API_KEY=your-key-here
```

That's the only required setting. `secrets.env` is git-ignored, so your key won't be committed. Don't share the file or paste your key anywhere else.

### 5. Optional: add more job sources

Company boards and Remotive work without a key. To search more widely, uncomment these lines in `secrets.env` and fill them in:

- **Adzuna**: register at [developer.adzuna.com](https://developer.adzuna.com/) (free) and set `ADZUNA_APP_ID` and `ADZUNA_APP_KEY`. Set `ADZUNA_COUNTRY` if you aren't searching in the US.
- **JSearch**: subscribe to JSearch on [RapidAPI](https://rapidapi.com/) and set `JSEARCH_API_KEY` to your RapidAPI key. The free tier is small.

### 6. Start the database and API

From `backend/`:

```
docker compose up -d                          # Postgres on localhost:5434
./gradlew bootRun                             # API on http://localhost:8081 (localhost only)
```

The first start takes a while because Gradle downloads its dependencies. The API is ready when the log says `Started JobRadarApplication`. Leave this terminal open.

### 7. Start the UI

In a second terminal, from the repo root:

```
cd ui
npm install
npm run dev                                   # UI on http://localhost:3001
```

Leave this terminal open too, and open http://localhost:3001.

### 8. Run your first search

1. **Setup** tab: add job titles (one per line), locations and other preferences, then upload a resume (PDF or .txt).
2. **Runs** tab: start **Fetch only**, which costs nothing. The **Kept** column shows how many jobs would be scored.
3. If that number looks right, start **Score only**. If not, narrow your preferences and fetch again.
4. **Latest** and **Top scored** show results. Filter by company with the picker at the top.

After that, **Full run** does both steps. One also starts every day at 7am Pacific while the API is running, and it will score new jobs using your credit.

### Starting and stopping

- **Stop:** press Ctrl-C in the API and UI terminals, then run `docker compose down` in `backend/` to stop Postgres. Your jobs, preferences and resume are kept.
- **Start again:** repeat steps 6 and 7. You can skip `npm install`.
- **After changing `secrets.env`:** restart the API.

### Setup problems

| What you see | What to do |
|---|---|
| The UI says it can't reach the JobRadar API | Check that `./gradlew bootRun` is still running and has finished starting. |
| The API won't start and the log mentions a database connection | Run `docker ps` to check that Docker and Postgres are running, then `docker compose up -d` in `backend/`. |
| Gradle can't find a Java 25 toolchain | Install JDK 25; `java -version` should show 25. |
| A port is already in use | Stop whatever is using 5434, 8081 or 3001. |
| `set ANTHROPIC_API_KEY in backend/secrets.env before scoring` | Check the key is in `backend/secrets.env` with no `#` in front, then restart the API. |

## Windows setup

Follow the steps in [Setup](#setup), with these changes. Run the commands in PowerShell. Native Windows setup hasn't been fully tested yet, so please report anything that doesn't match.

**Step 1: install the prerequisites.** Run this, then open a new PowerShell window so the new commands are found:

```
winget install Docker.DockerDesktop
winget install EclipseAdoptium.Temurin.25.JDK
winget install OpenJS.NodeJS.LTS
winget install Git.Git
```

Start Docker Desktop and wait until it says it's running. It may ask to set up WSL 2 and restart first.

**Step 4: add your secrets.** From `backend\`:

```
Copy-Item secrets.env.example secrets.env
notepad secrets.env
```

Paste your key after `ANTHROPIC_API_KEY=` and save. The file must stay named `secrets.env`; if Windows hides file extensions, check it didn't become `secrets.env.txt`.

**Step 6: start the database and API.** From `backend\`:

```
docker compose up -d
.\gradlew.bat bootRun
```

**Step 7: start the UI.** These commands are the same as in Setup. If Windows Firewall asks about Node.js, choose **Cancel**: the UI still works on your own computer, and this stops other devices on your network from opening it.

**To stop:** press Ctrl-C and answer `Y` if asked to terminate the batch job.

| What you see on Windows | What to do |
|---|---|
| `npm` says running scripts is disabled | Run `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`, or use `npm.cmd` in place of `npm`. |
| `JAVA_HOME is not set` or `java` isn't found | Open a new PowerShell window. If it still fails, reinstall the JDK and check that `java -version` shows 25. |
| `docker` can't connect | Start Docker Desktop and wait until it's running. |

## What scoring costs

Each job is one API request, billed to your key, up to 4 at a time. Jobs already scored are not scored again, so the first run costs the most: keep titles and filters narrow, and use **Fetch only** to see how many jobs you would be scoring first (the **Kept** column).

The default model is `claude-opus-5-5`. Set `CLAUDE_MODEL` to use another; `claude-haiku-5-5` costs far less per job. See [pricing](https://www.anthropic.com/pricing).

## When scoring fails

The Runs tab shows the error under the run's state:

- **failed**: no job got a score. Scoring stops after 8 failures in a row. Check that `ANTHROPIC_API_KEY` is right and the account has credit.
- **done, with an error**: some jobs got a score.

Either way, jobs without a score are retried on the next run, or use **Score only** to retry now.

## Where jobs come from

| Source | Key | Notes |
|---|---|---|
| Company boards in `backend/companies.yml` | none | Greenhouse, Lever, Ashby, Workday, Amazon, Google, Netflix. `{title}` in a slug is replaced by each of your titles. Edit the list to suit your field. |
| Remotive | none | Remote jobs only. |
| Adzuna | `ADZUNA_APP_ID`, `ADZUNA_APP_KEY` (free at developer.adzuna.com) | Aggregates many job sites. Set `ADZUNA_COUNTRY` (default `us`). |
| JSearch | `JSEARCH_API_KEY` (RapidAPI) | Google for Jobs results, which include LinkedIn, Indeed, Dice, Glassdoor and ZipRecruiter listings. Small free tier. |

LinkedIn, Indeed and Dice have no public job search API, and their terms forbid scraping, so JobRadar reaches them only through JSearch.

Sources with no key are skipped. **Check sources** in Runs lists any that error or come back empty.

## Settings (`backend/secrets.env` or shell)

| Variable | Default |
|---|---|
| `ANTHROPIC_API_KEY` | empty; needed to score |
| `CLAUDE_MODEL` | `claude-opus-5-5` |
| `CLAUDE_TIMEOUT` | `2m` per attempt; each job gets up to 4 |
| `ADZUNA_APP_ID` / `ADZUNA_APP_KEY` / `ADZUNA_COUNTRY` | empty / empty / `us` |
| `JSEARCH_API_KEY` | empty |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5434/jobradar` / `jobradar` / `jobradar` |

`secrets.env` is plain `KEY=value` lines with no quotes, and is git-ignored. A variable set in your shell overrides the file. `backend/secrets.env.example` lists all of these except the `DB_` settings, which you only need if you use a different database.

## Notes

- There is no login. The API listens on localhost only. Don't expose it: it holds your resume and spends your Claude credit.
- Changing preferences applies from the next fetch. Jobs that no longer match are hidden then.
- To rescore everything: `docker compose exec postgres-db psql -U jobradar -c "UPDATE jobs SET score=NULL, reason=NULL, visa_sponsorship=NULL, scored_at=NULL"`, then run **Score only**.
