import { useCallback, useEffect, useMemo, useState, type ChangeEvent, type FormEvent } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { apiDelete, apiGet, apiPostForm, apiPostJson, apiPut, ApiError } from "../api/client";

type Direction = "DEBIT" | "CREDIT";
type PaymentMethod = "CASH" | "CARD" | "UNKNOWN";

interface Transaction {
  id: number;
  sourceType: string;
  date: string;
  merchant: string;
  amount: number;
  /**
   * Statements report debits and credits as separate columns of positive numbers, so the
   * amount alone cannot say which way the money went - a R18,500 salary and a R18,500 purchase
   * are the same number. Without this the table renders them identically.
   */
  direction: Direction;
  category: string | null;
  description: string | null;
  paymentMethod: string;
  status: string;
}

interface BankStatementResponse {
  id: number;
  uploadDate: string;
  status: "PROCESSING" | "COMPLETE" | "FAILED";
  /** Chunk progress while PROCESSING - null until extraction has started. */
  totalChunks: number | null;
  processedChunks: number | null;
  /** Populated only when status is FAILED. */
  failureReason: string | null;
}

interface ReceiptResponse {
  id: number;
  uploadDate: string;
  status: string;
}

interface DashboardSummary {
  totalSpend: number;
  categoryBreakdown: { category: string; amount: number }[];
  trend: { month: string; amount: number }[];
}

interface RecommendationsResponse {
  recommendations: string[];
  /** Non-null when the list is empty and there is a reason worth showing the user. */
  unavailableReason: string | null;
}

/** Mirrors backend/UpdateTransactionRequest - every field is required by the API on save. */
interface TransactionEditForm {
  date: string;
  merchant: string;
  amount: string;
  direction: Direction;
  category: string;
  description: string;
  paymentMethod: PaymentMethod;
}

const ACCENT = "#aa3bff";
const PAYMENT_METHODS: PaymentMethod[] = ["CASH", "CARD", "UNKNOWN"];
const EMPTY_EDIT_FORM: TransactionEditForm = {
  date: "",
  merchant: "",
  amount: "",
  direction: "DEBIT",
  category: "",
  description: "",
  paymentMethod: "UNKNOWN",
};

const editFormFrom = (t: Transaction): TransactionEditForm => ({
  date: t.date,
  merchant: t.merchant,
  amount: String(t.amount),
  direction: t.direction,
  category: t.category ?? "",
  description: t.description ?? "",
  paymentMethod: (t.paymentMethod as PaymentMethod) ?? "UNKNOWN",
});

export function Dashboard() {
  const { token, email, logout } = useAuth();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [insights, setInsights] = useState<RecommendationsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<string | null>(null);
  const [uploadingReceipt, setUploadingReceipt] = useState(false);
  const [manualText, setManualText] = useState("");
  const [loggingManual, setLoggingManual] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Transaction search/filter - applied client-side over the already-loaded list. The backend
  // exposes the same filters as query parameters (used by the Calendar page's day drill-down,
  // where a fresh scoped fetch is the better fit); here the whole list is already in memory, so
  // a second round trip would only add latency for no benefit.
  const [filterCategory, setFilterCategory] = useState("");
  const [filterSourceType, setFilterSourceType] = useState("");
  const [filterDirection, setFilterDirection] = useState("");
  const [filterFrom, setFilterFrom] = useState("");
  const [filterTo, setFilterTo] = useState("");
  const [filterQuery, setFilterQuery] = useState("");

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<TransactionEditForm>(EMPTY_EDIT_FORM);
  const [savingEdit, setSavingEdit] = useState(false);

  const loadDashboard = useCallback(async () => {
    setLoading(true);
    try {
      const [transactionData, summaryData] = await Promise.all([
        apiGet<Transaction[]>("/api/transactions", token),
        apiGet<DashboardSummary>("/api/dashboard/summary", token),
      ]);
      setTransactions(transactionData);
      setSummary(summaryData);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load dashboard");
    } finally {
      setLoading(false);
    }

    // Fetched after the dashboard has already rendered, and never awaited alongside it: this
    // one may call rate-limited third-party providers, and the totals and charts are correct
    // whether or not the commentary on them arrives.
    try {
      setInsights(await apiGet<RecommendationsResponse>("/api/dashboard/recommendations", token));
    } catch {
      setInsights({ recommendations: [], unavailableReason: "Recommendations could not be loaded." });
    }
  }, [token]);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  const categoryOptions = useMemo(
    () =>
      [...new Set(transactions.map((t) => t.category).filter((c): c is string => Boolean(c)))].sort((a, b) =>
        a.localeCompare(b)
      ),
    [transactions]
  );

  const visibleTransactions = useMemo(() => {
    const query = filterQuery.trim().toLowerCase();
    return transactions.filter((t) => {
      if (filterCategory && t.category?.toLowerCase() !== filterCategory.toLowerCase()) return false;
      if (filterSourceType && t.sourceType !== filterSourceType) return false;
      if (filterDirection && t.direction !== filterDirection) return false;
      if (filterFrom && t.date < filterFrom) return false;
      if (filterTo && t.date > filterTo) return false;
      if (query) {
        const haystack = `${t.merchant} ${t.description ?? ""}`.toLowerCase();
        if (!haystack.includes(query)) return false;
      }
      return true;
    });
  }, [transactions, filterCategory, filterSourceType, filterDirection, filterFrom, filterTo, filterQuery]);

  const hasActiveFilters =
    filterCategory || filterSourceType || filterDirection || filterFrom || filterTo || filterQuery;

  const clearFilters = () => {
    setFilterCategory("");
    setFilterSourceType("");
    setFilterDirection("");
    setFilterFrom("");
    setFilterTo("");
    setFilterQuery("");
  };

  // Windowing the already-loaded list, not a second network request. The whole list is fetched
  // once and filtered client-side (see visibleTransactions above); a full statement upload or a
  // long history can still put hundreds of rows in one <table>, which is what actually gets
  // unusable to scroll and render - paging the DOM output fixes that without touching how
  // filtering works.
  const PAGE_SIZE = 25;
  const [currentPage, setCurrentPage] = useState(1);
  const totalPages = Math.max(1, Math.ceil(visibleTransactions.length / PAGE_SIZE));
  const pageInBounds = Math.min(currentPage, totalPages);
  const pagedTransactions = visibleTransactions.slice(
    (pageInBounds - 1) * PAGE_SIZE,
    pageInBounds * PAGE_SIZE
  );

  // A filter change can shrink the result set below the page you were on - reset rather than
  // show an empty page that looks like "no results" when results exist on page 1.
  useEffect(() => {
    setCurrentPage(1);
  }, [filterCategory, filterSourceType, filterDirection, filterFrom, filterTo, filterQuery]);

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setError(null);
    setUploading(true);
    setUploadProgress("Uploading...");
    try {
      const form = new FormData();
      form.append("file", file);
      // The server answers 202 as soon as it has stored the file - the transactions do not
      // exist yet. Extraction is paced by AI provider rate limits and can take minutes on a
      // large statement, so progress is polled rather than awaited in the request.
      const accepted = await apiPostForm<BankStatementResponse>("/api/statements", form, token);
      const settled = await pollUntilSettled(accepted.id);

      if (settled.status === "FAILED") {
        setError(settled.failureReason ?? "Statement processing failed");
      } else {
        await loadDashboard();
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Statement upload failed");
    } finally {
      setUploading(false);
      setUploadProgress(null);
      event.target.value = "";
    }
  };

  const handleManualEntry = async (event: FormEvent) => {
    event.preventDefault();
    if (!manualText.trim()) return;

    setError(null);
    setLoggingManual(true);
    try {
      await apiPostJson<Transaction[]>("/api/transactions/manual", { text: manualText.trim() }, token);
      // Cleared only after the call succeeds - on failure the user keeps what they typed and
      // can adjust it, rather than having to retype the whole description.
      setManualText("");
      await loadDashboard();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not log that entry");
    } finally {
      setLoggingManual(false);
    }
  };

  /**
   * Polls the statement until it leaves PROCESSING. The interval is deliberately unhurried:
   * extraction spends most of its time waiting out provider rate limits, so polling faster
   * would only add requests without learning anything sooner.
   * <p>
   * The deadline is 20 minutes, not a smaller "reasonable-looking" number, because free-tier
   * providers really can take that long in a genuine worst case: measured live 2026-08-27, a
   * chunk that fails over from Groq to OpenRouter (which happens whenever Groq's own quota is
   * exhausted, not rarely) takes ~50-90s on OpenRouter alone, and a large statement can need a
   * dozen chunks. Giving up too early would not fail any faster - extraction keeps running on
   * the backend regardless of whether this tab is still watching it, so "check back shortly"
   * genuinely means the statement will be there next time the dashboard is opened.
   */
  const pollUntilSettled = async (statementId: number): Promise<BankStatementResponse> => {
    const deadline = Date.now() + 20 * 60 * 1000;
    while (Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, 2000));
      const statement = await apiGet<BankStatementResponse>(`/api/statements/${statementId}`, token);
      if (statement.status !== "PROCESSING") {
        return statement;
      }
      // A percentage, not "part X of Y" - the chunk count is how the backend paces around a
      // provider rate limit, not something a user should ever have to see or understand.
      setUploadProgress(
        statement.totalChunks
          ? `Extracting your transactions... ${Math.round(
              ((statement.processedChunks ?? 0) / statement.totalChunks) * 100
            )}%`
          : "Reading statement..."
      );
    }
    throw new ApiError(504, "Statement is taking longer than expected - check back shortly");
  };

  const handleReceiptFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setError(null);
    setUploadingReceipt(true);
    try {
      const form = new FormData();
      form.append("file", file);
      await apiPostForm<ReceiptResponse>("/api/receipts", form, token);
      await loadDashboard();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Receipt upload failed");
    } finally {
      setUploadingReceipt(false);
      event.target.value = "";
    }
  };

  const startEdit = (t: Transaction) => {
    setEditingId(t.id);
    setEditForm(editFormFrom(t));
    setError(null);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditForm(EMPTY_EDIT_FORM);
  };

  const saveEdit = async (event: FormEvent) => {
    event.preventDefault();
    if (editingId === null) return;

    setSavingEdit(true);
    setError(null);
    try {
      await apiPut(
        `/api/transactions/${editingId}`,
        {
          date: editForm.date,
          merchant: editForm.merchant.trim(),
          amount: editForm.amount,
          direction: editForm.direction,
          category: editForm.category.trim(),
          description: editForm.description.trim() || null,
          paymentMethod: editForm.paymentMethod,
        },
        token
      );
      cancelEdit();
      await loadDashboard();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save that change");
    } finally {
      setSavingEdit(false);
    }
  };

  const deleteTransaction = async (t: Transaction) => {
    if (!window.confirm(`Delete this transaction? "${t.merchant}" R${t.amount.toFixed(2)} on ${t.date}.`)) {
      return;
    }
    setError(null);
    try {
      await apiDelete(`/api/transactions/${t.id}`, token);
      if (editingId === t.id) cancelEdit();
      await loadDashboard();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not delete that transaction");
    }
  };

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h1>FinMe</h1>
        <div>
          <Link to="/calendar">Calendar</Link>
          <Link to="/budgets">Budgets</Link>
          <Link to="/credit">Credit</Link>
          <span>{email}</span>
          <button type="button" onClick={logout}>
            Log out
          </button>
        </div>
      </header>

      <section className="upload-section">
        <label className="upload-button">
          {uploading ? uploadProgress ?? "Uploading..." : "Upload bank statement (PDF)"}
          <input type="file" accept="application/pdf" onChange={handleFileChange} disabled={uploading} hidden />
        </label>
        <label className="upload-button">
          {uploadingReceipt ? "Uploading..." : "Upload receipt (photo)"}
          <input
            type="file"
            accept="image/jpeg,image/png"
            onChange={handleReceiptFileChange}
            disabled={uploadingReceipt}
            hidden
          />
        </label>
        <form className="manual-entry" onSubmit={handleManualEntry}>
          <input
            type="text"
            value={manualText}
            onChange={(e) => setManualText(e.target.value)}
            placeholder="Or type a cash purchase: lunch R150 cash today"
            maxLength={500}
            disabled={loggingManual}
          />
          <button type="submit" disabled={loggingManual || !manualText.trim()}>
            {loggingManual ? "Logging..." : "Log"}
          </button>
        </form>
        {error && <p className="form-error">{error}</p>}
      </section>

      {!loading && summary && (
        <section className="summary-section">
          <div className="stat-tile">
            <span className="stat-label">Total spend</span>
            <span className="stat-value">R{summary.totalSpend.toFixed(2)}</span>
          </div>

          {insights && (insights.recommendations.length > 0 || insights.unavailableReason) && (
            <div className="chart-card chart-card--wide">
              <h2>Recommendations</h2>
              {insights.recommendations.length > 0 ? (
                <ul className="recommendation-list">
                  {insights.recommendations.map((recommendation, index) => (
                    <li key={index}>{recommendation}</li>
                  ))}
                </ul>
              ) : (
                <p className="recommendation-empty">{insights.unavailableReason}</p>
              )}
            </div>
          )}

          {summary.categoryBreakdown.length > 0 && (
            <div className="chart-card">
              <h2>Spend by category</h2>
              <ResponsiveContainer width="100%" height={Math.max(120, summary.categoryBreakdown.length * 40)}>
                <BarChart data={summary.categoryBreakdown} layout="vertical" margin={{ left: 24 }}>
                  <CartesianGrid horizontal={false} stroke="var(--border)" />
                  <XAxis type="number" tickFormatter={(v: number) => `R${v}`} stroke="var(--text)" fontSize={12} />
                  <YAxis type="category" dataKey="category" stroke="var(--text)" fontSize={12} width={100} />
                  <Tooltip formatter={(value) => [`R${Number(value).toFixed(2)}`, "Spend"]} />
                  <Bar dataKey="amount" fill={ACCENT} barSize={20} radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}

          {summary.trend.length > 0 && (
            <div className="chart-card">
              <h2>Spend trend</h2>
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={summary.trend}>
                  <CartesianGrid vertical={false} stroke="var(--border)" />
                  <XAxis dataKey="month" stroke="var(--text)" fontSize={12} />
                  <YAxis tickFormatter={(v: number) => `R${v}`} stroke="var(--text)" fontSize={12} />
                  <Tooltip formatter={(value) => [`R${Number(value).toFixed(2)}`, "Spend"]} />
                  <Line type="monotone" dataKey="amount" stroke={ACCENT} strokeWidth={2} dot={{ r: 4, fill: ACCENT }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}
        </section>
      )}

      <section>
        <h2>Transactions</h2>

        {transactions.length > 0 && (
          <div className="filter-bar">
            <input
              type="text"
              value={filterQuery}
              onChange={(e) => setFilterQuery(e.target.value)}
              placeholder="Search merchant or description"
            />
            <select value={filterCategory} onChange={(e) => setFilterCategory(e.target.value)}>
              <option value="">All categories</option>
              {categoryOptions.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
            <select value={filterSourceType} onChange={(e) => setFilterSourceType(e.target.value)}>
              <option value="">All sources</option>
              <option value="STATEMENT">Statement</option>
              <option value="RECEIPT">Receipt</option>
              <option value="MANUAL">Manual</option>
            </select>
            <select value={filterDirection} onChange={(e) => setFilterDirection(e.target.value)}>
              <option value="">Debits &amp; credits</option>
              <option value="DEBIT">Debits only</option>
              <option value="CREDIT">Credits only</option>
            </select>
            <input type="date" value={filterFrom} onChange={(e) => setFilterFrom(e.target.value)} aria-label="From date" />
            <input type="date" value={filterTo} onChange={(e) => setFilterTo(e.target.value)} aria-label="To date" />
            {hasActiveFilters && (
              <button type="button" onClick={clearFilters}>
                Clear filters
              </button>
            )}
          </div>
        )}

        {editingId !== null && (
          <form className="credit-form transaction-edit-form" onSubmit={saveEdit}>
            <input
              type="date"
              value={editForm.date}
              onChange={(e) => setEditForm({ ...editForm, date: e.target.value })}
              required
              disabled={savingEdit}
            />
            <input
              type="text"
              value={editForm.merchant}
              onChange={(e) => setEditForm({ ...editForm, merchant: e.target.value })}
              placeholder="Merchant"
              maxLength={200}
              required
              disabled={savingEdit}
            />
            <input
              type="number"
              step="0.01"
              min="0"
              value={editForm.amount}
              onChange={(e) => setEditForm({ ...editForm, amount: e.target.value })}
              placeholder="Amount"
              required
              disabled={savingEdit}
            />
            <select
              value={editForm.direction}
              onChange={(e) => setEditForm({ ...editForm, direction: e.target.value as Direction })}
              disabled={savingEdit}
            >
              <option value="DEBIT">Debit (spent)</option>
              <option value="CREDIT">Credit (received)</option>
            </select>
            {/* Free text with suggestions, not a fixed dropdown - category has no restricted
                list anywhere in this app; the AI's category list is a prompt hint, not a schema
                constraint, so a correction here can rename or invent a category freely. */}
            <input
              type="text"
              list="category-suggestions"
              value={editForm.category}
              onChange={(e) => setEditForm({ ...editForm, category: e.target.value })}
              placeholder="Category"
              maxLength={60}
              required
              disabled={savingEdit}
            />
            <input
              type="text"
              value={editForm.description}
              onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
              placeholder="Description (optional)"
              maxLength={500}
              disabled={savingEdit}
            />
            <select
              value={editForm.paymentMethod}
              onChange={(e) => setEditForm({ ...editForm, paymentMethod: e.target.value as PaymentMethod })}
              disabled={savingEdit}
            >
              {PAYMENT_METHODS.map((m) => (
                <option key={m} value={m}>
                  {m.charAt(0) + m.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
            <button type="submit" disabled={savingEdit}>
              {savingEdit ? "Saving..." : "Save changes"}
            </button>
            <button type="button" onClick={cancelEdit} disabled={savingEdit}>
              Cancel
            </button>
          </form>
        )}
        <datalist id="category-suggestions">
          {categoryOptions.map((c) => (
            <option key={c} value={c} />
          ))}
        </datalist>

        {loading ? (
          <p>Loading...</p>
        ) : transactions.length === 0 ? (
          <p>No transactions yet - upload a statement to get started.</p>
        ) : visibleTransactions.length === 0 ? (
          <p>No transactions match these filters.</p>
        ) : (
          <table className="transaction-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Merchant</th>
                <th>Category</th>
                <th>Description</th>
                <th>Amount</th>
                <th>Source</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {pagedTransactions.map((t) => (
                <tr key={t.id} className={editingId === t.id ? "editing-row" : undefined}>
                  <td>{t.date}</td>
                  <td>{t.merchant}</td>
                  <td>{t.category ?? "-"}</td>
                  <td>{t.description ?? "-"}</td>
                  <td className={t.direction === "CREDIT" ? "amount-credit" : undefined}>
                    {t.direction === "CREDIT" ? "+" : ""}R{t.amount.toFixed(2)}
                  </td>
                  <td>{t.sourceType}</td>
                  <td className="row-actions">
                    <button type="button" onClick={() => startEdit(t)}>
                      Edit
                    </button>
                    <button type="button" onClick={() => void deleteTransaction(t)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {visibleTransactions.length > PAGE_SIZE && (
          <div className="pager">
            <button
              type="button"
              onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              disabled={pageInBounds <= 1}
            >
              &larr; Prev
            </button>
            <span>
              Page {pageInBounds} of {totalPages} ({visibleTransactions.length} transactions)
            </span>
            <button
              type="button"
              onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              disabled={pageInBounds >= totalPages}
            >
              Next &rarr;
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
