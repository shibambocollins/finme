# Errors

## Response shape

Every error returns the same body, whatever produced it:

```json
{
  "status": 404,
  "message": "Receipt 12 was not found",
  "timestamp": "2026-09-15T09:42:11.482Z"
}
```

`message` is written to be shown to a user. Clients surface it directly rather than
substituting a generic string — the server knows why something failed, and replacing that with
"Something went wrong" throws away the only useful information.

## How it works

Exceptions carry their own status:

```java
public class ReceiptNotFoundException extends ApiException {
    public ReceiptNotFoundException(Long receiptId) {
        super(HttpStatus.NOT_FOUND, "Receipt " + receiptId + " was not found");
    }
}
```

`GlobalExceptionHandler` maps them to the shape above. Handling is centralised so no
controller invents its own error format.

## Common errors

### 400 — Validation

Bean-validation failures on a request body:

```json
{ "status": 400, "message": "email: must be a well-formed email address", "timestamp": "..." }
```

### 400 — Bad upload

| Message | Cause |
| --- | --- |
| `Uploaded file is empty` | Zero-byte file |
| `Only JPEG or PNG images are supported` | Wrong content type — HEIC included |
| `That file is not a JPEG or PNG image...` | Content type claimed one thing, file signature said another |

The last one is a signature check, not a trust of the declared type. A renamed `.txt` fails
here rather than confusing a vision model.

### 401 — Unauthenticated

Missing, malformed or expired token. Always JSON, never a redirect.

### 403 — Email not verified

```json
{ "status": 403, "message": "Verify your email address before signing in", "timestamp": "..." }
```

Distinct from bad credentials on purpose — "wrong password" would send the user looking for
the wrong problem.

### 404 — Not found, or not yours

Another user's resource returns `404`, not `403`. A `403` would confirm the resource exists,
which is itself a leak.

### 413 — Upload too large

The limit is 10 MB.

## Asynchronous failures

The important case, and the one clients get wrong.

Statement and receipt extraction runs **after** the upload response. By the time anything can
fail, the HTTP response is long gone — so failures are recorded on the row rather than thrown:

```json
{
  "id": 12,
  "status": "FAILED",
  "failureReason": "No readable text in that PDF. It looks like a scan or an image - download the PDF version from your banking app rather than scanning a printout."
}
```

A `202` means *accepted*, not *succeeded*. A client that stops at the upload response will
silently show nothing when extraction fails. Poll, check `status`, and show `failureReason`.

### Common failure reasons

| Reason | What happened | What the user should do |
| --- | --- | --- |
| `No readable text in that PDF...` | A scan or photo saved as PDF — no text layer | Download the real PDF from the banking app |
| `No purchase found in that image...` | The photo is not a receipt, or is unreadable | Retake with the whole slip visible and in focus |
| `Could not read that receipt right now...` | Every AI provider failed | Retry in a few minutes |

The first two are explicit rather than silent successes. A statement with no extractable text
would otherwise produce zero transactions and report `COMPLETE` — the app claiming success for
work it never did.

## Client handling

```typescript
try {
  await apiPostJson("/api/budgets", body, token);
} catch (err) {
  setError(err instanceof ApiError ? err.message : "Could not save that budget");
}
```

The fallback string is for genuine non-API failures — the network dropped, the response was
not JSON. When the server did answer, its message is the one worth showing.
