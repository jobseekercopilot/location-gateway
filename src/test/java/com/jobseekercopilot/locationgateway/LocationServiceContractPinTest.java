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
                .isEqualTo("0cd7a877836dfbf1a42b5f71e0a807ec8dc99f88d69a695d7c5734e320cdef27");
        assertThat(new String(contract, java.nio.charset.StandardCharsets.UTF_8))
                .contains("version: 1.2.0")
                .contains("enum: [GOOGLE_MAPS, POSTCODES_IO]");
        assertThat(pin)
                .contains("\"contractVersion\": \"1.2.0\"")
                .contains("\"sourceRevision\": \"3819dc5491ecf8ee95fe86d646311588c5e24d55\"")
                .contains("\"sha256\": \"" + checksum + "\"")
                .contains("\"generatorVersion\": \"7.24.0\"");
    }
}
