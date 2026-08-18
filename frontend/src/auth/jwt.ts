interface JwtPayload {
  sub?: string;
  email?: string;
  exp?: number;
  [key: string]: unknown;
}

/**
 * Reads the claims out of a JWT's payload segment. JWTs are signed, not encrypted, so
 * reading them client-side (without verifying the signature) is normal and safe here - we're
 * just recovering the email claim the backend already embedded, not trusting this as proof of
 * anything the backend hasn't already vouched for by issuing the token in the first place.
 */
export function decodeJwtPayload(token: string): JwtPayload | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;

  try {
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
    const json = decodeURIComponent(
      atob(padded)
        .split("")
        .map((c) => "%" + c.charCodeAt(0).toString(16).padStart(2, "0"))
        .join(""),
    );
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}
