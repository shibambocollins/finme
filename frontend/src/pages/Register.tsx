import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { apiPostJson, ApiError } from "../api/client";
import { GoogleIcon } from "../components/GoogleIcon";
import { PasswordInput } from "../components/PasswordInput";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string;

interface MessageResponse {
  message: string;
}

export function Register() {
  useDocumentTitle("Create your account — FinMe");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);
  const [resendStatus, setResendStatus] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await apiPostJson<MessageResponse>("/api/auth/register", {
        email,
        password,
        displayName: displayName.trim(),
      });
      setRegisteredEmail(email);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Registration failed");
    } finally {
      setSubmitting(false);
    }
  };

  const handleResend = async () => {
    if (!registeredEmail) return;
    setResendStatus(null);
    try {
      const response = await apiPostJson<MessageResponse>("/api/auth/resend-verification", {
        email: registeredEmail,
      });
      setResendStatus(response.message);
    } catch {
      setResendStatus("Couldn't resend right now - try again in a moment.");
    }
  };

  if (registeredEmail) {
    return (
      <div className="auth-page">
        <Link to="/" className="auth-page__back">
          &larr; Back to FinMe
        </Link>
        <div className="auth-form">
          <h1>Check your email</h1>
          <p>
            We sent a verification link to <strong>{registeredEmail}</strong>. Click it to
            activate your account before logging in.
          </p>
          <button type="button" className="oauth-button" onClick={handleResend}>
            Resend verification email
          </button>
          {resendStatus && <p className="form-error">{resendStatus}</p>}
          <p>
            <Link to="/login">Back to log in</Link>
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <Link to="/" className="auth-page__back">
        &larr; Back to FinMe
      </Link>
      <form className="auth-form" onSubmit={handleSubmit}>
        <Link to="/" className="auth-brand">
          Fin<span>Me</span>
        </Link>
        <h1>Create your account</h1>
        <label htmlFor="displayName">Your name</label>
        <input
          id="displayName"
          type="text"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          maxLength={100}
          required
        />
        <label htmlFor="email">Email</label>
        <input
          id="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <label htmlFor="password">Password</label>
        <PasswordInput
          id="password"
          value={password}
          onChange={setPassword}
          minLength={8}
          required
          autoComplete="new-password"
        />
        {error && <p className="form-error">{error}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? "Creating account..." : "Register"}
        </button>
        <a className="oauth-button" href={`${API_BASE_URL}/oauth2/authorization/google`}>
          <GoogleIcon />
          Continue with Google
        </a>
        <p>
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </div>
  );
}
