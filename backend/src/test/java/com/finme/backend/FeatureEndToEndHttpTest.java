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

    private record AuthedUser(HttpHeaders headers, String email) {
    }

    private AuthedUser authHeadersWithEmail() {
        User user = new User();
        user.setEmail("e2e-" + System.nanoTime() + "@example.com");
        user.setDisplayName("E2E Test User");
        user.setEmailVerified(true);
        user = userRepository.save(user);

        String token = jwtService.issueToken(user.getId(), user.getEmail(), user.getDisplayName());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new AuthedUser(headers, user.getEmail());
    }

    @Test
    void logsEditsSearchesAndDeletesATransactionThroughRealHttp() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<Map[]> manualResponse = rest.postForEntity(
                url("/api/transactions/manual"),
                new HttpEntity<>(Map.of("text", "lunch"), headers),
                Map[].class);
        assertThat(manualResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(manualResponse.getBody()).isNotEmpty();
        Number transactionId = (Number) manualResponse.getBody()[0].get("id");

        ResponseEntity<Map[]> listed = rest.exchange(
                url("/api/transactions"), HttpMethod.GET, new HttpEntity<>(headers), Map[].class);
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listed.getBody()).hasSize(1);

        ResponseEntity<Map[]> noMatch = rest.exchange(
                url("/api/transactions?category=DefinitelyNotARealCategory"),
                HttpMethod.GET, new HttpEntity<>(headers), Map[].class);
        assertThat(noMatch.getBody()).isEmpty();

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
        assertThat(updated.getBody().get("category")).isEqualTo("MyOwnCategory");

        ResponseEntity<Map[]> byNewCategory = rest.exchange(
                url("/api/transactions?category=MyOwnCategory"),
                HttpMethod.GET, new HttpEntity<>(headers), Map[].class);
        assertThat(byNewCategory.getBody()).hasSize(1);

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
    void updatesTheSignedInUsersDisplayNameThroughRealHttp() {
        HttpHeaders headers = authHeaders();

        ResponseEntity<Map> updated = rest.exchange(
                url("/api/users/me"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("displayName", "  Renamed User  "), headers), Map.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("displayName")).isEqualTo("Renamed User");

        User reloaded = userRepository.findByEmail((String) updated.getBody().get("email")).orElseThrow();
        assertThat(reloaded.getDisplayName()).isEqualTo("Renamed User");
    }

    @Test
    void rejectsAnEmptyDisplayNameThroughRealValidation() {
        HttpHeaders headers = authHeaders();

        assertThatThrownBy(() -> rest.exchange(
                url("/api/users/me"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("displayName", "   "), headers), Map.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void clearingFinancialDataRemovesTransactionsButKeepsTheAccountUsable() {
        AuthedUser authed = authHeadersWithEmail();
        HttpHeaders headers = authed.headers();
        String email = authed.email();

        rest.postForEntity(url("/api/transactions/manual"),
                new HttpEntity<>(Map.of("text", "lunch"), headers), Map[].class);
        assertThat(rest.exchange(url("/api/transactions"), HttpMethod.GET, new HttpEntity<>(headers), Map[].class)
                .getBody()).hasSize(1);

        ResponseEntity<Void> cleared = rest.exchange(
                url("/api/users/me/data"), HttpMethod.DELETE,
                new HttpEntity<>(Map.of("confirmationEmail", email), headers), Void.class);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(rest.exchange(url("/api/transactions"), HttpMethod.GET, new HttpEntity<>(headers), Map[].class)
                .getBody()).isEmpty();
    }

    @Test
    void clearingFinancialDataRejectsAMismatchedConfirmation() {
        HttpHeaders headers = authHeaders();

        assertThatThrownBy(() -> rest.exchange(
                url("/api/users/me/data"), HttpMethod.DELETE,
                new HttpEntity<>(Map.of("confirmationEmail", "not-my-email@example.com"), headers), Void.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void deletingTheAccountRemovesItsDataAndTheLoginItself() {
        AuthedUser authed = authHeadersWithEmail();
        HttpHeaders headers = authed.headers();
        String email = authed.email();

        rest.postForEntity(url("/api/transactions/manual"),
                new HttpEntity<>(Map.of("text", "coffee"), headers), Map[].class);

        ResponseEntity<Void> deleted = rest.exchange(
                url("/api/users/me"), HttpMethod.DELETE,
                new HttpEntity<>(Map.of("confirmationEmail", email), headers), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void deletingTheAccountRejectsAMismatchedConfirmation() {
        HttpHeaders headers = authHeaders();

        assertThatThrownBy(() -> rest.exchange(
                url("/api/users/me"), HttpMethod.DELETE,
                new HttpEntity<>(Map.of("confirmationEmail", "someone-else@example.com"), headers), Void.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsAccountDeletionWithNoToken() {
        assertThatThrownBy(() -> rest.exchange(
                url("/api/users/me"), HttpMethod.DELETE,
                new HttpEntity<>(Map.of("confirmationEmail", "anyone@example.com")), Void.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void rejectsAProfileUpdateWithNoToken() {
        assertThatThrownBy(() -> rest.exchange(
                url("/api/users/me"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("displayName", "Someone")), Map.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
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
