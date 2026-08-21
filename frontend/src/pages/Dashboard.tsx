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
import { SpendMap, type SpendLocation } from "../components/SpendMap";

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
  status: string;
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
  locations: SpendLocation[];
}

const ACCENT = "#aa3bff";

export function Dashboard() {
  const { token, email, logout } = useAuth();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
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
  }, [token]);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setError(null);
    setUploading(true);
    try {
      const form = new FormData();
      form.append("file", file);
      await apiPostForm<BankStatementResponse>("/api/statements", form, token);
      await loadDashboard();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Statement upload failed");
    } finally {
      setUploading(false);
      event.target.value = "";
    }
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
          {uploading ? "Uploading..." : "Upload bank statement (PDF)"}
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

          <div className="chart-card chart-card--wide">
            <h2>Spend map</h2>
            <SpendMap locations={summary.locations} />
          </div>
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
