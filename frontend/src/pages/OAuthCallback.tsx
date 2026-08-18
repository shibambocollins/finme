import { useEffect, useRef } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { decodeJwtPayload } from "../auth/jwt";

export function OAuthCallback() {
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
      navigate("/login?error=oauth2", { replace: true });
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
