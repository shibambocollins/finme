import { useCallback, useEffect, useState, type ChangeEvent } from "react";
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
import { useAuth } from "../auth/AuthContext";
import { apiGet, apiPostForm, ApiError } from "../api/client";

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
  direction: "DEBIT" | "CREDIT";
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

const ACCENT = "#aa3bff";

export function Dashboard() {
  const { token, email, logout } = useAuth();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [insights, setInsights] = useState<RecommendationsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<string | null>(null);
  const [uploadingReceipt, setUploadingReceipt] = useState(false);
  const [error, setError] = useState<string | null>(null);

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

  /**
   * Polls the statement until it leaves PROCESSING. The interval is deliberately unhurried:
   * extraction spends most of its time waiting out provider rate limits, so polling faster
   * would only add requests without learning anything sooner.
   */
  const pollUntilSettled = async (statementId: number): Promise<BankStatementResponse> => {
    const deadline = Date.now() + 10 * 60 * 1000;
    while (Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, 2000));
      const statement = await apiGet<BankStatementResponse>(`/api/statements/${statementId}`, token);
      if (statement.status !== "PROCESSING") {
        return statement;
      }
      setUploadProgress(
        statement.totalChunks
          ? `Extracting... part ${statement.processedChunks ?? 0} of ${statement.totalChunks}`
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

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h1>FinMe</h1>
        <div>
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
        {loading ? (
          <p>Loading...</p>
        ) : transactions.length === 0 ? (
          <p>No transactions yet - upload a statement to get started.</p>
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
              </tr>
            </thead>
            <tbody>
              {transactions.map((t) => (
                <tr key={t.id}>
                  <td>{t.date}</td>
                  <td>{t.merchant}</td>
                  <td>{t.category ?? "-"}</td>
                  <td>{t.description ?? "-"}</td>
                  <td className={t.direction === "CREDIT" ? "amount-credit" : undefined}>
                    {t.direction === "CREDIT" ? "+" : ""}R{t.amount.toFixed(2)}
                  </td>
                  <td>{t.sourceType}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
