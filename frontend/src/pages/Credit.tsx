import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import { apiDelete, apiGet, apiPostJson, apiPut, ApiError } from "../api/client";
import { AppHeader } from "../components/AppHeader";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

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

interface AccountUtilization {
  accountId: number;
  accountName: string;
  balance: number;
  creditLimit: number;
  /** Ratio against this account's own limit - 0.95 is 95%. */
  utilization: number | null;
  /** How far overall utilization would fall if this account were cleared. The ranking key. */
  overallReduction: number | null;
}

interface CreditAnalysis {
  overallUtilization: number | null;
  totalBalance: number;
  totalLimit: number;
  accounts: AccountUtilization[];
  plan: string[];
  planUnavailableReason: string | null;
  /** FR-2.3.3 - server-supplied constant, never model output. Always rendered. */
  disclaimer: string;
}

interface Simulation {
  currentOverall: number;
  simulatedOverall: number;
  change: number;
  note: string;
}

interface ScoreComparison {
  currentScore: number | null;
  previousScore: number | null;
  previousRecordedAt: string | null;
  change: number | null;
  daysApart: number | null;
  message: string;
}

const PAYMENT_STATUSES: PaymentStatus[] = ["ON_TIME", "LATE", "DEFAULTED", "UNKNOWN"];

const PAYMENT_STATUS_TAG_CLASS: Record<PaymentStatus, string> = {
  ON_TIME: "tag tag--on-time",
  LATE: "tag tag--late",
  DEFAULTED: "tag tag--defaulted",
  UNKNOWN: "tag",
};

const percent = (ratio: number | null) =>
  ratio === null ? "n/a" : `${(ratio * 100).toFixed(1)}%`;

const EMPTY_ACCOUNT = { accountName: "", balance: "", creditLimit: "", paymentStatus: "UNKNOWN" as PaymentStatus };

export function Credit() {
  useDocumentTitle("Credit | FinMe");
  const { token } = useAuth();
  const [profile, setProfile] = useState<CreditProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [accountForm, setAccountForm] = useState(EMPTY_ACCOUNT);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [scoreInput, setScoreInput] = useState("");
  const [analysis, setAnalysis] = useState<CreditAnalysis | null>(null);
  const [comparison, setComparison] = useState<ScoreComparison | null>(null);
  const [simAccountId, setSimAccountId] = useState("");
  const [simBalance, setSimBalance] = useState("");
  const [simulation, setSimulation] = useState<Simulation | null>(null);

  const loadProfile = useCallback(async () => {
    setLoading(true);
    try {
      setProfile(await apiGet<CreditProfile>("/api/credit/profile", token));
      setError(null);
      // Fetched after the profile, and never allowed to fail the page: the analysis calls an
      // AI provider for its plan, while the profile above is plain stored data.
      const [analysisData, comparisonData] = await Promise.all([
        apiGet<CreditAnalysis>("/api/credit/analysis", token).catch(() => null),
        apiGet<ScoreComparison>("/api/credit/score/comparison", token).catch(() => null),
      ]);
      setAnalysis(analysisData);
      setComparison(comparisonData);
    } catch (err) {
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

  /**
   * Every mutation returns the whole profile, so the view is replaced rather than patched.
   * <p>
   * The derived views are refetched too, and that is not optional: adding an account or
   * recording a score changes the calculated utilization and the score comparison, and those
   * come from different endpoints. Updating only the profile would leave "Where you stand"
   * showing figures from before the change - stale numbers that look current, which is worse
   * than showing nothing.
   */
  const run = async (action: () => Promise<CreditProfile | void>) => {
    setBusy(true);
    setError(null);
    try {
      const updated = await action();
      if (updated) {
        setProfile(updated);
      }
      // A simulation was run against balances that may no longer be current.
      setSimulation(null);
      const [analysisData, comparisonData] = await Promise.all([
        apiGet<CreditAnalysis>("/api/credit/analysis", token).catch(() => null),
        apiGet<ScoreComparison>("/api/credit/score/comparison", token).catch(() => null),
      ]);
      setAnalysis(analysisData);
      setComparison(comparisonData);
      if (!updated) {
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

  const submitSimulation = (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    apiPostJson<Simulation>(
      "/api/credit/simulate",
      { accountId: Number(simAccountId), newBalance: simBalance },
      token
    )
      .then(setSimulation)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Could not run that simulation")
      )
      .finally(() => setBusy(false));
  };

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
    <>
      <AppHeader active="credit" />
      <main className="page" id="main-content">
        <h1>Credit</h1>

      {error && <p className="form-error">{error}</p>}

      {loading ? (
        <div className="skeleton-list">
          {[68, 76, 60].map((width, i) => (
            <div className="skeleton-row" key={i}>
              <span className="skeleton-bar" style={{ width: "44%" }} />
              <span className="skeleton-bar" style={{ width: `${width}px`, flex: "0 0 auto" }} />
            </div>
          ))}
        </div>
      ) : !profile ? (
        <section className="chart-card">
          <h2>Track your credit position</h2>
          <p className="recommendation-empty">
            Add your credit accounts and score to see where you stand. This is optional - your
            spending dashboard works without it.
          </p>
          <button type="button" style={{ marginTop: 16 }} onClick={() => void createProfile()} disabled={busy}>
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
                <button type="button" className="btn-quiet" onClick={cancelEdit} disabled={busy}>
                  Cancel
                </button>
              )}
            </form>
          </section>

          {analysis && (
            <section className="chart-card chart-card--wide">
              <h2>Where you stand</h2>
              <p className="stat-value">
                {analysis.overallUtilization === null
                  ? "Add an account to see your utilization"
                  : `${percent(analysis.overallUtilization)} overall utilization`}
              </p>
              {analysis.overallUtilization !== null && (
                <p className="recommendation-empty">
                  R{analysis.totalBalance.toFixed(2)} of R{analysis.totalLimit.toFixed(2)} in use
                </p>
              )}

              {analysis.accounts.length > 0 && (
                <table className="transaction-table">
                  <thead>
                    <tr>
                      <th>Account</th>
                      <th>Its own utilization</th>
                      <th>Clearing it lowers overall by</th>
                    </tr>
                  </thead>
                  <tbody>
                    {analysis.accounts.map((a) => (
                      <tr key={a.accountId}>
                        <td data-label="Account">{a.accountName}</td>
                        <td data-label="Its own utilization">{percent(a.utilization)}</td>
                        <td data-label="Clearing it lowers overall by">{percent(a.overallReduction)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}

              <h3>What to do first</h3>
              {analysis.plan.length > 0 ? (
                <ol className="recommendation-list">
                  {analysis.plan.map((step, i) => (
                    <li key={i}>{step}</li>
                  ))}
                </ol>
              ) : (
                <p className="recommendation-empty">{analysis.planUnavailableReason}</p>
              )}

              {/* FR-2.3.3 - rendered whenever an analysis is shown, plan or no plan. */}
              <p className="credit-disclaimer">{analysis.disclaimer}</p>
            </section>
          )}

          {analysis && analysis.accounts.length > 0 && (
            <section className="chart-card chart-card--wide">
              <h2>What if you paid one down?</h2>
              <form className="credit-form" onSubmit={submitSimulation}>
                <select
                  value={simAccountId}
                  onChange={(e) => setSimAccountId(e.target.value)}
                  required
                  disabled={busy}
                >
                  <option value="">Choose an account</option>
                  {analysis.accounts.map((a) => (
                    <option key={a.accountId} value={a.accountId}>
                      {a.accountName}
                    </option>
                  ))}
                </select>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={simBalance}
                  onChange={(e) => setSimBalance(e.target.value)}
                  placeholder="New balance"
                  required
                  disabled={busy}
                />
                <button type="submit" disabled={busy || !simAccountId || !simBalance}>
                  Simulate
                </button>
              </form>
              {simulation && (
                <>
                  <p>
                    {percent(simulation.currentOverall)} &rarr;{" "}
                    <strong>{percent(simulation.simulatedOverall)}</strong>{" "}
                    ({simulation.change <= 0 ? "" : "+"}
                    {(simulation.change * 100).toFixed(1)} points of utilization)
                  </p>
                  <p className="recommendation-empty">{simulation.note}</p>
                </>
              )}
            </section>
          )}

          {comparison && comparison.previousScore !== null && (
            <section className="chart-card chart-card--wide">
              <h2>Progress</h2>
              <p className="stat-value">{comparison.message}</p>
              <p className="recommendation-empty">
                {comparison.previousScore} on{" "}
                {new Date(comparison.previousRecordedAt as string).toLocaleDateString()} &rarr;{" "}
                {comparison.currentScore} now
              </p>
            </section>
          )}

          <section>
            <h2>Your credit accounts</h2>
            {profile.accounts.length === 0 ? (
              <div className="empty-state">
                <p>No accounts yet - add one above.</p>
              </div>
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
                      <td data-label="Account">{account.accountName}</td>
                      <td data-label="Balance">R{account.balance.toFixed(2)}</td>
                      <td data-label="Limit">R{account.creditLimit.toFixed(2)}</td>
                      <td data-label="Payments">
                        <span className={PAYMENT_STATUS_TAG_CLASS[account.paymentStatus]}>
                          {account.paymentStatus.replace("_", " ").toLowerCase()}
                        </span>
                      </td>
                      <td data-label="" className="row-actions">
                        <button type="button" className="btn-quiet btn-small" onClick={() => editAccount(account)} disabled={busy}>
                          Edit
                        </button>
                        <button type="button" className="btn-delete btn-small" onClick={() => void removeAccount(account.id)} disabled={busy}>
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
    </>
  );
}
