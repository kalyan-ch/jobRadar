package com.jobradar.ats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Response shapes copied from each API's docs; a live call would need keys. */
class SearchBoardTest {

    @Test
    void adzunaPutsSalaryInTextForThePayFilter() {
        var j = AdzunaBoard.parse(Http.json("""
                {"results":[{"id":"42","title":"<strong>Backend</strong> Engineer","company":{"display_name":"Acme"},
                 "location":{"display_name":"San Francisco, California"},"redirect_url":"https://adzuna/42",
                 "description":"Build APIs","salary_max":210000,"created":"2026-10-20T08:00:00Z"}]}""")).getFirst();
        assertEquals("adz:42", j.id());
        assertEquals("Backend Engineer", j.title());
        assertEquals("Acme", j.company());
        assertTrue(j.descriptionText().contains("$210000"));
        assertEquals("2026-10-20T08:00:00Z", j.postedAt().toString());
    }

    @Test
    void jsearchMarksRemoteAndPicksDateWindow() {
        var j = JSearchBoard.parse(Http.json("""
                {"data":[{"job_id":"x1","employer_name":"Dice Co","job_title":"Software Engineer","job_city":"Austin",
                 "job_state":"TX","job_country":"US","job_is_remote":true,"job_apply_link":"https://dice/x1",
                 "job_description":"Go","job_posted_at_datetime_utc":"2026-10-25T00:00:00.000Z"}]}""")).getFirst();
        assertEquals("Austin, TX, US (Remote)", j.location());
        assertEquals("week", JSearchBoard.window(7));
        assertEquals("month", JSearchBoard.window(30));
    }

    @Test
    void remotiveDateHasNoOffset() {
        var j = RemotiveBoard.parse(Http.json("""
                {"jobs":[{"id":7,"title":"SRE","company_name":"R","candidate_required_location":"USA",
                 "url":"https://remotive/7","description":"<p>Ops</p>","salary":"$150k","publication_date":"2026-10-24T10:00:00"}]}""")).getFirst();
        assertEquals("Remote (USA)", j.location());
        assertEquals("2026-10-24T10:00:00Z", j.postedAt().toString());
    }
}
