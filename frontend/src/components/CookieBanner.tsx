import { useEffect, useState } from "react";

const STORAGE_KEY = "finme.cookie-notice-dismissed";

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
      // ignore
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
