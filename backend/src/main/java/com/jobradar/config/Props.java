package com.jobradar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Server settings and keys. What the user is looking for lives in the profile table, set from the UI. */
@ConfigurationProperties("jobradar")
public record Props(
        String anthropicKey,
        String anthropicUrl,
        String model,
        Duration claudeTimeout,
        String adzunaId,
        String adzunaKey,
        String adzunaCountry,
        String jsearchKey) {}
