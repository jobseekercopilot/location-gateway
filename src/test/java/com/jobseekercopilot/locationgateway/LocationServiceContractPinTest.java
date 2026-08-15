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
                .isEqualTo("cd74fbf278c710a2782bbbe6473f9f708a19b6dd9329f42927ce302bb53f5f6b");
        assertThat(pin)
                .contains("\"contractVersion\": \"1.1.0\"")
                .contains("\"sourceRevision\": \"91857140c71bfda8b807c535272f918fe7741263\"")
                .contains("\"sha256\": \"" + checksum + "\"")
                .contains("\"generatorVersion\": \"7.24.0\"");
    }
}
