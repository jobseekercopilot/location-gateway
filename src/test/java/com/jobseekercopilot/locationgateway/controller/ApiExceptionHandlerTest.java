package com.jobseekercopilot.locationgateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @RestController
    static class RequestBodyController {
        @PostMapping(value = "/request-body", consumes = MediaType.APPLICATION_JSON_VALUE)
        Map<String, Object> acceptJson(@RequestBody Map<String, Object> body) {
            return body;
        }
    }
}
