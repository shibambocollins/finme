import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { apiDelete, apiGet, apiPostJson, apiPut, ApiError } from "../api/client";

type PaymentStatus = "ON_TIME" | "LATE" | "DEFAULTED" | "UNKNOWN";

interface CreditAccount {
  id: number;
  accountName: string;
  balance: number;
  creditLimit: number;
  paymentStatus: PaymentStatus;
}

interface CreditProfile {
  id: number;
  bureau: string;
  /** The profile's own scale, not a constant - a different bureau has a different ceiling. */
  maxScore: number;
  createdAt: string;
  currentScore: number | null;
  scoreRecordedAt: string | null;
  accounts: CreditAccount[];
}

const PAYMENT_STATUSES: PaymentStatus[] = ["ON_TIME", "LATE", "DEFAULTED", "UNKNOWN"];

const EMPTY_ACCOUNT = { accountName: "", balance: "", creditLimit: "", paymentStatus: "UNKNOWN" as PaymentStatus };

export function Credit() {
  const { token } = useAuth();
  const [profile, setProfile] = useState<CreditProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [accountForm, setAccountForm] = useState(EMPTY_ACCOUNT);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [scoreInput, setScoreInput] = useState("");

  const loadProfile = useCallback(async () => {
    setLoading(true);
    try {
      setProfile(await apiGet<CreditProfile>("/api/credit/profile", token));
      setError(null);
    } catch (err) {
      // 404 is the ordinary state for a user who has not opted in - the credit module is
      // optional, so "no profile" is a starting point to offer, not a failure to report.
      if (err instanceof ApiError && err.status === 404) {
        setProfile(null);
        setError(null);
      } else {
        setError(err instanceof ApiError ? err.message : "Could not load your credit profile");
      }
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void loadProfile();
  }, [loadProfile]);

  /** Every mutation returns the whole profile, so the view is replaced rather than patched. */
  const run = async (action: () => Promise<CreditProfile | void>) => {
    setBusy(true);
    setError(null);
    try {
      const updated = await action();
      if (updated) {
        setProfile(updated);
      } else {
        await loadProfile();
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong");
    } finally {
      setBusy(false);
    }
  };

  const createProfile = () =>
    run(() => apiPostJson<CreditProfile>("/api/credit/profile", {}, token));

  const submitAccount = (event: FormEvent) => {
    event.preventDefault();
    const body = {
      accountName: accountForm.accountName.trim(),
      balance: accountForm.balance,
      creditLimit: accountForm.creditLimit,
      paymentStatus: accountForm.paymentStatus,
    };
    void run(async () => {
      const updated = editingId
        ? await apiPut<CreditProfile>(`/api/credit/accounts/${editingId}`, body, token)
        : await apiPostJson<CreditProfile>("/api/credit/accounts", body, token);
      setAccountForm(EMPTY_ACCOUNT);
      setEditingId(null);
      return updated;
    });
  };

  const editAccount = (account: CreditAccount) => {
    setEditingId(account.id);
    setAccountForm({
      accountName: account.accountName,
      balance: String(account.balance),
      creditLimit: String(account.creditLimit),
      paymentStatus: account.paymentStatus,
    });
  };

  const cancelEdit = () => {
    setEditingId(null);
    setAccountForm(EMPTY_ACCOUNT);
  };

  const removeAccount = (id: number) =>
    run(() => apiDelete<CreditProfile>(`/api/credit/accounts/${id}`, token));

  const submitScore = (event: FormEvent) => {
    event.preventDefault();
    void run(async () => {
      const updated = await apiPostJson<CreditProfile>(
        "/api/credit/score",
        { score: Number(scoreInput) },
        token
      );
      setScoreInput("");
      return updated;
    });
  };

  return (
    <main className="page">
      <header className="page-header">
        <h1>Credit</h1>
        <Link to="/dashboard">Back to dashboard</Link>
      </header>

      {error && <p className="form-error">{error}</p>}

      {loading ? (
        <p>Loading...</p>
      ) : !profile ? (
        <section className="chart-card">
          <h2>Track your credit position</h2>
          <p>
            Add your credit accounts and score to see where you stand. This is optional - your
            spending dashboard works without it.
          </p>
          <button type="button" onClick={() => void createProfile()} disabled={busy}>
            {busy ? "Creating..." : "Create credit profile"}
          </button>
        </section>
      ) : (
        <>
          <section className="summary-section">
            <div className="stat-tile">
              <span className="stat-label">Current score ({profile.bureau})</span>
              <span className="stat-value">
                {profile.currentScore === null
                  ? "Not recorded"
                  : `${profile.currentScore} / ${profile.maxScore}`}
              </span>
              {profile.scoreRecordedAt && (
                <span className="stat-label">
                  Recorded {new Date(profile.scoreRecordedAt).toLocaleDateString()}
                </span>
              )}
            </div>

            <div className="chart-card">
              <h2>Record a score</h2>
              <form className="manual-entry" onSubmit={submitScore}>
                <input
                  type="number"
                  min={0}
                  max={profile.maxScore}
                  value={scoreInput}
                  onChange={(e) => setScoreInput(e.target.value)}
                  placeholder={`Your latest score (0 - ${profile.maxScore})`}
                  disabled={busy}
                />
                <button type="submit" disabled={busy || !scoreInput}>
                  Save
                </button>
              </form>
              <p className="recommendation-empty">
                Each reading is kept, so you can see how your score moves over time.
              </p>
            </div>
          </section>

          <section className="chart-card chart-card--wide">
            <h2>{editingId ? "Edit account" : "Add a credit account"}</h2>
            <form className="credit-form" onSubmit={submitAccount}>
              <input
                type="text"
                value={accountForm.accountName}
                onChange={(e) => setAccountForm({ ...accountForm, accountName: e.target.value })}
                placeholder="Account name, e.g. Visa Gold"
                maxLength={100}
                required
                disabled={busy}
              />
              <input
                type="number"
                step="0.01"
                min="0"
                value={accountForm.balance}
                onChange={(e) => setAccountForm({ ...accountForm, balance: e.target.value })}
                placeholder="Balance"
                required
                disabled={busy}
              />
              <input
                type="number"
                step="0.01"
                min="0.01"
                value={accountForm.creditLimit}
                onChange={(e) => setAccountForm({ ...accountForm, creditLimit: e.target.value })}
                placeholder="Credit limit"
                required
                disabled={busy}
              />
              <select
                value={accountForm.paymentStatus}
                onChange={(e) =>
                  setAccountForm({ ...accountForm, paymentStatus: e.target.value as PaymentStatus })
                }
                disabled={busy}
              >
                {PAYMENT_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status.replace("_", " ").toLowerCase()}
                  </option>
                ))}
              </select>
              <button type="submit" disabled={busy}>
                {editingId ? "Save changes" : "Add account"}
              </button>
              {editingId && (
                <button type="button" onClick={cancelEdit} disabled={busy}>
                  Cancel
                </button>
              )}
            </form>
          </section>

          <section>
            <h2>Your credit accounts</h2>
            {profile.accounts.length === 0 ? (
              <p>No accounts yet - add one above.</p>
            ) : (
              <table className="transaction-table">
                <thead>
                  <tr>
                    <th>Account</th>
                    <th>Balance</th>
                    <th>Limit</th>
                    <th>Payments</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {profile.accounts.map((account) => (
                    <tr key={account.id}>
                      <td>{account.accountName}</td>
                      <td>R{account.balance.toFixed(2)}</td>
                      <td>R{account.creditLimit.toFixed(2)}</td>
                      <td>{account.paymentStatus.replace("_", " ").toLowerCase()}</td>
                      <td>
                        <button type="button" onClick={() => editAccount(account)} disabled={busy}>
                          Edit
                        </button>{" "}
                        <button type="button" onClick={() => void removeAccount(account.id)} disabled={busy}>
                          Remove
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {/*
              No utilization figure here on purpose. That calculation is FR-2.2.1 and lands in
              Iteration 9, computed on the server - deriving it in the browser now would put the
              one number this project insists is deterministic into the least controlled place.
            */}
          </section>
        </>
      )}
    </main>
  );
}
