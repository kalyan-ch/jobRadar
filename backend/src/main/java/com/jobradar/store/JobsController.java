package com.jobradar.store;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only API for the ui/ Next app. */
@RestController
class JobsController {
    private final JobStore store;

    JobsController(JobStore store) { this.store = store; }

    @GetMapping("/api/jobs/latest")
    List<JobStore.Top> latest(@RequestParam(required = false) String company) { return store.latest(100, company); }

    @GetMapping("/api/jobs/top")
    List<JobStore.Top> top(@RequestParam(required = false) String company) { return store.top(100, company); }

    @GetMapping("/api/companies")
    List<JobStore.Company> companies() { return store.companies(); }
}
