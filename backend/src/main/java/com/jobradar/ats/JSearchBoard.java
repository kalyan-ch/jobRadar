package com.jobradar.ats;

import com.jobradar.config.Props;
import com.jobradar.domain.Job;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JSearch on RapidAPI — Google for Jobs results, so it covers LinkedIn, Indeed, Dice, Glassdoor, ZipRecruiter
 * and company sites through one licensed API. Free tier is small (about 200 requests/month), so one page per query.
 */
@Component
public class JSearchBoard implements SearchBoard {
    private static final String HOST = "jsearch.p.rapidapi.com";

    private final Http http;
    private final Props p;

    JSearchBoard(Http http, Props p) { this.http = http; this.p = p; }

    @Override public String name() { return "jsearch"; }

    @Override public boolean enabled() { return !p.jsearchKey().isBlank(); }

    @Override
    public List<Job> search(String title, String location, int maxAgeDays) {
        var res = http.get("https://" + HOST + "/search?query=" + title + (location.isEmpty() ? "" : " in " + location)
                        + "&page=1&num_pages=1&date_posted=" + window(maxAgeDays),
                Map.of("X-RapidAPI-Key", p.jsearchKey(), "X-RapidAPI-Host", HOST));
        return parse(res);
    }

    /** JSearch only takes fixed windows; pick the smallest that covers maxAgeDays. */
    static String window(int days) {
        return days <= 1 ? "today" : days <= 3 ? "3days" : days <= 7 ? "week" : days <= 31 ? "month" : "all";
    }

    static List<Job> parse(JsonNode res) {
        var jobs = new ArrayList<Job>();
        for (var j : res.path("data")) {
            var loc = Stream.of("job_city", "job_state", "job_country").map(f -> j.path(f).asString(""))
                    .filter(s -> !s.isBlank()).collect(Collectors.joining(", "));
            var pay = j.path("job_max_salary").asLong(0);
            jobs.add(new Job(
                    "js:" + j.path("job_id").asString(),
                    j.path("employer_name").asString(""),
                    j.path("job_title").asString(""),
                    j.path("job_is_remote").asBoolean(false) ? (loc.isEmpty() ? "Remote" : loc + " (Remote)") : loc,
                    j.path("job_apply_link").asString(""),
                    j.path("job_description").asString("") + (pay > 0 ? "\nSalary up to $" + pay : ""),
                    Http.instant(j.path("job_posted_at_datetime_utc").asString(""))));
        }
        return jobs;
    }
}
