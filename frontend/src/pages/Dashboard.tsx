import { useCallback, useEffect, useState, type ChangeEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import { apiGet, apiPostForm, ApiError } from "../api/client";

interface Transaction {
  id: number;
  sourceType: string;
  date: string;
  merchant: string;
  amount: number;
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

export function Dashboard() {
  const { token, email, logout } = useAuth();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadTransactions = useCallback(async () => {
    setLoading(true);
    try {
      const data = await apiGet<Transaction[]>("/api/transactions", token);
      setTransactions(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load transactions");
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void loadTransactions();
  }, [loadTransactions]);

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setError(null);
    setUploading(true);
    try {
      const form = new FormData();
      form.append("file", file);
      await apiPostForm<BankStatementResponse>("/api/statements", form, token);
      await loadTransactions();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Statement upload failed");
    } finally {
      setUploading(false);
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
        {error && <p className="form-error">{error}</p>}
      </section>

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
                  <td>R{t.amount.toFixed(2)}</td>
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
