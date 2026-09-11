import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { apiGet, ApiError } from "../api/client";
import { AppHeader } from "../components/AppHeader";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

interface CalendarDay {
  date: string;
  total: number;
}

interface CalendarResponse {
  month: string;
  days: CalendarDay[];
}

interface DayTransaction {
  id: number;
  merchant: string;
  amount: number;
  direction: "DEBIT" | "CREDIT";
  category: string | null;
}

const WEEKDAY_LABELS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function shiftMonth(month: string, delta: number): string {
  const [year, m] = month.split("-").map(Number);
  const shifted = new Date(year, m - 1 + delta, 1);
  return `${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, "0")}`;
}

function monthLabel(month: string): string {
  const [year, m] = month.split("-").map(Number);
  return new Date(year, m - 1, 1).toLocaleDateString(undefined, { month: "long", year: "numeric" });
}

export function CalendarPage() {
  useDocumentTitle("Calendar | FinMe");
  const { token } = useAuth();
  const [month, setMonth] = useState(currentMonth());
  const [calendar, setCalendar] = useState<CalendarResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [dayTransactions, setDayTransactions] = useState<DayTransaction[]>([]);
  const [loadingDay, setLoadingDay] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setSelectedDate(null);
    try {
      setCalendar(await apiGet<CalendarResponse>(`/api/dashboard/calendar?month=${month}`, token));
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load the calendar");
    } finally {
      setLoading(false);
    }
  }, [token, month]);

  useEffect(() => {
    void load();
  }, [load]);

  // Optional-chains days too, not just calendar - a response missing the array threw here
  // and took the whole page down to a blank screen rather than an error message.
  const days = useMemo(() => calendar?.days ?? [], [calendar]);

  const leadingBlanks = useMemo(() => {
    if (!days.length) return 0;
    const [year, m, d] = days[0].date.split("-").map(Number);
    return new Date(year, m - 1, d).getDay();
  }, [days]);

  const openDay = async (date: string) => {
    setSelectedDate(date);
    setLoadingDay(true);
    try {
      setDayTransactions(await apiGet<DayTransaction[]>(`/api/transactions?from=${date}&to=${date}`, token));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load that day's transactions");
    } finally {
      setLoadingDay(false);
    }
  };

  const maxDaySpend = useMemo(() => Math.max(1, ...days.map((d) => d.total), 0), [days]);

  // Derived from the days already fetched for the grid - no second request, and it can
  // never disagree with what the squares show.
  const monthSummary = useMemo(() => {
    if (!days.length) return null;
    const spentDays = days.filter((d) => d.total > 0);
    const total = spentDays.reduce((sum, d) => sum + d.total, 0);
    const busiest = spentDays.reduce<CalendarDay | null>(
      (top, d) => (!top || d.total > top.total ? d : top),
      null
    );
    return {
      total,
      spentDayCount: spentDays.length,
      zeroDayCount: days.length - spentDays.length,
      // Across every day in the month, zero-spend days included - this is the per-day
      // burn rate, so the no-spend days have to count or it is not an average at all.
      averagePerDay: total / days.length,
      busiest,
    };
  }, [days]);

  // Category split for the open day - the transactions are already loaded, this is just
  // a different cut of them.
  const dayBreakdown = useMemo(() => {
    const debits = dayTransactions.filter((t) => t.direction === "DEBIT");
    if (!debits.length) return null;
    const byCategory = new Map<string, number>();
    for (const t of debits) {
      const key = t.category ?? "Uncategorized";
      byCategory.set(key, (byCategory.get(key) ?? 0) + t.amount);
    }
    const total = debits.reduce((sum, t) => sum + t.amount, 0);
    return {
      total,
      categories: [...byCategory.entries()]
        .map(([name, amount]) => ({ name, amount, share: total ? (amount / total) * 100 : 0 }))
        .sort((a, b) => b.amount - a.amount),
    };
  }, [dayTransactions]);

  return (
    <>
      <AppHeader active="calendar" />
      <main className="page page--wide" id="main-content">
        <h1>Calendar</h1>

      {error && <p className="form-error">{error}</p>}

      <div className="calendar-nav">
        <button type="button" className="btn-icon" onClick={() => setMonth((m) => shiftMonth(m, -1))} disabled={loading}>
          &larr;
        </button>
        <h2>{monthLabel(month)}</h2>
        <button type="button" className="btn-icon" onClick={() => setMonth((m) => shiftMonth(m, 1))} disabled={loading}>
          &rarr;
        </button>
      </div>

      {loading || !calendar ? (
        <p>Loading...</p>
      ) : (
        <div className="calendar-layout">
          <div className="calendar-grid">
            {WEEKDAY_LABELS.map((label) => (
              <div key={label} className="calendar-weekday">
                {label}
              </div>
            ))}
            {Array.from({ length: leadingBlanks }).map((_, i) => (
              <div key={`blank-${i}`} className="calendar-day calendar-day--blank" />
            ))}
            {days.map((day) => {
              const dayNumber = Number(day.date.split("-")[2]);
              const intensity = day.total > 0 ? Math.min(0.32, Math.max(0.08, (day.total / maxDaySpend) * 0.32)) : 0;
              return (
                <button
                  key={day.date}
                  type="button"
                  className={`calendar-day${selectedDate === day.date ? " calendar-day--selected" : ""}`}
                  style={day.total > 0 ? { background: `rgba(18, 80, 58, ${intensity})` } : undefined}
                  onClick={() => void openDay(day.date)}
                >
                  <span className="calendar-day-number">{dayNumber}</span>
                  <span className="calendar-day-total">{day.total > 0 ? `R${day.total.toFixed(0)}` : "-"}</span>
                </button>
              );
            })}
          </div>

          <aside className="calendar-side">
            {monthSummary && (
              <section className="calendar-panel">
                <h2 className="calendar-panel-title">This month</h2>
                <dl className="calendar-stats">
                  <div>
                    <dt>Total spent</dt>
                    <dd className="calendar-stat-lead">R{monthSummary.total.toFixed(2)}</dd>
                  </div>
                  <div>
                    <dt>Days with spending</dt>
                    <dd>{monthSummary.spentDayCount}</dd>
                  </div>
                  <div>
                    <dt>No-spend days</dt>
                    <dd>{monthSummary.zeroDayCount}</dd>
                  </div>
                  <div>
                    <dt>Average per day</dt>
                    <dd>R{monthSummary.averagePerDay.toFixed(2)}</dd>
                  </div>
                  {monthSummary.busiest && (
                    <div>
                      <dt>Busiest day</dt>
                      <dd>
                        {Number(monthSummary.busiest.date.split("-")[2])} - R
                        {monthSummary.busiest.total.toFixed(2)}
                      </dd>
                    </div>
                  )}
                </dl>
              </section>
            )}

            <section className="calendar-panel">
              <h2 className="calendar-panel-title">
                {selectedDate ? selectedDate : "Day detail"}
              </h2>
              {!selectedDate ? (
                <p className="calendar-panel-hint">Select a day to see what was spent.</p>
              ) : loadingDay ? (
                <p className="calendar-panel-hint">Loading...</p>
              ) : dayTransactions.length === 0 ? (
                <p className="calendar-panel-hint">
                  Nothing spent this day. A zero-spend day is a real result, not missing data.
                </p>
              ) : (
                <ul className="calendar-day-list">
                  {dayTransactions.map((t) => (
                    <li key={t.id}>
                      <span className="calendar-day-list-merchant">{t.merchant}</span>
                      <span className="calendar-day-list-meta">{t.category ?? "Uncategorized"}</span>
                      <span className={t.direction === "CREDIT" ? "amount-credit" : undefined}>
                        {t.direction === "CREDIT" ? "+" : ""}R{t.amount.toFixed(2)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {dayBreakdown && (
              <section className="calendar-panel">
                <h2 className="calendar-panel-title">Where it went</h2>
                <p className="calendar-breakdown-total">R{dayBreakdown.total.toFixed(2)}</p>
                <ul className="calendar-breakdown">
                  {dayBreakdown.categories.map((c) => (
                    <li key={c.name}>
                      <div className="calendar-breakdown-row">
                        <span className="calendar-breakdown-name">{c.name}</span>
                        <span className="calendar-breakdown-amount">R{c.amount.toFixed(2)}</span>
                      </div>
                      <div
                        className="calendar-breakdown-bar"
                        role="img"
                        aria-label={`${c.name}: ${c.share.toFixed(0)} percent of the day`}
                      >
                        <span style={{ width: `${c.share}%` }} />
                      </div>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </aside>
        </div>
      )}
      </main>
    </>
  );
}
