import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import { apiDelete, apiGet, apiPostJson, ApiError } from "../api/client";
import { AppHeader } from "../components/AppHeader";
import { SUGGESTED_CATEGORIES } from "../constants/categories";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

interface BudgetStatus {
  id: number;
  category: string;
  monthlyLimit: number;
  spent: number;
  remaining: number;
  /** A ratio - 0.42 is 42%, uncapped, so it can exceed 1.0 once over budget. */
  percentUsed: number;
  overBudget: boolean;
}

const EMPTY_FORM = { category: "", monthlyLimit: "" };

export function Budgets() {
  useDocumentTitle("Budgets | FinMe");
  const { token } = useAuth();
  const [budgets, setBudgets] = useState<BudgetStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState(EMPTY_FORM);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setBudgets(await apiGet<BudgetStatus[]>("/api/budgets", token));
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load your budgets");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await apiPostJson("/api/budgets", { category: form.category.trim(), monthlyLimit: form.monthlyLimit }, token);
      setForm(EMPTY_FORM);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save that budget");
    } finally {
      setBusy(false);
    }
  };

  const remove = async (budget: BudgetStatus) => {
    if (!window.confirm(`Remove the ${budget.category} budget?`)) return;
    setBusy(true);
    setError(null);
    try {
      await apiDelete(`/api/budgets/${budget.id}`, token);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove that budget");
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <AppHeader active="budgets" />
      <main className="page" id="main-content">
        <h1>Budgets</h1>

      {error && <p className="form-error">{error}</p>}

      <section className="chart-card chart-card--wide">
        <h2>Set a monthly budget</h2>
        <p className="recommendation-empty">
          Budgets carry forward every month - set it once, and it applies to July, August, and
          every month after until you change or remove it.
        </p>
        <form className="credit-form" onSubmit={submit}>
          <input
            type="text"
            list="budget-category-suggestions"
            value={form.category}
            onChange={(e) => setForm({ ...form, category: e.target.value })}
            placeholder="Category, e.g. Groceries"
            maxLength={60}
            required
            disabled={busy}
          />
          <datalist id="budget-category-suggestions">
            {SUGGESTED_CATEGORIES.map((c) => (
              <option key={c} value={c} />
            ))}
          </datalist>
          <input
            type="number"
            step="0.01"
            min="0.01"
            value={form.monthlyLimit}
            onChange={(e) => setForm({ ...form, monthlyLimit: e.target.value })}
            placeholder="Monthly limit"
            required
            disabled={busy}
          />
          <button type="submit" disabled={busy || !form.category.trim() || !form.monthlyLimit}>
            Save budget
          </button>
        </form>
      </section>

      <section>
        <h2>This month</h2>
        {loading ? (
          <div className="skeleton-list">
            {[64, 72, 58].map((width, i) => (
              <div className="skeleton-row" key={i}>
                <span className="skeleton-bar" style={{ width: "40%" }} />
                <span className="skeleton-bar" style={{ width: `${width}px`, flex: "0 0 auto" }} />
              </div>
            ))}
          </div>
        ) : budgets.length === 0 ? (
          <div className="empty-state">
            <p>No budgets set yet - add one above to start tracking against it.</p>
          </div>
        ) : (
          <div className="budget-list">
            {budgets.map((b) => (
              <div key={b.id} className={`chart-card budget-card${b.overBudget ? " budget-card--over" : ""}`}>
                <div className="budget-card-header">
                  <h3>{b.category}</h3>
                  <button type="button" className="btn-delete btn-small" onClick={() => void remove(b)} disabled={busy}>
                    Remove
                  </button>
                </div>
                <div className="budget-progress-track">
                  <div
                    className="budget-progress-fill"
                    style={{ width: `${Math.min(100, b.percentUsed * 100)}%` }}
                  />
                </div>
                <p className="budget-figures">
                  R{b.spent.toFixed(2)} of R{b.monthlyLimit.toFixed(2)}{" "}
                  <span className="recommendation-empty">({(b.percentUsed * 100).toFixed(0)}%)</span>
                </p>
                {b.overBudget ? (
                  <p className="budget-over-note">R{Math.abs(b.remaining).toFixed(2)} over budget</p>
                ) : (
                  <p className="recommendation-empty">R{b.remaining.toFixed(2)} remaining</p>
                )}
              </div>
            ))}
          </div>
        )}
      </section>
      </main>
    </>
  );
}
