package com.jobradar.filter;

import com.jobradar.domain.Job;
import com.jobradar.profile.Profile;
import com.jobradar.profile.ProfileStore;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cheap rules from the profile that run before the LLM, so token spend stays flat. */
@Component
public class Prefilter {
    private static final Pattern PAY = Pattern.compile("\\$\\s?(\\d{2,3}),?(\\d{3})");
    private static final Pattern VISA = ci("no sponsorship|not able to sponsor|unable to sponsor"
            + "|must be authorized to work.*without sponsorship|us citizen|TS/SCI|security clearance");

    private final ProfileStore store;
    private volatile Rules rules;

    public Prefilter(ProfileStore store) { this.store = store; }

    /** Each fetch starts here, so a profile saved from the UI applies to the next run. */
    public Prefilter reload() { return use(store.get()); }

    public Prefilter use(Profile p) { rules = new Rules(p); return this; }

    private record Rules(Profile p, List<List<String>> titles, Pattern location, Pattern exclude) {
        Rules(Profile p) {
            this(p,
                    p.titles().stream().map(t -> List.of(t.toLowerCase().split("[^\\p{L}\\p{N}+#]+"))).toList(),
                    // ponytail: "non-US" is the only remote exclusion boards write; add others as they show up.
                    p.locations().isEmpty() ? null : ci(String.join("|", p.locations().stream().map(Pattern::quote).toList())
                            + (p.remoteOk() ? "|remote(?!.*non-us)" : "")),
                    // Whole words, so "intern" doesn't drop "Internal Tools" and "ml" doesn't drop "HTML".
                    p.exclude().isEmpty() ? null : ci("\\b(" + String.join("|", p.exclude().stream().map(Pattern::quote).toList()) + ")\\b"));
        }
    }

    private static Pattern ci(String re) { return Pattern.compile(re, Pattern.CASE_INSENSITIVE); }

    public List<Job> apply(List<Job> jobs) { return jobs.stream().filter(this::keep).toList(); }

    public boolean keep(Job j) {
        var r = rules;
        String text = j.title() + "\n" + j.location() + "\n" + j.descriptionText();
        if (!titleOk(j.title())) return false;
        if (!recent(j.postedAt(), r.p().maxAgeDays())) return false;
        if (!locationOk(j.location())) return false;
        if (r.p().needsSponsorship() && VISA.matcher(text).find()) return false;
        return payOk(j.descriptionText());
    }

    /** Workday lists a multi-city job as "2 Locations"; let it through, the detail call has the real ones. */
    public boolean locationOk(String loc) {
        var r = rules;
        String l = Objects.toString(loc, "");
        return r.location() == null || l.matches("\\d+ Locations") || r.location().matcher(l).find();
    }

    /** For boards that pay one detail call per job: run on the list page, so a dropped job costs no request. */
    public boolean listingOk(String title, String loc) { return titleOk(title) && locationOk(loc); }

    /** Every word of one wanted title, in any order: "Backend Engineer" keeps "Senior Engineer, Backend". */
    private boolean titleOk(String title) {
        var r = rules;
        String t = Objects.toString(title, "").toLowerCase();
        if (r.exclude() != null && r.exclude().matcher(t).find()) return false;
        return r.titles().isEmpty() || r.titles().stream().anyMatch(words -> words.stream().allMatch(t::contains));
    }

    /** No date = pass, so a board whose date parsing breaks doesn't silently drop every job. */
    private static boolean recent(Instant posted, int maxAgeDays) {
        return posted == null || posted.isAfter(Instant.now().minus(Duration.ofDays(maxAgeDays)));
    }

    /** No range found = pass; many postings don't disclose. */
    public boolean payOk(String description) {
        long floor = rules.p().minPay();
        if (floor <= 0) return true;
        Matcher m = PAY.matcher(Objects.toString(description, ""));
        long max = 0;
        boolean found = false;
        while (m.find()) {
            found = true;
            max = Math.max(max, Long.parseLong(m.group(1) + m.group(2)));
        }
        return !found || max >= floor;
    }
}
