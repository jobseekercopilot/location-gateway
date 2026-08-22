package com.jobseekercopilot.locationgateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ApiExceptionHandlerTest {

    private final MockMvc mockMvc = standaloneSetup(new RequestBodyController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void malformedJsonUsesStableBadRequestResponse() throws Exception {
        mockMvc.perform(post("/request-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.message").value("Malformed request body."));
    }

    @Test
    void unsupportedContentTypeUsesStableResponse() throws Exception {
        mockMvc.perform(post("/request-body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.statusCode").value(415))
                .andExpect(jsonPath("$.message").value("Unsupported content type."));
    }

    @Test
    void locationDomainUnsupportedCoverageRemainsRedacted() throws Exception {
        mockMvc.perform(get("/domain-coverage"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.statusCode").value(422))
                .andExpect(jsonPath("$.message")
                        .value("This postcode area is not currently supported."))
                .andExpect(content().string(not(containsString("BT11AA"))))
                .andExpect(content().string(not(containsString("private"))));
    }

    @RestController
    static class RequestBodyController {
        @PostMapping(value = "/request-body", consumes = MediaType.APPLICATION_JSON_VALUE)
        Map<String, Object> acceptJson(@RequestBody Map<String, Object> body) {
            return body;
        }

        @GetMapping("/domain-coverage")
        void domainCoverage() {
            throw HttpClientErrorException.create(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "unsupported",
                    HttpHeaders.EMPTY,
                    "private provider detail for BT11AA".getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
        }
    }
}
