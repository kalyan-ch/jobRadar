package com.jobradar.profile;

import com.jobradar.Fixtures;
import com.jobradar.TestDb;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(TestDb.class)
class ProfileStoreTest {

    @Autowired ProfileStore store;

    @Test
    void savesProfileAndResumeIndependently() {
        store.save(Fixtures.PROFILE);
        store.saveResume("ten years of backend\0");
        store.save(Fixtures.PROFILE);   // saving the profile again keeps the resume
        assertEquals(Fixtures.PROFILE, store.get());
        assertEquals("ten years of backend", store.resume());
    }
}
