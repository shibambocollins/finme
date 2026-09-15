import { useEffect, useRef, useState } from "react";

/**
 * A short greeting on arrival at the dashboard.
 *
 * Landing straight on a data screen after signing in reads as abrupt - there is no moment
 * that acknowledges the sign-in worked, which is most noticeable on a first visit when the
 * dashboard is also empty.
 *
 * New-vs-returning is decided by whether this browser has recorded this account before,
 * rather than by anything the API returns. That is deliberate: it needs no backend change,
 * and "have I seen you here before" is what the greeting is actually claiming. The cost is
 * that a first sign-in on a second device greets you as new again, which is a fair reading
 * of the same question.
 */
const SEEN_KEY = "finme.seenAccounts";

function hasSeenBefore(email: string): boolean {
  try {
    const raw = localStorage.getItem(SEEN_KEY);
    const seen = raw ? (JSON.parse(raw) as string[]) : [];
    return Array.isArray(seen) && seen.includes(email);
  } catch {
    // A corrupt or unavailable store should not break the dashboard - treat it as a first
    // visit, which is the harmless side of the guess.
    return false;
  }
}

function rememberAccount(email: string) {
  try {
    const raw = localStorage.getItem(SEEN_KEY);
    const seen = raw ? (JSON.parse(raw) as string[]) : [];
    const list = Array.isArray(seen) ? seen : [];
    if (!list.includes(email)) {
      localStorage.setItem(SEEN_KEY, JSON.stringify([...list, email]));
    }
  } catch {
    // Non-fatal: the greeting is cosmetic, so a storage failure just means it may repeat.
  }
}

interface WelcomeBannerProps {
  email: string | null;
  displayName: string | null;
  /** Drives the follow-up line - a returning user with data does not need onboarding copy. */
  hasTransactions: boolean;
}

export function WelcomeBanner({ email, displayName, hasTransactions }: WelcomeBannerProps) {
  const [state, setState] = useState<{ returning: boolean } | null>(null);
  const [dismissed, setDismissed] = useState(false);
  /**
   * The effect both reads and writes the same key, which makes it non-idempotent: under
   * StrictMode's deliberate double-invoke, the first pass records the account and the
   * second then reads it back as already seen, greeting every new user with "welcome
   * back". Guarding per-account keeps one decision per mount.
   */
  const decidedFor = useRef<string | null>(null);

  useEffect(() => {
    if (!email || decidedFor.current === email) return;
    decidedFor.current = email;
    // Read before writing: the write is what makes the next visit "returning".
    setState({ returning: hasSeenBefore(email) });
    rememberAccount(email);
  }, [email]);

  if (!state || dismissed) return null;

  const firstName = displayName?.trim().split(/\s+/)[0] ?? null;

  return (
    <div className="welcome-banner" role="status">
      <div className="welcome-banner__text">
        <strong>
          {state.returning
            ? `Welcome back${firstName ? `, ${firstName}` : ""}.`
            : `Welcome to FinMe${firstName ? `, ${firstName}` : ""}.`}
        </strong>
        <span>
          {state.returning
            ? hasTransactions
              ? "Here's where your money stands."
              : "Upload a statement whenever you're ready."
            : "Start with a bank statement - it fills a whole month in one go."}
        </span>
      </div>
      <button
        type="button"
        className="welcome-banner__close"
        onClick={() => setDismissed(true)}
        aria-label="Dismiss welcome message"
      >
        &times;
      </button>
    </div>
  );
}
