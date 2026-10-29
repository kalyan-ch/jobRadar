// Server component: fetches from Spring on the server, so no proxy or CORS.
import { redirect } from 'next/navigation'

export const dynamic = 'force-dynamic'   // render per request; build doesn't need the API up

const API = process.env.JOBS_API ?? 'http://localhost:8081'
const tier = (s) => (s == null ? 'none' : s >= 85 ? 'high' : s >= 70 ? 'mid' : 'low')

function ago(iso) {
  if (!iso) return 'date unknown'
  const days = Math.floor((Date.now() - new Date(iso)) / 86_400_000)
  return days <= 0 ? 'today' : days === 1 ? 'yesterday' : days < 30 ? `${days}d ago` : `${Math.floor(days / 30)}mo ago`
}

// Tabs are plain links (?tab=top), so they work with no client JS and survive a reload.
const TABS = {
  latest: { label: 'Latest', sub: (n) => `Latest ${n} jobs, newest first`, path: '/api/jobs/latest' },
  top: { label: 'Top scored', sub: (n) => `Top ${n} scored jobs, best first`, path: '/api/jobs/top' },
  runs: { label: 'Runs', sub: (n) => `Last ${n} runs`, path: '/api/runs' },
  setup: { label: 'Setup', sub: () => 'What you are looking for, and your resume', path: '/api/profile' },
}

async function get(path) {
  const r = await fetch(`${API}${path}`, { cache: 'no-store' })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return r.json()
}

/** After a Server Action: back to the tab with Spring's error message, or a short notice. */
async function back(tab, r, ok) {
  const error = r.ok ? '' : (await r.json().catch(() => ({}))).message || `HTTP ${r.status}`
  redirect(`/?tab=${tab}&` + (error ? `error=${encodeURIComponent(error)}` : `ok=${encodeURIComponent(ok)}`))
}

function Stat({ label, value }) {
  return (
    <div className="stat">
      <span className="stat-value">{value}</span>
      <span className="stat-label">{label}</span>
    </div>
  )
}

function JobCard({ j }) {
  return (
    <article className="card">
      <div className={`score ${tier(j.score)}`} aria-label={j.score == null ? 'not scored' : `score ${j.score}`}>
        {j.score ?? '—'}
      </div>
      <div className="body">
        <a className="title" href={j.url} target="_blank" rel="noreferrer">{j.title}</a>
        <div className="meta">
          <span className="company">{j.company}</span>
          <span>{j.location || 'Location not listed'}</span>
          <span>{ago(j.postedAt)}</span>
        </div>
        {j.reason && <p className="reason">{j.reason}</p>}
      </div>
      <div className="tags">
        {j.visaSponsorship && <span className={`tag visa-${j.visaSponsorship}`}>visa: {j.visaSponsorship}</span>}
      </div>
    </article>
  )
}

// Plain GET form: the filter lives in the URL, so it survives a reload and live refresh.
function CompanyFilter({ tab, companies, company }) {
  return (
    <form className="filter" method="get">
      <input type="hidden" name="tab" value={tab} />
      <label htmlFor="company">Company</label>
      <select id="company" name="company" defaultValue={company ?? ''}>
        <option value="">All companies</option>
        {companies.map((c) => <option key={c.name} value={c.name}>{c.name} ({c.jobs})</option>)}
      </select>
      <button>Filter</button>
      {company && <a href={`?tab=${tab}`}>Clear</a>}
    </form>
  )
}

const PRESETS = [
  ['fetch,score', 'Full run', 'Fetch from every board, then score new jobs'],
  ['fetch', 'Fetch only', 'No LLM'],
  ['score', 'Score only', 'Score jobs that have no score yet'],
  ['check-slugs', 'Check sources', 'List boards and searches that error or return nothing'],
]

// Server Actions run on the Next server, so Spring stays on localhost. Next checks Origin, so no CSRF.
async function startRun(fd) {
  'use server'
  const r = await fetch(`${API}/api/runs`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ steps: String(fd.get('steps')).split(','), skipWorkday: fd.get('skipWorkday') === 'on' }),
  })
  await back('runs', r, 'Run started')
}

async function saveProfile(fd) {
  'use server'
  const lines = (k) => String(fd.get(k) ?? '').split('\n').map((s) => s.trim()).filter(Boolean)
  const r = await fetch(`${API}/api/profile`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      titles: lines('titles'), locations: lines('locations'), exclude: lines('exclude'),
      remoteOk: fd.get('remoteOk') === 'on', needsSponsorship: fd.get('needsSponsorship') === 'on',
      minPay: Number(fd.get('minPay')) || 0, maxAgeDays: Number(fd.get('maxAgeDays')) || 30,
      notes: String(fd.get('notes') ?? ''),
    }),
  })
  await back('setup', r, 'Preferences saved. They apply from the next run.')
}

async function uploadResume(fd) {
  'use server'
  const body = new FormData()
  body.set('file', fd.get('file'))
  const r = await fetch(`${API}/api/profile/resume`, { method: 'PUT', body })
  await back('setup', r, 'Resume saved')
}

function Field({ id, label, hint, children }) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children}
      {hint && <p className="muted" id={`${id}-hint`}>{hint}</p>}
    </div>
  )
}

function SetupView({ data }) {
  const p = data.profile
  const list = (l) => (l ?? []).join('\n')
  return (
    <div className="setup">
      <form action={saveProfile} className="panel">
        <h2>Preferences</h2>
        <Field id="titles" label="Job titles" hint="One per line. A job matches when its title has every word of one of these.">
          <textarea id="titles" name="titles" rows={4} required defaultValue={list(p.titles)}
            placeholder={'Backend Engineer\nPlatform Engineer'} aria-describedby="titles-hint" />
        </Field>
        <Field id="locations" label="Locations" hint="One per line, matched against the job's location. Empty means anywhere.">
          <textarea id="locations" name="locations" rows={3} defaultValue={list(p.locations)}
            placeholder={'San Francisco\nNew York'} aria-describedby="locations-hint" />
        </Field>
        <label className="check"><input type="checkbox" name="remoteOk" defaultChecked={p.remoteOk} /> Remote jobs are fine</label>
        <label className="check"><input type="checkbox" name="needsSponsorship" defaultChecked={p.needsSponsorship} /> I need visa sponsorship (drops jobs that say they won’t sponsor)</label>
        <div className="row">
          <Field id="minPay" label="Minimum pay (USD/year)" hint="Only drops jobs that list a lower range. 0 = off.">
            <input id="minPay" name="minPay" type="number" min="0" step="1000" defaultValue={p.minPay} aria-describedby="minPay-hint" />
          </Field>
          <Field id="maxAgeDays" label="Max posting age (days)">
            <input id="maxAgeDays" name="maxAgeDays" type="number" min="1" max="365" defaultValue={p.maxAgeDays} />
          </Field>
        </div>
        <Field id="exclude" label="Exclude title words" hint="One per line, whole words. e.g. intern, manager, principal">
          <textarea id="exclude" name="exclude" rows={3} defaultValue={list(p.exclude)} aria-describedby="exclude-hint" />
        </Field>
        <Field id="notes" label="Anything else for the scorer" hint="Plain words the LLM weighs: level, stacks to avoid, company size, industries.">
          <textarea id="notes" name="notes" rows={3} maxLength={2000} defaultValue={p.notes}
            placeholder="Senior level. Prefer Java or Go. No crypto or adtech." aria-describedby="notes-hint" />
        </Field>
        <button className="primary">Save preferences</button>
      </form>

      <form action={uploadResume} className="panel">
        <h2>Resume</h2>
        <p className="sub">
          {data.resumeChars ? `Saved: ${data.resumeChars.toLocaleString('en-US')} characters of text.` : 'No resume yet. Scoring needs one.'}
        </p>
        {data.resumeStart && <pre className="preview">{data.resumeStart}…</pre>}
        <Field id="file" label="Upload PDF or .txt" hint="Text is pulled out and kept on this machine. Scanned image PDFs have no text to read.">
          <input id="file" name="file" type="file" accept=".pdf,.txt,application/pdf,text/plain" required aria-describedby="file-hint" />
        </Field>
        <button className="primary">Upload resume</button>
      </form>
    </div>
  )
}

const when = (iso) => new Date(iso).toLocaleString('en-US', { dateStyle: 'short', timeStyle: 'short' })
const took = (r) => `${Math.round((new Date(r.finishedAt ?? Date.now()) - new Date(r.startedAt)) / 60_000)}m`
const n = (v) => v ?? '—'

function RunsView({ runs }) {
  const running = runs.some((r) => r.state === 'running')
  return (
    <>
      <form action={startRun} className="run-form">
        <div className="presets">
          {PRESETS.map(([steps, label, hint]) => (
            <button key={steps} name="steps" value={steps} disabled={running} title={hint}>{label}</button>
          ))}
        </div>
        <label><input type="checkbox" name="skipWorkday" /> Skip Workday</label>
      </form>
      {running && <p className="sub">A run is going; this page updates as it moves.</p>}
      {runs.length === 0
        ? <div className="empty"><h2>No runs yet</h2><p>Fill in <a href="?tab=setup">Setup</a>, then start one above. A full run also happens daily at 7am.</p></div>
        : (
          <div className="table-wrap">
            <table className="runs">
              <thead>
                <tr><th>#</th><th>Started</th><th>Steps</th><th>State</th><th>Sources</th><th>Failed</th>
                  <th>Fetched</th><th>Kept</th><th>Scored</th><th>Took</th></tr>
              </thead>
              <tbody>
                {runs.map((r) => (
                  <tr key={r.id}>
                    <td>{r.id}</td>
                    <td>{when(r.startedAt)}<div className="muted">{r.triggeredBy}</div></td>
                    <td>{r.steps}{r.skipWorkday && <div className="muted">no Workday</div>}</td>
                    <td>
                      <span className={`tag state-${r.state}`}>{r.state === 'running' ? `running: ${r.step}` : r.state}</span>
                      {r.error && <div className="muted">{r.error}</div>}
                      {r.badSlugs && <details><summary>bad sources</summary><pre>{r.badSlugs}</pre></details>}
                    </td>
                    <td>{n(r.sources)}</td><td>{n(r.sourcesFailed)}</td><td>{n(r.jobsFetched)}</td>
                    <td>{n(r.jobsKept)}</td><td>{n(r.jobsScored)}</td><td>{took(r)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
    </>
  )
}

function Tabs({ tab }) {
  return (
    <nav className="tabs">
      {Object.entries(TABS).map(([k, v]) => (
        <a key={k} href={`?tab=${k}`} className="tab" aria-current={k === tab ? 'page' : undefined}>{v.label}</a>
      ))}
    </nav>
  )
}

function Shell({ tab, sub, stats, error, ok, children }) {
  return (
    <main>
      <header>
        <div>
          <h1>JobRadar</h1>
          <p className="sub">{sub}</p>
        </div>
        {stats}
      </header>
      <Tabs tab={tab} />
      {error && <p className="error" role="alert">{error}</p>}
      {ok && <p className="notice" role="status">{ok}</p>}
      {children}
    </main>
  )
}

export default async function Page({ searchParams }) {
  const { tab: t, error, ok, company } = await searchParams
  const tab = Object.hasOwn(TABS, t ?? '') ? t : 'latest'
  const jobTab = tab === 'latest' || tab === 'top'
  let data, companies
  try {
    ;[data, companies] = await Promise.all([
      get(TABS[tab].path + (jobTab && company ? `?company=${encodeURIComponent(company)}` : '')),
      jobTab ? get('/api/companies') : null,
    ])
  } catch (e) {
    return (
      <main>
        <div className="empty">
          <h2>Can’t reach the JobRadar API</h2>
          <p>{API}: {e.message}. Is the backend running?</p>
        </div>
      </main>
    )
  }

  const shell = { tab, error, ok }
  if (tab === 'setup') return <Shell {...shell} sub={TABS.setup.sub()}><SetupView data={data} /></Shell>
  if (tab === 'runs') return <Shell {...shell} sub={TABS.runs.sub(data.length)}><RunsView runs={data} /></Shell>

  const jobs = data
  const scored = jobs.filter((j) => j.score != null)
  const stats = (
    <div className="stats">
      <Stat label="jobs" value={jobs.length} />
      <Stat label="scored" value={scored.length} />
      <Stat label="score 70+" value={scored.filter((j) => j.score >= 70).length} />
      <Stat label="companies" value={new Set(jobs.map((j) => j.company)).size} />
    </div>
  )
  return (
    <Shell {...shell} sub={TABS[tab].sub(jobs.length) + (company ? ` at ${company}` : '')} stats={stats}>
      {companies.length > 0 && <CompanyFilter tab={tab} companies={companies} company={company} />}
      {jobs.length === 0
        ? <div className="empty"><h2>No jobs yet</h2><p>Fill in <a href="?tab=setup">Setup</a>, then start a full run from <a href="?tab=runs">Runs</a>.</p></div>
        : <section className="list">{jobs.map((j) => <JobCard key={j.id} j={j} />)}</section>}
    </Shell>
  )
}
