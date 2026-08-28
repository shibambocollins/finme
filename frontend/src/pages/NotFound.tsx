import { Link } from "react-router-dom";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

export function NotFound() {
  useDocumentTitle("Page not found — FinMe");

  return (
    <div className="auth-page">
      <div className="auth-form" style={{ textAlign: "center", alignItems: "center" }}>
        <Link to="/" className="auth-brand" style={{ justifyContent: "center" }}>
          Fin<span>Me</span>
        </Link>
        <p style={{ fontFamily: "var(--font-mono)", fontSize: 13, letterSpacing: "0.1em", color: "var(--ink-40)", marginTop: 8 }}>
          404
        </p>
        <h1 style={{ marginTop: 8 }}>Page not found</h1>
        <p style={{ color: "var(--ink-50)", marginTop: 8 }}>
          That page doesn't exist, or you don't have access to it.
        </p>
        <Link to="/" style={{ marginTop: 8, display: "inline-block" }}>
          <button type="button">Back to FinMe</button>
        </Link>
      </div>
    </div>
  );
}
