package com.jobradar;

import com.jobradar.domain.Job;
import com.jobradar.filter.Prefilter;
import com.jobradar.profile.Profile;

import java.util.List;

/** Shared test data so each layer's test stays about that layer. */
public final class Fixtures {

    public static final Profile PROFILE = new Profile(
            List.of("Backend Engineer", "Software Engineer"), List.of("San Francisco", "Bay Area"), true, true,
            180_000, List.of("intern", "manager", "senior staff", "android"), 30, "");

    /** A Prefilter on PROFILE with no DB behind it. */
    public static Prefilter prefilter() { return new Prefilter(null).use(PROFILE); }

    public static Job job(String title, String location, String description) {
        return new Job("id", "co", title, location, "https://example.com/apply", description, null);
    }

    private Fixtures() {}
}
