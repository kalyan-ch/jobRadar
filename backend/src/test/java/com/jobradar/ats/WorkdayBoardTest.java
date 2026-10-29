package com.jobradar.ats;

import org.junit.jupiter.api.Test;

import static com.jobradar.Fixtures.prefilter;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkdayBoardTest {

    @Test
    void rejectsSlugThatIsntTenantWdSiteSearch() {
        var board = new WorkdayBoard(new Http(), prefilter());
        assertThrows(IllegalArgumentException.class, () -> board.fetch("nvidia"));
        assertThrows(IllegalArgumentException.class, () -> board.fetch("nvidia/wd5/Site"));
    }
}
