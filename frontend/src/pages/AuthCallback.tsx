import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { decodeJwtPayload } from "../auth/jwt";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

/**
 * Shared landing page for anything that ends in "backend redirects here with a token in the
 * query string" - Google OAuth login and email verification both use this exact mechanism, so
 * they share this page rather than each getting a near-duplicate one. They diverge on exactly
 * one thing: only the verification link carries "&verified=true" (see AuthController), which is
 * what tells this page to show a real confirmation instead of silently continuing straight to
 * the dashboard - a Google login didn't just verify an email, so it never shows this message.
 */
export function AuthCallback() {
  useDocumentTitle("Signing you in… — FinMe");
  const { completeOAuthLogin } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const handled = useRef(false);
  const [showVerified, setShowVerified] = useState(false);

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const token = searchParams.get("token");
    const payload = token ? decodeJwtPayload(token) : null;

    if (!token || !payload?.email) {
      navigate("/login?error=auth", { replace: true });
      return;
    }

    const displayName = typeof payload.displayName === "string" ? payload.displayName : null;
    completeOAuthLogin(token, payload.email, displayName);

    if (searchParams.get("verified") === "true") {
      setShowVerified(true);
      const timer = setTimeout(() => navigate("/dashboard", { replace: true }), 2500);
      return () => clearTimeout(timer);
    }

    navigate("/dashboard", { replace: true });
  }, [searchParams, completeOAuthLogin, navigate]);

  if (showVerified) {
    return (
      <div className="auth-page">
        <div className="auth-form" style={{ textAlign: "center", alignItems: "center" }}>
          <p className="auth-brand" style={{ justifyContent: "center" }}>
            Fin<span>Me</span>
          </p>
          <h1>Email verified!</h1>
          <p style={{ color: "var(--ink-50)" }}>
            Your account is ready. Taking you to your dashboard...
          </p>
          <Link to="/dashboard" style={{ marginTop: 8, display: "inline-block" }}>
            <button type="button">Go to Dashboard</button>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <p>Signing you in...</p>
    </div>
  );
}
