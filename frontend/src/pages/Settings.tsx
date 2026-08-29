import { useState, type FormEvent, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { apiDeleteJson, apiPut, ApiError } from "../api/client";
import { AppHeader } from "../components/AppHeader";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

interface ProfileResponse {
  email: string;
  displayName: string;
}

/**
 * Shared shape for a destructive, confirmation-gated account action (clear data / delete
 * account) - collapsed by default, and only reveals the real "do it" button once the caller has
 * typed their own email back. The backend enforces the same match independently (UserService),
 * so this is a real UX gate, not the only thing standing between a stray click and data loss.
 */
function DangerAction({
  title,
  description,
  actionLabel,
  busyLabel,
  confirmEmail,
  onConfirm,
}: {
  title: string;
  description: ReactNode;
  actionLabel: string;
  busyLabel: string;
  confirmEmail: string;
  onConfirm: (typedEmail: string) => Promise<void>;
}) {
  const [armed, setArmed] = useState(false);
  const [typedEmail, setTypedEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const cancel = () => {
    setArmed(false);
    setTypedEmail("");
    setError(null);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onConfirm(typedEmail.trim());
      cancel();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ paddingTop: 16, marginTop: armed ? 0 : 16, borderTop: "1px solid var(--clay-border)" }}>
      <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 16, flexWrap: "wrap" }}>
        <div style={{ maxWidth: "44ch" }}>
          <p style={{ margin: "0 0 4px", fontWeight: 700 }}>{title}</p>
          <p className="recommendation-empty">{description}</p>
        </div>
        {!armed && (
          <button type="button" className="btn-delete" onClick={() => setArmed(true)}>
            {actionLabel}
          </button>
        )}
      </div>

      {armed && (
        <form onSubmit={submit} style={{ marginTop: 14 }}>
          <label style={{ display: "block", fontSize: 13.5, fontWeight: 600, marginBottom: 6 }}>
            Type <strong>{confirmEmail}</strong> to confirm
          </label>
          <input
            type="email"
            value={typedEmail}
            onChange={(e) => setTypedEmail(e.target.value)}
            placeholder={confirmEmail}
            required
            disabled={busy}
            style={{ width: "100%", maxWidth: 340 }}
          />
          {error && <p className="form-error">{error}</p>}
          <div style={{ display: "flex", gap: 10, marginTop: 12 }}>
            <button type="submit" className="btn-delete" disabled={busy || !typedEmail.trim()}>
              {busy ? busyLabel : actionLabel}
            </button>
            <button type="button" className="btn-quiet" onClick={cancel} disabled={busy}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

export function Settings() {
  useDocumentTitle("Settings | FinMe");
  const { token, email, displayName, updateDisplayName, logout } = useAuth();
  const navigate = useNavigate();
  const [nameInput, setNameInput] = useState(displayName ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [cleared, setCleared] = useState(false);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSaved(false);
    setSaving(true);
    try {
      const profile = await apiPut<ProfileResponse>("/api/users/me", { displayName: nameInput.trim() }, token);
      updateDisplayName(profile.displayName);
      setNameInput(profile.displayName);
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save your name");
    } finally {
      setSaving(false);
    }
  };

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const clearFinancialData = async (typedEmail: string) => {
    await apiDeleteJson("/api/users/me/data", { confirmationEmail: typedEmail }, token);
    setCleared(true);
  };

  const deleteAccount = async (typedEmail: string) => {
    await apiDeleteJson("/api/users/me", { confirmationEmail: typedEmail }, token);
    // Not the same as instant server-side revocation - see UserService.deleteAccount. The
    // account and its data are already gone at this point; this just ends the local session.
    logout();
    navigate("/login");
  };

  return (
    <>
      <AppHeader active="settings" />
      <main className="page page--narrow" id="main-content">
        <h1>Settings</h1>

        <section className="chart-card" style={{ marginBottom: 20 }}>
          <h2>Profile</h2>

          <label htmlFor="settings-email" style={{ display: "block", marginTop: 4 }}>
            Email
          </label>
          <input
            id="settings-email"
            type="email"
            value={email ?? ""}
            disabled
            className="field-readonly"
            style={{ width: "100%" }}
          />

          <form onSubmit={handleSubmit} style={{ marginTop: 18 }}>
            <label htmlFor="settings-name" style={{ display: "block" }}>
              Display name
            </label>
            <input
              id="settings-name"
              type="text"
              value={nameInput}
              onChange={(e) => {
                setNameInput(e.target.value);
                setSaved(false);
              }}
              maxLength={100}
              required
              style={{ width: "100%" }}
              disabled={saving}
            />
            {error && <p className="form-error">{error}</p>}
            {saved && !error && (
              <p className="recommendation-empty" style={{ marginTop: 8, color: "var(--forest)" }}>
                Saved.
              </p>
            )}
            <button
              type="submit"
              style={{ marginTop: 14 }}
              disabled={saving || !nameInput.trim() || nameInput.trim() === displayName}
            >
              {saving ? "Saving..." : "Save changes"}
            </button>
          </form>
        </section>

        <section className="chart-card" style={{ marginBottom: 20 }}>
          <h2>Security &amp; sign-in</h2>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: 12 }}>
            <span className="recommendation-empty">Signed in as {email}</span>
            <button type="button" className="btn-quiet btn-small" onClick={handleLogout}>
              Log out
            </button>
          </div>
        </section>

        <section className="chart-card" style={{ marginBottom: 20 }}>
          <h2>Preferences</h2>
          <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between" }}>
            <span>Currency</span>
            <span className="field-readonly-inline">South African Rand (ZAR)</span>
          </div>
        </section>

        <section className="chart-card" style={{ marginBottom: 20 }}>
          <h2>Data &amp; privacy</h2>
          <p className="recommendation-empty" style={{ marginBottom: 12 }}>
            Export your transactions as a CSV file from the Dashboard - it exports whatever your
            current filters show.
          </p>
          <Link to="/dashboard">
            <button type="button" className="btn-quiet">
              Go to Dashboard
            </button>
          </Link>

          {cleared && (
            <p className="recommendation-empty" style={{ marginTop: 14, color: "var(--forest)" }}>
              Done - your transactions, statements, receipts, budgets and credit profile have been
              removed.
            </p>
          )}
          {email && (
            <DangerAction
              title="Clear my financial data"
              description="Removes every transaction, uploaded statement, receipt, budget, and your credit profile if you have one. Your login stays - this is a reset, not account deletion."
              actionLabel="Clear my data"
              busyLabel="Clearing..."
              confirmEmail={email}
              onConfirm={clearFinancialData}
            />
          )}
        </section>

        <section className="chart-card danger-zone">
          <h2>Danger zone</h2>
          {email && (
            <DangerAction
              title="Delete my FinMe account"
              description="Permanently removes your account and everything in it - transactions, statements, receipts, budgets, and your credit profile. This cannot be undone, and you'll be signed out immediately."
              actionLabel="Delete my account"
              busyLabel="Deleting..."
              confirmEmail={email}
              onConfirm={deleteAccount}
            />
          )}
        </section>
      </main>
    </>
  );
}
