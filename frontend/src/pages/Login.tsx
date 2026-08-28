import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../api/client";
import { GoogleIcon } from "../components/GoogleIcon";
import { PasswordInput } from "../components/PasswordInput";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string;

const ERROR_MESSAGES: Record<string, string> = {
  oauth2: "Google sign-in failed - please try again",
  verification: "That verification link is invalid or has expired - request a new one from the register page",
  auth: "Sign-in link was invalid or incomplete - please try again",
};

export function Login() {
  useDocumentTitle("Log in — FinMe");
  const { login } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(
    ERROR_MESSAGES[searchParams.get("error") ?? ""] ?? null,
  );
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Login failed");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <Link to="/" className="auth-page__back">
        &larr; Back to FinMe
      </Link>
      <form className="auth-form" onSubmit={handleSubmit}>
        <Link to="/" className="auth-brand">
          Fin<span>Me</span>
        </Link>
        <h1>Log in</h1>
        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <label htmlFor="password">Password</label>
        <PasswordInput id="password" value={password} onChange={setPassword} required />
        {error && <p className="form-error">{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? "Logging in..." : "Log in"}
        </button>
        <a className="oauth-button" href={`${API_BASE_URL}/oauth2/authorization/google`}>
          <GoogleIcon />
          Continue with Google
        </a>
        <p>
          Don't have an account? <Link to="/register">Register</Link>
        </p>
      </form>
    </div>
  );
}
