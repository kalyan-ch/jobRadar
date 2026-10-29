# JobRadar

Self-hosted job search. Set the titles, locations and pay you want, upload your resume, and JobRadar pulls jobs from
company career boards and job search APIs, drops the obvious misses, and has an LLM score the rest against your resume.
Everything stays on your machine.

```
backend/   Spring Boot API: fetch, filter, score, schedule (Java 25)
ui/        Next.js dashboard: jobs, runs, setup
```

## Run

```
cd backend
docker compose up -d                          # Postgres on localhost:5434
./gradlew bootRun                             # API on http://localhost:8081 (localhost only)
cd ../ui && npm install && npm run dev        # UI on http://localhost:3001
```

You also need an OpenAI-compatible LLM. The default is Ollama: `ollama pull qwen2.5:7b-instruct`.

1. **Setup** tab: add job titles (one per line), locations and other preferences, then upload a resume (PDF or .txt).
2. **Runs** tab: start a **Full run**. A full run also happens every day at 7am Pacific.
3. **Latest** and **Top scored** show results. Filter by company with the picker at the top.

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
| `LLM_URL` | `http://localhost:11434` (Ollama) |
| `LLM_API_KEY` | empty |
| `LLM_MODEL` | `qwen2.5:7b-instruct` |
| `ADZUNA_APP_ID` / `ADZUNA_APP_KEY` / `ADZUNA_COUNTRY` | empty / empty / `us` |
| `JSEARCH_API_KEY` | empty |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5434/jobradar` / `jobradar` / `jobradar` |

`secrets.env` is plain `KEY=value` lines and is git-ignored.

## Notes

- There is no login. The API listens on localhost only. Don't expose it: it holds your resume and spends LLM time.
- Changing preferences applies from the next fetch. Jobs that no longer match are hidden then.
- To rescore everything: `docker compose exec postgres-db psql -U jobradar -c "UPDATE jobs SET score=NULL, reason=NULL, visa_sponsorship=NULL, scored_at=NULL"`, then run **Score only**.
