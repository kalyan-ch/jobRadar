package com.jobradar.ats;

import com.jobradar.config.Props;
import com.jobradar.domain.Job;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Adzuna — free official API (key at developer.adzuna.com) that aggregates many job sites per country.
 * Descriptions are ~500-char snippets, so the LLM sees less than for a company board.
 */
@Component
public class AdzunaBoard implements SearchBoard {
    private static final int PAGE = 50, PAGES = 4;

    private final Http http;
    private final Props p;

    AdzunaBoard(Http http, Props p) { this.http = http; this.p = p; }

    @Override public String name() { return "adzuna"; }

    @Override public boolean enabled() { return !p.adzunaId().isBlank() && !p.adzunaKey().isBlank(); }

    @Override
    public List<Job> search(String title, String location, int maxAgeDays) {
        var jobs = new ArrayList<Job>();
        for (int page = 1; page <= PAGES; page++) {
            var res = http.get("https://api.adzuna.com/v1/api/jobs/" + p.adzunaCountry() + "/search/" + page
                    + "?app_id=" + p.adzunaId() + "&app_key=" + p.adzunaKey() + "&results_per_page=" + PAGE
                    + "&what_phrase=" + title + (location.isEmpty() ? "" : "&where=" + location)
                    + "&max_days_old=" + maxAgeDays + "&sort_by=date&content-type=application/json");
            var page1 = parse(res);
            jobs.addAll(page1);
            if (page1.size() < PAGE) break;
        }
        return jobs;
    }

    static List<Job> parse(JsonNode res) {
        var jobs = new ArrayList<Job>();
        for (var j : res.path("results")) {
            // Salary goes in the text so the pay prefilter sees it like a posted range.
            var pay = j.path("salary_max").asLong(0);
            jobs.add(new Job(
                    "adz:" + j.path("id").asString(),
                    j.path("company").path("display_name").asString(""),
                    Http.plain(j.path("title").asString("")),
                    j.path("location").path("display_name").asString(""),
                    j.path("redirect_url").asString(""),
                    Http.plain(j.path("description").asString("")) + (pay > 0 ? "\nSalary up to $" + pay : ""),
                    Http.instant(j.path("created").asString(""))));
        }
        return jobs;
    }
}
