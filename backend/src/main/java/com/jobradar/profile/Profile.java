package com.jobradar.profile;

import java.util.List;

/** What the user is looking for. Empty lists mean "no limit". Saved as JSON, so a new field needs no migration. */
public record Profile(List<String> titles, List<String> locations, boolean remoteOk, boolean needsSponsorship,
                      long minPay, List<String> exclude, int maxAgeDays, String notes) {

    public static final Profile EMPTY = new Profile(null, null, true, false, 0, null, 0, null);

    public Profile {
        titles = clean(titles);
        locations = clean(locations);
        exclude = clean(exclude);
        if (maxAgeDays <= 0) maxAgeDays = 30;
        notes = notes == null ? "" : notes.strip();
    }

    private static List<String> clean(List<String> l) {
        return l == null ? List.of() : l.stream().map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }
}
