# Authentication

Stateless JWT. Two ways to obtain a token; one scheme once you have it.

## Using a token

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Every `/api/**` endpoint requires it except `/api/auth/**`. Missing or invalid gives `401`
with a JSON body, never a redirect to a login page — this is an API, and a 302 to HTML would
be useless to a client.

Default lifetime is 24 hours (`JWT_EXPIRATION_MS`). There is no refresh token: for a
single-user personal app, re-authenticating daily is an acceptable trade for not maintaining
refresh-token rotation and revocation.

## Password login

```mermaid
sequenceDiagram
    participant C as Client
    participant A as API
    participant D as Database

    C->>A: POST /api/auth/register
    A->>D: save user (email_verified = false)
    A->>C: verification email sent

    Note over C: user clicks the emailed link
    C->>A: GET /api/auth/verify-email?token=...
    A->>D: email_verified = true
    A-->>C: redirect to frontend login

    C->>A: POST /api/auth/login
    A->>A: verify hash, require verified email
    A-->>C: { token, email, displayName }
```

Registration alone is not enough to log in — an unverified email gets `403` with an
explanation, not a generic failure.

## Google OAuth

```mermaid
sequenceDiagram
    participant B as Browser
    participant A as Backend
    participant G as Google
    participant F as Frontend

    B->>A: GET /oauth2/authorization/google
    A-->>B: 302 to Google
    B->>G: sign in and consent
    G->>A: GET /login/oauth2/code/google?code=...
    A->>G: exchange code for profile
    A->>A: find or create user, mark verified
    A->>A: issue the same JWT as password login
    A-->>B: 302 to frontend /auth-callback?token=...
    B->>F: token in the query string
```

Three things here catch people out:

**The redirect URI points at the backend.** `https://finme-backend.azurewebsites.net/login/oauth2/code/google`,
not the frontend. Spring Security handles the exchange and then redirects onward. Pointing it
at the frontend gives `redirect_uri_mismatch` — the most common misconfiguration in this
setup.

**The token arrives in a query string,** because this is the tail of a full-page redirect
chain, not a fetch the client made. `/auth-callback` reads it and hands it to the auth context.

**The same JWT is issued either way.** Downstream, nothing knows how a user signed in.

Scopes are Spring's Google defaults — `openid`, `profile`, `email`. All non-sensitive, so no
Google verification review is required.

### Behind a TLS-terminating proxy

Azure App Service terminates TLS at its edge and forwards over HTTP with `X-Forwarded-*`
headers. Without this setting:

```properties
server.forward-headers-strategy=framework
```

Spring builds the `redirect_uri` from the internal `http://host:8080` request rather than the
public HTTPS host, and Google rejects it. This is invisible locally and breaks immediately in
production.

## Email verification

Tokens are single-use with a 24-hour expiry (`EMAIL_VERIFICATION_TOKEN_EXPIRY_HOURS`).
`/api/auth/verify-email` redirects to the frontend rather than returning JSON, since it is
opened directly from an email client.

OAuth users are marked verified on first sign-in — Google has already verified the address.

## Resolving the current user

```java
Long userId = authenticatedUser.currentUserId();
```

Resolved from the validated token. **No endpoint accepts a user id as a parameter** — one that
did would let any authenticated caller read anyone's data.

Every user-scoped query filters on this id at the repository call, and a resource belonging to
someone else returns `404`, not `403`.

## Token contents

```json
{
  "sub": "42",
  "email": "user@example.com",
  "displayName": "Collins",
  "iat": 1757923200,
  "exp": 1758009600
}
```

`displayName` rides along so the client can render a greeting without an extra request. The
client decodes the payload for display only — the signature is verified server-side on every
request, and a client-side decode is never trusted for authorisation.
