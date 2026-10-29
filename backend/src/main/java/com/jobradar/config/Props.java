package com.jobradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Server settings and keys. What the user is looking for lives in the profile table, set from the UI. */
@ConfigurationProperties("jobradar")
public record Props(
        String llmUrl,
        String llmKey,
        String model,
        String adzunaId,
        String adzunaKey,
        String adzunaCountry,
        String jsearchKey) {}
