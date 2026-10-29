package com.jobradar.ats;

import com.jobradar.domain.Job;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Remotive — free, no key, remote jobs only. Searched once per title (location is ignored). */
@Component
public class RemotiveBoard implements SearchBoard {
    private final Http http;

    RemotiveBoard(Http http) { this.http = http; }

    @Override public String name() { return "remotive"; }

    @Override public boolean enabled() { return true; }

    @Override
    public List<Job> search(String title, String location, int maxAgeDays) {
        return parse(http.get("https://remotive.com/api/remote-jobs?limit=100&search=" + title));
    }

    static List<Job> parse(JsonNode res) {
        var jobs = new ArrayList<Job>();
        for (var j : res.path("jobs")) {
            var where = j.path("candidate_required_location").asString("");
            jobs.add(new Job(
                    "rmt:" + j.path("id").asString(),
                    j.path("company_name").asString(""),
                    j.path("title").asString(""),
                    where.isBlank() ? "Remote" : "Remote (" + where + ")",
                    j.path("url").asString(""),
                    Http.plain(j.path("description").asString("")) + "\n" + j.path("salary").asString(""),
                    Http.instant(j.path("publication_date").asString("") + "Z")));   // UTC, sent without offset
        }
        return jobs;
    }
}
