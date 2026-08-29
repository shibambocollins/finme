import { useCallback, useEffect, useMemo, useState, type ChangeEvent, type FormEvent } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useAuth } from "../auth/AuthContext";
import { apiDelete, apiGet, apiPostForm, apiPostJson, apiPut, ApiError } from "../api/client";
import { AppHeader } from "../components/AppHeader";
import { SUGGESTED_CATEGORIES } from "../constants/categories";
import { downloadCsv } from "../utils/csv";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

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

const ACCENT = "#12503a"; // --forest, kept as a literal hex since recharts props take real colours, not CSS vars
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

/** "2026-08" -> "Aug" - the trend axis reads across a handful of recent months, where the year
 *  is implied and just adds noise; the month-over-month comparison line above it still says the
 *  full previous-month name for anyone who needs the year disambiguated. */
const formatMonthShort = (month: string): string => {
  const [year, m] = month.split("-").map(Number);
  return new Date(year, m - 1, 1).toLocaleDateString(undefined, { month: "short" });
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
  useDocumentTitle("Dashboard | FinMe");
  const { token } = useAuth();
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

  // Purely a client-side window over the trend series already fetched for the chart - the
  // backend returns the whole history in one call, so "3 months" vs "6 months" is just how much
  // of the end of that array gets rendered, not a different request.
  const [trendRange, setTrendRange] = useState<"3" | "6" | "all">("6");

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

  // Categories actually present in the data - drives the filter dropdown, where showing an
  // unused category would just be a selectable option that always returns nothing.
  const categoryOptions = useMemo(
    () =>
      [...new Set(transactions.map((t) => t.category).filter((c): c is string => Boolean(c)))].sort((a, b) =>
        a.localeCompare(b)
      ),
    [transactions]
  );

  // Seed list merged with whatever the user has actually used - drives the free-text category
  // datalist, where the goal is good suggestions (including for a brand-new user with no
  // transactions yet), not a restriction to what already exists.
  const categorySuggestions = useMemo(
    () => [...new Set([...SUGGESTED_CATEGORIES, ...categoryOptions])].sort((a, b) => a.localeCompare(b)),
    [categoryOptions]
  );

  // Total-spend month-over-month only - the backend's category breakdown is all-time, not
  // scoped per month, so a per-category comparison ("18% less on takeaways") would need a new
  // aggregation query. This reads entirely from the trend series already fetched for the chart.
  const spendComparison = useMemo(() => {
    if (!summary || summary.trend.length < 2) return null;
    const sorted = [...summary.trend].sort((a, b) => a.month.localeCompare(b.month));
    const current = sorted[sorted.length - 1];
    const previous = sorted[sorted.length - 2];
    if (previous.amount === 0) return null;

    const [year, month] = previous.month.split("-").map(Number);
    const previousMonthLabel = new Date(year, month - 1, 1).toLocaleDateString(undefined, { month: "long" });
    const percentChange = ((current.amount - previous.amount) / previous.amount) * 100;
    const rounded = Math.round(Math.abs(percentChange));
    if (rounded === 0) return `About the same as ${previousMonthLabel}`;
    return `${rounded}% ${percentChange > 0 ? "more" : "less"} than ${previousMonthLabel}`;
  }, [summary]);

  const trendData = useMemo(() => {
    if (!summary) return [];
    const sorted = [...summary.trend].sort((a, b) => a.month.localeCompare(b.month));
    if (trendRange === "all") return sorted;
    return sorted.slice(-Number(trendRange));
  }, [summary, trendRange]);

  // Money in vs money out - the direction every transaction already carries, just summed
  // instead of listed. All-time, matching the scope of the total-spend figure above it.
  const moneyFlow = useMemo(() => {
    let in_ = 0;
    let out = 0;
    for (const t of transactions) {
      if (t.direction === "CREDIT") in_ += t.amount;
      else out += t.amount;
    }
    return [
      { label: "Money in", amount: in_ },
      { label: "Money out", amount: out },
    ];
  }, [transactions]);

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

  // Exports whatever the filters currently show, not the whole account - "export what I'm
  // looking at" matches how the filter bar already behaves everywhere else on this page.
  const exportVisibleAsCsv = () => {
    downloadCsv(
      `finme-transactions-${new Date().toISOString().slice(0, 10)}.csv`,
      ["Date", "Merchant", "Category", "Description", "Amount", "Direction", "Source", "Payment Method"],
      visibleTransactions.map((t) => [
        t.date,
        t.merchant,
        t.category ?? "",
        t.description ?? "",
        t.amount.toFixed(2),
        t.direction,
        t.sourceType,
        t.paymentMethod,
      ])
    );
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
   * providers really can take that long in a genuine worst case - a chunk falling back from
   * Groq to OpenRouter isn't rare, and a large statement can need a dozen chunks. Giving up
   * early wouldn't fail any faster either: extraction keeps running on the backend regardless
   * of whether this tab is watching, so "check back shortly" genuinely means the statement will
   * be there next time the dashboard is opened.
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

  // The backend reports progress as a percentage baked into uploadProgress's text (see
  // pollUntilSettled) - pulled back out here only to drive the visual bar, never re-derived.
  const uploadPercentMatch = uploadProgress?.match(/(\d+)%/);
  const uploadPercent = uploadPercentMatch ? Number(uploadPercentMatch[1]) : null;

  return (
    <>
      <AppHeader active="dashboard" />
      <main className="page" id="main-content">
      <section className="upload-section">
        <div>
          <label className="upload-button">
            {uploading ? uploadProgress ?? "Uploading..." : "Upload bank statement (PDF)"}
            <input type="file" accept="application/pdf" onChange={handleFileChange} disabled={uploading} hidden />
          </label>
          {uploading && (
            <div className="upload-progress-track">
              <div
                className="upload-progress-fill"
                style={{ width: uploadPercent !== null ? `${uploadPercent}%` : "12%" }}
              />
            </div>
          )}
        </div>
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
            {spendComparison && <span className="stat-label">{spendComparison}</span>}
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
            <div className="chart-card chart-card--wide">
              <h2>Spend by category</h2>
              <ResponsiveContainer width="100%" height={Math.max(160, summary.categoryBreakdown.length * 34)}>
                <BarChart
                  data={summary.categoryBreakdown}
                  layout="vertical"
                  margin={{ left: 8, right: 24 }}
                  barCategoryGap={10}
                >
                  <CartesianGrid horizontal stroke="var(--line)" />
                  <XAxis type="number" tickFormatter={(v: number) => `R${v}`} stroke="var(--ink-50)" fontSize={12} />
                  <YAxis type="category" dataKey="category" stroke="var(--ink-50)" fontSize={12} width={140} />
                  <Tooltip formatter={(value) => [`R${Number(value).toFixed(2)}`, "Spend"]} />
                  <Bar
                    dataKey="amount"
                    fill={ACCENT}
                    stroke="var(--forest-deep)"
                    strokeWidth={1}
                    barSize={18}
                    radius={[0, 4, 4, 0]}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}

          {summary.trend.length > 0 && (
            <div className="chart-card">
              <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 12 }}>
                <h2>Spend trend</h2>
                <select
                  value={trendRange}
                  onChange={(e) => setTrendRange(e.target.value as "3" | "6" | "all")}
                  aria-label="Trend range"
                  style={{ padding: "4px 8px", fontSize: 12.5 }}
                >
                  <option value="3">Last 3 months</option>
                  <option value="6">Last 6 months</option>
                  <option value="all">All time</option>
                </select>
              </div>
              <ResponsiveContainer width="100%" height={220}>
                <LineChart data={trendData}>
                  <CartesianGrid vertical={false} stroke="var(--line)" />
                  <XAxis dataKey="month" tickFormatter={formatMonthShort} stroke="var(--ink-50)" fontSize={12} />
                  <YAxis tickFormatter={(v: number) => `R${v}`} stroke="var(--ink-50)" fontSize={12} />
                  <Tooltip
                    labelFormatter={(label) => (typeof label === "string" ? formatMonthShort(label) : label)}
                    formatter={(value) => [`R${Number(value).toFixed(2)}`, "Spend"]}
                  />
                  <Line type="monotone" dataKey="amount" stroke={ACCENT} strokeWidth={2} dot={{ r: 4, fill: ACCENT }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          )}

          {(moneyFlow[0].amount > 0 || moneyFlow[1].amount > 0) && (
            <div className="chart-card">
              <h2>Money in vs money out</h2>
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={moneyFlow} margin={{ top: 8 }}>
                  <CartesianGrid vertical={false} stroke="var(--line)" />
                  <XAxis dataKey="label" stroke="var(--ink-50)" fontSize={12} />
                  <YAxis tickFormatter={(v: number) => `R${v}`} stroke="var(--ink-50)" fontSize={12} />
                  <Tooltip formatter={(value) => [`R${Number(value).toFixed(2)}`, "Amount"]} />
                  <Bar dataKey="amount" radius={[4, 4, 0, 0]} barSize={64} stroke="var(--forest-deep)" strokeWidth={1}>
                    <Cell fill={ACCENT} />
                    <Cell fill="var(--sage)" />
                  </Bar>
                </BarChart>
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
              <button type="button" className="btn-quiet" onClick={clearFilters}>
                Clear filters
              </button>
            )}
            <button type="button" className="btn-quiet" onClick={exportVisibleAsCsv} disabled={visibleTransactions.length === 0}>
              Export CSV
            </button>
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
            <button type="button" className="btn-quiet" onClick={cancelEdit} disabled={savingEdit}>
              Cancel
            </button>
          </form>
        )}
        <datalist id="category-suggestions">
          {categorySuggestions.map((c) => (
            <option key={c} value={c} />
          ))}
        </datalist>

        {loading ? (
          <div className="skeleton-list">
            {[72, 88, 64, 80, 70].map((width, i) => (
              <div className="skeleton-row" key={i}>
                <span className="skeleton-bar" style={{ width: `${48 - i * 3}%` }} />
                <span className="skeleton-bar" style={{ width: `${width}px`, flex: "0 0 auto" }} />
              </div>
            ))}
          </div>
        ) : transactions.length === 0 ? (
          <div className="empty-state">
            <h2>Nothing in your ledger yet.</h2>
            <p>
              Start with last month's bank statement - it fills a whole month in one go. Receipts
              and cash entries slot in afterwards.
            </p>
          </div>
        ) : visibleTransactions.length === 0 ? (
          <p className="recommendation-empty">No transactions match these filters.</p>
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
                  <td data-label="Date">{t.date}</td>
                  <td data-label="Merchant">{t.merchant}</td>
                  <td data-label="Category">{t.category ?? "-"}</td>
                  <td data-label="Description">{t.description ?? "-"}</td>
                  <td data-label="Amount" className={t.direction === "CREDIT" ? "amount-credit" : undefined}>
                    {t.direction === "CREDIT" ? "+" : ""}R{t.amount.toFixed(2)}
                  </td>
                  <td data-label="Source">
                    <span className="tag">{t.sourceType}</span>
                  </td>
                  <td data-label="" className="row-actions">
                    <button type="button" className="btn-quiet btn-small" onClick={() => startEdit(t)}>
                      Edit
                    </button>
                    <button type="button" className="btn-delete btn-small" onClick={() => void deleteTransaction(t)}>
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
              className="btn-quiet btn-small"
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
              className="btn-quiet btn-small"
              onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              disabled={pageInBounds >= totalPages}
            >
              Next &rarr;
            </button>
          </div>
        )}
      </section>
      </main>
    </>
  );
}
