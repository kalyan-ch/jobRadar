package com.jobradar.ats;

import com.jobradar.config.Boards;
import com.jobradar.domain.Job;
import com.jobradar.profile.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Fans the profile out across company boards (companies.yml) and search boards (aggregators). */
@Component
public class AtsClient {
    private static final Logger log = LoggerFactory.getLogger(AtsClient.class);

    private final Boards config;
    private final Map<String, Board> boards;
    private final List<SearchBoard> searches;

    AtsClient(Boards config, List<Board> boards, List<SearchBoard> searches) {
        this.config = config;
        this.boards = boards.stream().collect(Collectors.toMap(Board::name, Function.identity()));
        this.searches = searches;
    }

    /** One fetch: its source id (the unit a sync adds/removes within) and the call that does it. */
    public record Source(String id, Callable<List<Job>> fetch) {}

    /** Every fetch a run makes. A slug with {title} runs once per profile title; skipWorkday drops Workday
     *  tenants, whose stored jobs, like a failed fetch's, stay untouched. */
    public List<Source> sources(Profile p, boolean skipWorkday) {
        var out = new ArrayList<Source>();
        for (var c : config.companies()) {
            if (skipWorkday && c.board().equals("workday")) continue;
            var titles = c.slug().contains("{title}") ? p.titles() : List.of("");
            for (var t : titles) {
                var slug = c.slug().replace("{title}", t);
                var board = boards.get(c.board());
                out.add(new Source(c.board() + "/" + slug, () -> {
                    if (board == null) throw new IllegalStateException("no Board implementation for " + c.board());
                    return board.fetch(slug);
                }));
            }
        }
        // ponytail: one query per title x location; ~20 titles x 5 places burns a small free tier fast.
        // Cap or rotate queries per run if users hit quota.
        var places = p.locations().isEmpty() ? List.of("") : p.locations();
        for (var s : searches) {
            if (!s.enabled()) continue;
            for (var t : p.titles())
                for (var l : s.name().equals("remotive") ? List.of("") : places)
                    out.add(new Source(s.name() + "/" + t + (l.isEmpty() ? "" : "/" + l),
                            () -> s.search(t, l, p.maxAgeDays())));
        }
        return out;
    }

    /** Hands each source's jobs to sink on its own thread as soon as it's fetched, so saving never waits for
     *  the slowest board. A failed fetch never reaches sink, so the store can't mistake it for "all jobs removed".
     *  Returns how many fetches failed. */
    public int fetchAll(List<Source> sources, BiConsumer<String, List<Job>> sink) {
        int n = sources.size();
        var done = new AtomicInteger();
        var failed = new AtomicInteger();
        // One virtual thread per source: total time is the slowest board, not the sum.
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            sources.forEach(s -> pool.submit(() -> { if (!fetch(s, done, n, sink)) failed.incrementAndGet(); }));
        }
        return failed.get();
    }

    /** Runs every source and lists the ones that error or return 0 jobs: stale entries to fix. */
    public List<String> badSlugs(List<Source> sources) {
        List<Future<String>> futures;
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = sources.stream().map(s -> pool.submit(() -> problem(s))).toList();
        }
        return futures.stream().map(Future::resultNow).filter(s -> s != null).toList();
    }

    private static String problem(Source s) {
        try {
            return s.fetch().call().isEmpty() ? s.id() + ": 0 jobs" : null;
        } catch (Exception e) {
            return s.id() + ": " + brief(e);
        }
    }

    /** Error bodies can be whole HTML pages (Cloudflare challenges); the status line is what matters. */
    public static String brief(Exception e) {
        var s = e.toString();
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    private static boolean fetch(Source s, AtomicInteger done, int n, BiConsumer<String, List<Job>> sink) {
        List<Job> jobs;
        try {
            jobs = s.fetch().call();
        } catch (Exception e) {
            log.warn("[{}/{}] {} fetch failed: {}", done.incrementAndGet(), n, s.id(), brief(e));
            return false;
        }
        log.info("[{}/{}] {}: {} jobs", done.incrementAndGet(), n, s.id(), jobs.size());
        try {   // submit() would swallow this into an unread Future
            sink.accept(s.id(), jobs);
            return true;
        } catch (Exception e) {
            log.warn("{} save failed: {}", s.id(), brief(e));
            return false;
        }
    }
}
