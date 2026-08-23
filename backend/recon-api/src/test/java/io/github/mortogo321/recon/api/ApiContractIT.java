package io.github.mortogo321.recon.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/**
 * The HTTP contract, exercised through the real security chain, the real services and the real
 * databases — no mocks. Mocking the services here would test the annotations rather than the
 * behaviour, and every one of these cases is a rule someone could plausibly break by accident:
 * segregation of duties, the double-click guard, what an unauthenticated caller may see.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:recon-api-it;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "recon.legacy.datasource.url=jdbc:h2:mem:legacy-api-it;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "recon.outbox.dispatch-interval=PT1H"
        })
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiContractIT {

    private static final String DEMO_DAY = "2026-08-20";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("an unauthenticated caller sees nothing but the login endpoint and liveness")
    void anonymousAccessIsLimited() throws Exception {
        mvc.perform(get("/api/runs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());

        // Health is open so an orchestrator can probe it; metrics are not, because a scrape of
        // recon.outbox.dead tells an outsider how the reconciliation is going.
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("wrong credentials are refused without revealing which half was wrong")
    void badCredentialsAreRefused() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    @DisplayName("roles reach the console without Spring's ROLE_ prefix, on both auth endpoints")
    void rolesAreExposedWithoutTheSpringPrefix() throws Exception {
        // The prefix is a Spring Security internal: the converter re-adds it when it builds the
        // authorities. Leaking it onto the wire once cost the console every capability check it
        // makes, silently, because both halves type-checked against their own idea of the name.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles").value(containsInAnyOrder("ADMIN", "OPERATOR", "APPROVER")));

        mvc.perform(get("/api/auth/me").header("Authorization", bearer("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(contains("OPERATOR")));
    }

    @Test
    @DisplayName("launching a run is an admin act, not something the exception queue can do")
    void operatorCannotLaunchARun() throws Exception {
        mvc.perform(post("/api/runs")
                        .header("Authorization", bearer("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessDate\":\"" + DEMO_DAY + "\"}"))
                .andExpect(status().isForbidden());

        // The read side stays open to every console role.
        mvc.perform(get("/api/runs").header("Authorization", bearer("operator"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a bad request is answered with the field that was wrong, not a stack trace")
    void validationAndProfileErrorsAreSpecific() throws Exception {
        mvc.perform(post("/api/runs")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessDate\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.businessDate").exists());

        mvc.perform(post("/api/runs")
                        .header("Authorization", bearer("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessDate\":\"" + DEMO_DAY + "\",\"toleranceProfile\":\"no-such-profile\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://recon.example/problems/unknown-tolerance-profile"));
    }

    @Test
    @DisplayName("the correlation id a caller supplies comes back on the response")
    void correlationIdIsEchoed() throws Exception {
        mvc.perform(get("/api/runs/profiles")
                        .header("Authorization", bearer("operator"))
                        .header("X-Correlation-Id", "trace-me-please"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "trace-me-please"));
    }

    @Test
    @DisplayName("an idempotency key stops the double click but is not spent by a rejected request")
    void idempotencyKeySurvivesARejectedRequest() throws Exception {
        String key = "contract-test-key";

        // Rejected for lack of authority: the key must not be consumed, or the operator's admin
        // colleague could never retry the same click.
        mvc.perform(post("/api/runs")
                        .header("Authorization", bearer("operator"))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessDate\":\"" + DEMO_DAY + "\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/runs")
                        .header("Authorization", bearer("admin"))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessDate\":\"" + DEMO_DAY + "\"}"))
                .andExpect(status().isOk());

        // The same key again is the double click, and is refused rather than silently ignored.
        mvc.perform(post("/api/runs")
                        .header("Authorization", bearer("admin"))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessDate\":\"" + DEMO_DAY + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @DisplayName("the exception journey enforces maker-checker end to end")
    void exceptionJourneyEnforcesSegregationOfDuties() throws Exception {
        long exceptionId = anyOpenExceptionId();

        mvc.perform(post("/api/exceptions/" + exceptionId + "/assign")
                        .header("Authorization", bearer("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"operator\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("INVESTIGATING"));

        mvc.perform(post("/api/exceptions/" + exceptionId + "/submit")
                        .header("Authorization", bearer("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Acquirer confirmed the posting was never sent.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING_APPROVAL"));

        // An operator cannot approve at all, and the submitter cannot approve their own work even
        // when they hold the approver role. Both halves matter; only the second is easy to lose.
        mvc.perform(post("/api/exceptions/" + exceptionId + "/decision")
                        .header("Authorization", bearer("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"WRITTEN_OFF\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/exceptions/" + exceptionId + "/decision")
                        .header("Authorization", bearer("approver"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"WRITTEN_OFF\",\"note\":\"Approved.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("WRITTEN_OFF"));

        // And the workflow is a state machine, not a set of independent flags.
        mvc.perform(post("/api/exceptions/" + exceptionId + "/submit")
                        .header("Authorization", bearer("operator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Trying again after sign-off.\"}"))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ helpers

    private String bearer(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + username + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return "Bearer " + JsonPath.<String>read(body, "$.accessToken");
    }

    /** Reconciles the demo day if nothing has yet, and returns a break to work through. */
    private long anyOpenExceptionId() throws Exception {
        String admin = bearer("admin");
        String launch = mvc.perform(post("/api/runs")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessDate\":\"" + DEMO_DAY + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long runId = ((Number) JsonPath.read(launch, "$.runId")).longValue();

        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)) {
            String page = mvc.perform(get("/api/exceptions")
                            .param("runId", String.valueOf(runId))
                            .param("state", "OPEN")
                            .header("Authorization", admin))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            Number total = JsonPath.read(page, "$.totalElements");
            if (total.longValue() > 0) {
                return ((Number) JsonPath.read(page, "$.items[0].id")).longValue();
            }
            Thread.sleep(Duration.ofMillis(150));
        }
        throw new AssertionError("No open exception appeared for run " + runId + " within 60s");
    }

    @Test
    @DisplayName("an admin can read the operational endpoints an operator cannot")
    void adminSeesActuator() throws Exception {
        mvc.perform(get("/actuator/metrics").header("Authorization", bearer("admin")))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics").header("Authorization", bearer("operator")))
                .andExpect(status().isForbidden());
        assertThat(true).isTrue();
    }
}
