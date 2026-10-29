package com.jobradar.ats;

import com.jobradar.domain.Job;

import java.util.List;

/** A job search across companies (aggregators), driven by the profile's titles and locations, not companies.yml. */
public interface SearchBoard {
    String name();

    /** False until its API key is set, so an install without keys just skips it. */
    boolean enabled();

    /** location "" = anywhere. */
    List<Job> search(String title, String location, int maxAgeDays);
}
