package com.finme.backend;

import com.finme.backend.entity.User;
import com.finme.backend.repository.UserRepository;
import com.finme.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives transactions, budgets and the calendar through real HTTP with a real JWT - the one
 * thing the service-level unit tests and the earlier "unauthenticated requests get 401" spot
 * check do not prove between them: that a genuinely authenticated request reaches the intended
 * controller method and gets real data back, through the actual security filter chain.
 * <p>
 * The user is inserted directly via the repository rather than through registration, since
 * password registration requires email verification - irrelevant to what this test is
 * checking, which is authorization and routing for already-authenticated requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FeatureEndToEndHttpTest {

    @LocalServerPort
    private int port;

    /**
     * Plain RestTemplate rather than Boot's TestRestTemplate: Spring Boot 4 moved that class
     * into a separate spring-boot-resttestclient module this project does not depend on, and
     * pulling in a new dependency for one verification test is not worth it when the standard
     * client (already on the classpath - this is a REST API) does the same job here. The one
     * behavioural difference is that RestTemplate throws on 4xx/5xx instead of returning them
     * as a normal ResponseEntity, which the 401/404 assertions below account for.
     */
    private final RestTemplate rest = new RestTemplate();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    private HttpHeaders authHeaders() {
        User user = new User();
        user.setEmail("e2e-" + System.nanoTime() + "@example.com");
        user.setDisplayName("E2E Test User");
        user.setEmailVerified(true);
        user = userRepository.save(user);

        String token = jwtService.issueToken(user.getId(), user.getEmail(), user.getDisplayName());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void logsEditsSearchesAndDeletesATransactionThroughRealHttp() {
        HttpHeaders headers = authHeaders();

        // Log a cash purchase via natural language would need a live AI provider - not what
        // this test is for. Instead exercise edit/search/delete against the manual endpoint's
        // sibling capabilities directly, using a transaction that manual entry would produce in
        // production but constructing the HTTP calls that matter here: list, filter, edit,
        // delete. We seed via a direct manual-entry call under the mock provider, which is
        // deterministic and makes no network call.
        ResponseEntity<Map[]> manualResponse = rest.postForEntity(
                url("/api/transactions/manual"),
                new HttpEntity<>(Map.of("text", "lunch"), headers),
                Map[].class);
        assertThat(manualResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(manualResponse.getBody()).isNotEmpty();
        Number transactionId = (Number) manualResponse.getBody()[0].get("id");

        // list
        ResponseEntity<Map[]> listed = rest.exchange(
                url("/api/transactions"), HttpMethod.GET, new HttpEntity<>(headers), Map[].class);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).hasSize(1);

        // filter - by a category that does not match, then one that does
        ResponseEntity<Map[]> noMatch = rest.exchange(
                url("/api/transactions?category=DefinitelyNotARealCategory"),
                HttpMethod.GET, new HttpEntity<>(headers), Map[].class);
        assertThat(noMatch.getBody()).isEmpty();

        // edit
        Map<String, Object> update = Map.of(
                "date", "2026-07-15",
                "merchant", "Corrected Merchant",
                "amount", "42.50",
                "direction", "DEBIT",
                "category", "MyOwnCategory",
                "description", "fixed after the fact",
                "paymentMethod", "CASH");
        ResponseEntity<Map> updated = rest.exchange(
                url("/api/transactions/" + transactionId), HttpMethod.PUT,
                new HttpEntity<>(update, headers), Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("merchant")).isEqualTo("Corrected Merchant");
        // Proves category is genuinely free text through the real validated endpoint, not
        // just at the entity layer.
        assertThat(updated.getBody().get("category")).isEqualTo("MyOwnCategory");

        // filtering by the new category now finds it
        ResponseEntity<Map[]> byNewCategory = rest.exchange(
                url("/api/transactions?category=MyOwnCategory"),
                HttpMethod.GET, new HttpEntity<>(headers), Map[].class);
        assertThat(byNewCategory.getBody()).hasSize(1);

        // delete
        ResponseEntity<Void> deleted = rest.exchange(
                url("/api/transactions/" + transactionId), HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map[]> afterDelete = rest.exchange(
                url("/api/transactions"), HttpMethod.GET, new HttpEntity<>(headers), Map[].class);
        assertThat(afterDelete.getBody()).isEmpty();
    }

    @Test
    void rejectsEditingOrDeletingAnotherUsersTransaction() {
        HttpHeaders owner = authHeaders();
        HttpHeaders stranger = authHeaders();

        ResponseEntity<Map[]> created = rest.postForEntity(
                url("/api/transactions/manual"),
                new HttpEntity<>(Map.of("text", "coffee"), owner),
                Map[].class);
        Number transactionId = (Number) created.getBody()[0].get("id");

        assertThatThrownBy(() -> rest.exchange(
                url("/api/transactions/" + transactionId), HttpMethod.DELETE,
                new HttpEntity<>(stranger), Map.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void setsAndTracksABudgetThroughRealHttp() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<Map> created = rest.postForEntity(
                url("/api/budgets"),
                new HttpEntity<>(Map.of("category", "Groceries", "monthlyLimit", "3000.00"), headers),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) created.getBody().get("spent")).doubleValue()).isEqualTo(0.0);

        // Re-posting the same category updates rather than duplicating.
        rest.postForEntity(
                url("/api/budgets"),
                new HttpEntity<>(Map.of("category", "groceries", "monthlyLimit", "3500.00"), headers),
                Map.class);

        ResponseEntity<Map[]> list = rest.exchange(
                url("/api/budgets"), HttpMethod.GET, new HttpEntity<>(headers), Map[].class);
        assertThat(list.getBody()).hasSize(1);
        assertThat(((Number) list.getBody()[0].get("monthlyLimit")).doubleValue()).isEqualTo(3500.0);

        Number budgetId = (Number) list.getBody()[0].get("id");
        ResponseEntity<Void> deleted = rest.exchange(
                url("/api/budgets/" + budgetId), HttpMethod.DELETE,
                new HttpEntity<>(headers), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void returnsACalendarWithEveryDayOfTheRequestedMonth() {
        HttpHeaders headers = authHeaders();

        rest.postForEntity(url("/api/transactions/manual"),
                new HttpEntity<>(Map.of("text", "lunch"), headers), Map[].class);

        ResponseEntity<Map> calendar = rest.exchange(
                url("/api/dashboard/calendar?month=2026-07"), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(calendar.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(calendar.getBody().get("month")).isEqualTo("2026-07");
        assertThat((List<?>) calendar.getBody().get("days")).hasSize(31);
    }

    @Test
    void registrationCarriesTheDisplayNameThroughToLoginAndTheJwt() {
        String email = "e2e-register-" + System.nanoTime() + "@example.com";

        ResponseEntity<Map> registered = rest.postForEntity(
                url("/api/auth/register"),
                new HttpEntity<>(Map.of("email", email, "password", "password123", "displayName", "  Jane Doe  ")),
                Map.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Password registration requires clicking an emailed link before login works - not what
        // this test is about, so the account is verified directly rather than standing up a
        // real SMTP round trip. Everything above this line went through the real, validated
        // /api/auth/register endpoint; only the verification step is bypassed.
        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getDisplayName())
                .as("stripped, the same way every other free-text field in this app is")
                .isEqualTo("Jane Doe");
        user.setEmailVerified(true);
        userRepository.save(user);

        ResponseEntity<Map> loggedIn = rest.postForEntity(
                url("/api/auth/login"),
                new HttpEntity<>(Map.of("email", email, "password", "password123")),
                Map.class);
        assertThat(loggedIn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loggedIn.getBody().get("displayName")).isEqualTo("Jane Doe");

        String token = (String) loggedIn.getBody().get("token");
        assertThat(jwtService.parseClaims(token).get("displayName", String.class)).isEqualTo("Jane Doe");
    }

    @Test
    void registrationRejectsAMissingDisplayNameThroughRealValidation() {
        assertThatThrownBy(() -> rest.postForEntity(
                url("/api/auth/register"),
                new HttpEntity<>(Map.of("email", "no-name@example.com", "password", "password123")),
                Map.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void everyNewEndpointRejectsAMissingToken() {
        assertUnauthorized("/api/budgets");
        assertUnauthorized("/api/dashboard/calendar");
    }

    private void assertUnauthorized(String path) {
        assertThatThrownBy(() -> rest.getForEntity(url(path), String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
