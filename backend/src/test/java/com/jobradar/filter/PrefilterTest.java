package com.jobradar.filter;

import com.jobradar.domain.Job;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.jobradar.profile.Profile;

import static com.jobradar.Fixtures.PROFILE;
import static com.jobradar.Fixtures.prefilter;
import static com.jobradar.Fixtures.job;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefilterTest {

    private final Prefilter f = prefilter();

    @Test
    void keepsBayAreaEngineerAbovePayFloor() {
        assertTrue(f.keep(job("Senior Backend Engineer", "San Francisco, CA", "$200,000 - $260,000")));
    }

    @Test
    void rejectsByTitleLocationVisaAndPay() {
        assertFalse(f.keep(job("Engineering Manager", "San Francisco", "$300,000")), "denied title");
        assertFalse(f.keep(job("Software Engineer Intern", "San Francisco", "")), "denied word");
        assertTrue(f.keep(job("Software Engineer, Internal Tools", "San Francisco", "")), "whole-word deny");
        assertFalse(f.keep(job("Senior Staff Software Engineer", "San Francisco", "")), "level above");
        assertTrue(f.keep(job("Staff Software Engineer", "San Francisco", "")), "phrase, not single words");
        assertFalse(f.keep(job("Senior Software Engineer, Android", "San Francisco", "")), "excluded word");
        assertTrue(f.keep(job("Senior Engineer, Backend", "San Francisco", "")), "title words in any order");
        assertFalse(f.keep(job("Product Designer", "San Francisco", "")), "no wanted title");
        assertTrue(f.keep(job("Backend Engineer", "Remote - US", "")), "remote ok");
        assertFalse(f.keep(job("Backend Engineer", "Austin, TX", "$300,000")), "wrong metro");
        assertFalse(f.keep(job("Backend Engineer", "San Francisco", "security clearance required")), "visa");
        assertFalse(f.keep(job("Backend Engineer", "San Francisco", "$120,000 - $150,000")), "below floor");
        assertFalse(f.keep(job("Backend Engineer", "Remote (non-US)", "Remote (non-US)")), "reject regex");
    }

    @Test
    void dropsJobsPostedBeforeMaxAge() {
        var old = new Job("id", "co", "Backend Engineer", "San Francisco", "u", "", Instant.now().minus(Duration.ofDays(31)));
        var fresh = new Job("id", "co", "Backend Engineer", "San Francisco", "u", "", Instant.now().minus(Duration.ofDays(29)));
        assertFalse(f.keep(old));
        assertTrue(f.keep(fresh));
        assertFalse(f.keep(job("Backend Engineer", "Austin, TX", "based in San Francisco")), "location field only");
    }

    @Test
    void emptyProfileListsMeanNoLimit() {
        var any = new Prefilter(null).use(new Profile(List.of("Nurse"), null, false, false, 0, null, 0, null));
        assertTrue(any.keep(job("Registered Nurse", "Austin, TX", "security clearance $50,000")), "no location/pay/visa rules");
        assertFalse(any.keep(job("Backend Engineer", "Austin, TX", "")));
        assertTrue(f.keep(job("Backend Engineer", "SF", "no sponsorship")) == !PROFILE.needsSponsorship());
    }

    @Test
    void passesWhenNoPayDisclosed() {
        assertTrue(f.payOk("no numbers here"));
        assertTrue(f.payOk("$150,000 base plus $220,000 total"), "keeps on the max of the range");
    }

    @Test
    void listingOkLetsMultiLocationThroughForTheDetailCall() {
        assertTrue(f.listingOk("Backend Engineer", "2 Locations"));
        assertTrue(f.listingOk("Backend Engineer", "San Francisco, CA"));
        assertFalse(f.listingOk("Backend Engineer", "Israel, Yokneam"), "location");
        assertFalse(f.listingOk("Engineering Manager", "San Francisco, CA"), "title");
    }
}
