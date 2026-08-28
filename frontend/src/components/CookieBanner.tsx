import { useEffect, useState } from "react";

const STORAGE_KEY = "finme.cookie-notice-dismissed";

/**
 * A notice, not an accept/reject consent flow - FinMe doesn't set any tracking or analytics
 * cookie today. The only cookie in the app is the strictly necessary, short-lived one Google's
 * OAuth login needs mid-handshake (see SecurityConfig). Framed honestly rather than presenting
 * a choice that doesn't actually exist yet; revisit this if analytics is ever added.
 */
export function CookieBanner() {
  const [dismissed, setDismissed] = useState(true);

  useEffect(() => {
    try {
      setDismissed(localStorage.getItem(STORAGE_KEY) === "1");
    } catch {
      setDismissed(true);
    }
  }, []);

  const dismiss = () => {
    setDismissed(true);
    try {
      localStorage.setItem(STORAGE_KEY, "1");
    } catch {
      // Private browsing or storage disabled - the banner just reappears next visit, an
      // acceptable fallback rather than something worth failing loudly over.
    }
  };

  if (dismissed) return null;

  return (
    <div className="cookie-banner" role="dialog" aria-label="Cookie notice">
      <p>
        FinMe only sets a strictly necessary cookie during Google sign-in - no tracking or
        analytics cookies. There's nothing to opt into.
      </p>
      <button type="button" onClick={dismiss}>
        Got it
      </button>
    </div>
  );
}
