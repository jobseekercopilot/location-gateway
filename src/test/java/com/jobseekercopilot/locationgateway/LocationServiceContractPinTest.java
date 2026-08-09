package com.jobseekercopilot.locationgateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class LocationServiceContractPinTest {
    @Test
    void pinsTheExactReviewedLocationServiceContract() throws Exception {
        byte[] contract = Files.readAllBytes(Path.of("src/main/openapi/location-service.yaml"));
        String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(contract));
        String pin = Files.readString(Path.of("src/main/openapi/location-service.pin.json"));

        assertThat(checksum)
                .isEqualTo("22238272fb50d69c49abb3fda167e41897749c6fb67651c8e3b045d13817a2f5");
        assertThat(pin)
                .contains("\"contractVersion\": \"1.0.0\"")
                .contains("\"sourceRevision\": \"1ccaa0c153262ca4dda3c9bac9866bee30c6f3e7\"")
                .contains("\"sha256\": \"" + checksum + "\"")
                .contains("\"generatorVersion\": \"7.24.0\"");
    }
}
