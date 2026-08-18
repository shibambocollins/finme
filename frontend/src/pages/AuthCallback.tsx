import { useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { decodeJwtPayload } from "../auth/jwt";

/**
 * Shared landing page for anything that ends in "backend redirects here with a token in the
 * query string" - Google OAuth login and email verification both use this exact mechanism, so
 * they share this page rather than each getting a near-duplicate one.
 */
export function AuthCallback() {
  const { completeOAuthLogin } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const token = searchParams.get("token");
    const payload = token ? decodeJwtPayload(token) : null;

    if (!token || !payload?.email) {
      navigate("/login?error=auth", { replace: true });
      return;
    }

    completeOAuthLogin(token, payload.email);
    navigate("/dashboard", { replace: true });
  }, [searchParams, completeOAuthLogin, navigate]);

  return (
    <div className="auth-page">
      <p>Signing you in...</p>
    </div>
  );
}
