import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { apiPut, ApiError } from "../api/client";
import { AppHeader } from "../components/AppHeader";

interface ProfileResponse {
  email: string;
  displayName: string;
}

export function Settings() {
  const { token, email, displayName, updateDisplayName, logout } = useAuth();
  const navigate = useNavigate();
  const [nameInput, setNameInput] = useState(displayName ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

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

  return (
    <>
      <AppHeader active="settings" />
      <main className="page page--narrow">
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

        <section className="chart-card">
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
        </section>
      </main>
    </>
  );
}
