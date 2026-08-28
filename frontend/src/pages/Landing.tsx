import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import "./Landing.css";

/**
 * Public marketing page. No API calls anywhere on this page - everything shown is fixed sample
 * data, clearly marked as such in the footer, so a logged-out visitor never sees a hint of
 * needing to be authenticated to view it. Real behaviour (redaction, deterministic math, the
 * credit disclaimer, free-text categories) is described only where it matches how the
 * authenticated app actually works - see Dashboard.tsx, Budgets.tsx, Credit.tsx.
 */

const formatR = (n: number) => {
  const neg = n < 0;
  const [whole, cents] = Math.abs(n).toFixed(2).split(".");
  return (neg ? "-R" : "R") + whole.replace(/\B(?=(\d{3})+(?!\d))/g, " ") + "." + cents;
};
const formatR0 = (n: number) => "R" + Math.round(Math.abs(n)).toString().replace(/\B(?=(\d{3})+(?!\d))/g, " ");

const HERO_ROWS = [
  { date: "25 Aug", merchant: "Kestrel Digital", category: "Salary", source: "Statement", amount: "+R32 400.00", dc: "CR", credit: true },
  { date: "24 Aug", merchant: "Woolworths Sea Point", category: "Groceries", source: "Statement", amount: "R842.35", dc: "DR", credit: false },
  { date: "24 Aug", merchant: "Uber", category: "Transport", source: "Manual", amount: "R92.00", dc: "DR", credit: false },
  { date: "23 Aug", merchant: "Vodacom", category: "Airtime & data", source: "Statement", amount: "R599.00", dc: "DR", credit: false },
  { date: "22 Aug", merchant: "Netflix", category: "Subscriptions", source: "Statement", amount: "R199.00", dc: "DR", credit: false },
  { date: "21 Aug", merchant: "Nando's Rondebosch", category: "Takeaways", source: "Receipt", amount: "R168.50", dc: "DR", credit: false },
];

type SourceId = "statement" | "receipt" | "cash";

const SOURCES: { id: SourceId; n: string; label: string; desc: string }[] = [
  { id: "statement", n: "01", label: "A bank statement", desc: "PDF. Every transaction in it, read and categorised." },
  { id: "receipt", n: "02", label: "A receipt photo", desc: "JPEG or PNG. Merchant, total and date pulled off the slip." },
  { id: "cash", n: "03", label: "A sentence you type", desc: "For cash, where nothing else leaves a record." },
];

const CATEGORY_KEYWORDS: [RegExp, string][] = [
  [/lunch|dinner|breakfast|coffee|takeaway|nando|kfc|steers|pizza|burger/i, "Takeaways"],
  [/uber|bolt|taxi|fuel|petrol|diesel|parking/i, "Transport"],
  [/groceries|woolworths|checkers|spar|pick ?n ?pay|shoprite/i, "Groceries"],
  [/airtime|data|vodacom|mtn|telkom/i, "Airtime & data"],
];

function parseCashDemo(text: string) {
  const t = text.trim();
  const amountMatch = t.match(/r\s?(\d{1,3}(?:[ ,]\d{3})*(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)/i);
  const amount = amountMatch ? parseFloat(amountMatch[1].replace(/[ ,](?=\d{3})/g, "").replace(",", ".")) : null;
  const method = /\bcard\b/i.test(t) ? "Card" : "Cash";
  const date = /yesterday/i.test(t) ? "Yesterday" : "Today";
  const rest = t
    .replace(amountMatch ? amountMatch[0] : "", " ")
    .replace(/\b(cash|card|today|yesterday|paid|spent|for|on|at)\b/gi, " ")
    .replace(/\s+/g, " ")
    .trim();
  const merchant = rest ? rest.charAt(0).toUpperCase() + rest.slice(1) : "Uncategorised";
  let category = "Uncategorised";
  for (const [re, name] of CATEGORY_KEYWORDS) {
    if (re.test(t)) {
      category = name;
      break;
    }
  }
  return { merchant, amount, method, date, category, ok: amount !== null && rest.length > 0 };
}

const CATEGORY_BARS = [
  { name: "Home & utilities", amount: 6500 },
  { name: "Groceries", amount: 4240 },
  { name: "Transport", amount: 1860 },
  { name: "Takeaways", amount: 1340 },
  { name: "Clothing account", amount: 2310 },
  { name: "Subscriptions", amount: 798 },
  { name: "Personal & health", amount: 780 },
];
const CATEGORY_MAX = Math.max(...CATEGORY_BARS.map((c) => c.amount));

const TREND_MONTHS = ["Mar", "Apr", "May", "Jun", "Jul", "Aug"];
const TREND_POINTS = "10,145.3 126,91.4 242,124 358,23.8 474,81 590,59.5";

const CAL_SPEND: Record<number, number> = {
  1: 0, 2: 318.4, 3: 842.35, 4: 92, 5: 0, 6: 1420, 7: 265.5, 8: 1180.9, 9: 0, 10: 199,
  11: 486.2, 12: 0, 13: 74.5, 14: 2310, 15: 640.1, 16: 0, 17: 355.75, 18: 128, 19: 912.4,
  20: 0, 21: 168.5, 22: 199, 23: 599, 24: 934.35, 25: 0, 26: 447.8, 27: 260, 28: 0,
  29: 1685.2, 30: 0, 31: 0,
};
const CAL_TX: Record<number, { m: string; c: string; a: number }[]> = {
  3: [{ m: "Woolworths Sea Point", c: "Groceries", a: 842.35 }],
  6: [{ m: "City of Cape Town", c: "Utilities", a: 1420 }],
  14: [{ m: "Truworths", c: "Clothing account", a: 2310 }],
  21: [{ m: "Nando's Rondebosch", c: "Takeaways", a: 168.5 }],
  24: [{ m: "Woolworths Sea Point", c: "Groceries", a: 842.35 }, { m: "Uber", c: "Transport", a: 92 }],
  29: [{ m: "Checkers Hyper", c: "Groceries", a: 1685.2 }],
};
const CAL_MAX = Math.max(...Object.values(CAL_SPEND));

const BUDGETS = [
  { name: "Groceries", spent: 4240, limit: 5000 },
  { name: "Takeaways", spent: 1340, limit: 1000 },
  { name: "Transport", spent: 1860, limit: 2400 },
  { name: "Subscriptions", spent: 798, limit: 800 },
];

const CREDIT_ACCOUNTS = [
  { name: "Credit card", bal: 8420, limit: 15000 },
  { name: "Overdraft", bal: 4180, limit: 20000 },
  { name: "Store account", bal: 2310, limit: 6000 },
];

const FAQS = [
  {
    q: "Is FinMe a bank?",
    a: "No. FinMe doesn't hold money, move money or make payments. It reads records of spending you give it and organises them.",
  },
  {
    q: "What happens to a statement I upload?",
    a: "It's parsed to pull out transactions. Account numbers, ID numbers and names are removed from the text before any of it reaches an external model. Exact retention and deletion timelines are still being finalised.",
  },
  {
    q: "What files can I upload?",
    a: "Bank statements as PDF, and receipts as JPEG or PNG. A statement that's a scanned image rather than real text may fail to extract - upload those pages as receipts instead.",
  },
  {
    q: "How do cash purchases work?",
    a: "Type a sentence like “lunch R150 cash today”. FinMe reads the amount, date and payment method from it and creates a transaction you can correct in place.",
  },
  {
    q: "Do I have to use credit tracking?",
    a: "No. It's a separate section you switch on. Nothing on your dashboard depends on it, and leaving it off doesn't leave gaps anywhere else.",
  },
  {
    q: "Can FinMe change my credit score?",
    a: "No. FinMe stores score readings you enter yourself and calculates utilisation from accounts you add. It doesn't contact a bureau and has no effect on your actual score.",
  },
  {
    q: "How should I read the improvement plan?",
    a: "As suggestions ordered by their effect on your utilisation, written from figures FinMe calculated. Following them does not guarantee an increase in your score.",
  },
];

export function Landing() {
  const [source, setSource] = useState<SourceId>("statement");
  const [cashText, setCashText] = useState("lunch R150 cash today");
  const [demoStatus, setDemoStatus] = useState<"idle" | "running" | "done">("idle");
  const [demoPct, setDemoPct] = useState(0);
  const [day, setDay] = useState(21);
  const [openFaq, setOpenFaq] = useState<number | null>(1);
  const [simAccount, setSimAccount] = useState(0);
  const [simBalance, setSimBalance] = useState(3500);
  const intervalRef = useRef<number | null>(null);

  useEffect(() => () => {
    if (intervalRef.current !== null) window.clearInterval(intervalRef.current);
  }, []);

  const runDemo = () => {
    if (intervalRef.current !== null) window.clearInterval(intervalRef.current);
    setDemoStatus("running");
    setDemoPct(0);
    intervalRef.current = window.setInterval(() => {
      setDemoPct((p) => {
        const next = p + 3 + Math.random() * 6;
        if (next >= 100) {
          if (intervalRef.current !== null) window.clearInterval(intervalRef.current);
          setDemoStatus("done");
          return 100;
        }
        return next;
      });
    }, 140);
  };

  const cash = parseCashDemo(cashText);
  const totalLimit = CREDIT_ACCOUNTS.reduce((sum, a) => sum + a.limit, 0);
  const totalBalance = CREDIT_ACCOUNTS.reduce((sum, a) => sum + a.bal, 0);
  const currentUtilPct = (totalBalance / totalLimit) * 100;
  const activeAccount = CREDIT_ACCOUNTS[simAccount];
  const clampedSimBalance = Math.min(simBalance, activeAccount.limit);
  const simulatedTotal = totalBalance - activeAccount.bal + clampedSimBalance;
  const simulatedUtilPct = (simulatedTotal / totalLimit) * 100;
  const utilDelta = simulatedUtilPct - currentUtilPct;

  const dayTotal = CAL_SPEND[day] ?? 0;
  const dayTx = CAL_TX[day] ?? [];

  return (
    <div className="landing">
      <header className="landing-header">
        <a href="#top" className="landing-header__brand">
          Fin<span>Me</span>
        </a>
        <nav className="landing-header__nav">
          <a href="#how-it-works">How it works</a>
          <a href="#spending">Spending</a>
          <a href="#credit">Credit</a>
          <a href="#faq">FAQ</a>
        </nav>
        <div className="landing-header__actions">
          <Link to="/login" className="link-button">
            Sign in
          </Link>
          <Link to="/register">
            <button type="button">Get started</button>
          </Link>
        </div>
      </header>

      <section id="top" className="landing-section landing-hero">
        <h1>
          Eish, where did my money <span>go?</span>
        </h1>
        <p>FinMe helps you track spending, spot patterns and keep an eye on your credit.</p>
        <div className="landing-cta-row">
          <Link to="/register">
            <button type="button">Create your account</button>
          </Link>
          <a href="#how-it-works">
            <button type="button" className="btn-quiet">
              See how it works
            </button>
          </a>
        </div>

        <div className="landing-inner landing-preview">
          <div className="landing-preview__head">
            <strong>august_2026_statement.pdf</strong>
            <span>42 transactions read</span>
          </div>
          <div className="landing-scroll">
            <table className="transaction-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Merchant</th>
                  <th>Category</th>
                  <th>Source</th>
                  <th>Amount</th>
                </tr>
              </thead>
              <tbody>
                {HERO_ROWS.map((row) => (
                  <tr key={row.merchant + row.date}>
                    <td data-label="Date">{row.date}</td>
                    <td data-label="Merchant">{row.merchant}</td>
                    <td data-label="Category">{row.category}</td>
                    <td data-label="Source">
                      <span className="tag">{row.source}</span>
                    </td>
                    <td data-label="Amount" className={row.credit ? "amount-credit" : undefined}>
                      {row.amount}
                      <span style={{ marginLeft: 8, fontFamily: "var(--font-mono)", fontSize: 10, color: "var(--ink-40)" }}>
                        {row.dc}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="landing-preview__foot">
            <span>Showing 6 of 42</span>
            <span>CR = money in &middot; DR = money out</span>
          </div>
        </div>
      </section>

      <section id="how-it-works" className="landing-section landing-section--band">
        <div className="landing-inner">
          <div className="landing-section-head">
            <h2>Three ways in. One record.</h2>
            <p>A statement, a receipt photo, or a sentence you type. They all land in the same ledger, ready to correct.</p>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: "clamp(20px, 3vw, 44px)", alignItems: "start" }}>
            <div className="landing-source-picker">
              {SOURCES.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  className={`landing-source-tab${source === s.id ? " active" : ""}`}
                  aria-pressed={source === s.id}
                  onClick={() => setSource(s.id)}
                >
                  <span className="landing-source-tab__n">{s.n}</span>
                  <span>
                    <span className="landing-source-tab__label">{s.label}</span>
                    <span className="landing-source-tab__desc">{s.desc}</span>
                  </span>
                </button>
              ))}
            </div>

            <div className="landing-demo-card">
              <div className="landing-demo-card__head">
                <span>Input</span>
                <span>{source === "statement" ? "statement.pdf" : source === "receipt" ? "IMG_4471.jpeg" : "typed"}</span>
              </div>
              <div className="landing-demo-card__body">
                {source === "statement" && (
                  <p style={{ margin: 0, fontSize: 14, lineHeight: 1.5, color: "var(--ink-50)" }}>
                    Account numbers, ID numbers and names are stripped from the text before any of it is read by a
                    model.
                  </p>
                )}
                {source === "receipt" && (
                  <>
                    <div className="landing-field-row">
                      <span>Merchant</span>
                      <span>Nando's Rondebosch</span>
                    </div>
                    <div className="landing-field-row">
                      <span>Date</span>
                      <span>21 Aug 2026</span>
                    </div>
                    <div className="landing-field-row">
                      <span>Total</span>
                      <span>R168.50</span>
                    </div>
                  </>
                )}
                {source === "cash" && (
                  <>
                    <label htmlFor="landing-cash" style={{ display: "block", marginBottom: 8, fontFamily: "var(--font-mono)", fontSize: 10, letterSpacing: "0.1em", textTransform: "uppercase", color: "var(--ink-50)" }}>
                      Type what you spent
                    </label>
                    <input
                      id="landing-cash"
                      type="text"
                      value={cashText}
                      onChange={(e) => setCashText(e.target.value)}
                      placeholder="lunch R150 cash today"
                      style={{ width: "100%" }}
                    />
                    <p style={{ margin: "10px 0 0", fontSize: 13, lineHeight: 1.5, color: "var(--ink-50)" }}>
                      Edit the sentence - the result below reads from it live.
                    </p>
                  </>
                )}
              </div>

              <div className="landing-becomes">
                <span />
                <span>becomes</span>
                <span />
              </div>

              <div className="landing-result-card">
                <div className="landing-result-card__head">
                  <span>
                    {source === "statement"
                      ? "Woolworths Sea Point"
                      : source === "receipt"
                        ? "Nando's Rondebosch"
                        : cash.merchant}
                  </span>
                  <span>
                    {source === "statement"
                      ? "R842.35"
                      : source === "receipt"
                        ? "R168.50"
                        : cash.amount === null
                          ? "-"
                          : formatR(cash.amount)}
                  </span>
                </div>
                <div className="landing-result-fields">
                  {(source === "statement"
                    ? [{ k: "Date", v: "24 Aug 2026" }, { k: "Category", v: "Groceries" }, { k: "Method", v: "Card" }]
                    : source === "receipt"
                      ? [{ k: "Date", v: "21 Aug 2026" }, { k: "Category", v: "Takeaways" }, { k: "Method", v: "Card" }]
                      : [{ k: "Date", v: cash.date }, { k: "Category", v: cash.category }, { k: "Method", v: cash.method }]
                  ).map((f) => (
                    <div key={f.k} style={{ display: "flex", flexDirection: "column", gap: 3 }}>
                      <span style={{ fontFamily: "var(--font-mono)", fontSize: 9.5, letterSpacing: "0.1em", textTransform: "uppercase", color: "var(--ink-40)" }}>
                        {f.k}
                      </span>
                      <span style={{ fontSize: 14.5, fontWeight: 600, color: "var(--ink)" }}>{f.v}</span>
                    </div>
                  ))}
                </div>
              </div>
              <p style={{ margin: "0 18px 18px", fontSize: 13, lineHeight: 1.5, color: "var(--ink-50)" }}>
                Every field stays editable. Category is free text with suggestions, not a fixed list.
              </p>
            </div>
          </div>
        </div>
      </section>

      <section className="landing-section">
        <div className="landing-inner landing-inner--narrow">
          <span className="landing-eyebrow">Long-running work</span>
          <h2 style={{ fontSize: "clamp(28px, 4vw, 44px)", lineHeight: 1.05, margin: "0 0 16px" }}>
            A statement can take minutes. You shouldn't have to watch it.
          </h2>
          <p style={{ margin: "0 0 30px", fontSize: 16, lineHeight: 1.6, color: "var(--ink-50)" }}>
            Extraction runs in the background and reports where it actually is. Leave the page, use the rest of
            FinMe, come back to a finished ledger.
          </p>

          <div className="landing-progress-card">
            <div className="landing-progress-card__head">
              <span style={{ fontFamily: "var(--font-mono)", fontSize: 13, color: "var(--ink)" }}>
                august_2026_statement.pdf
              </span>
              <span
                className="landing-chip"
                style={{
                  background: demoStatus === "running" ? "#eaf0e4" : demoStatus === "done" ? "var(--forest-wash)" : "var(--stone)",
                  color: demoStatus === "running" ? "#4a6b39" : demoStatus === "done" ? "var(--forest)" : "var(--ink-40)",
                }}
              >
                <span
                  className="landing-chip__dot"
                  style={{
                    background: "currentColor",
                    animation: demoStatus === "running" ? "finme-blink 1.1s steps(1) infinite" : "none",
                  }}
                />
                {demoStatus === "running" ? "Processing" : demoStatus === "done" ? "Complete" : "Waiting"}
              </span>
            </div>
            <div className="landing-progress-card__body">
              <div className="landing-progress-label-row">
                <span>
                  {demoStatus === "idle"
                    ? "Ready to upload"
                    : demoStatus === "done"
                      ? "Done - 42 transactions read"
                      : "Extracting your transactions..."}
                </span>
                <span className="landing-progress-pct">{demoStatus === "idle" ? "-" : `${Math.round(demoPct)}%`}</span>
              </div>
              <div className="landing-progress-track">
                <div className="landing-progress-fill" style={{ width: `${demoStatus === "idle" ? 0 : demoPct}%` }} />
              </div>
              {demoStatus === "done" && (
                <p style={{ margin: "16px 0 0", fontSize: 15, color: "var(--forest)", fontWeight: 700 }}>
                  42 transactions added. <span style={{ color: "var(--ink-50)", fontWeight: 400 }}>6 need a category.</span>
                </p>
              )}
              <button
                type="button"
                style={{ marginTop: 20 }}
                onClick={runDemo}
                disabled={demoStatus === "running"}
              >
                {demoStatus === "running" ? "Uploading..." : demoStatus === "done" ? "Run it again" : "See it in action"}
              </button>
            </div>
          </div>
        </div>
      </section>

      <section id="spending" className="landing-section landing-section--band">
        <div className="landing-inner" style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(310px, 1fr))", gap: "clamp(32px, 5vw, 72px)", alignItems: "start" }}>
          <div>
            <span className="landing-eyebrow">Spent in August</span>
            <p className="landing-figure">
              R17 828<span>.40</span>
            </p>
            <p style={{ margin: "20px 0 0", fontSize: 16, lineHeight: 1.55, color: "var(--ink-50)", maxWidth: "34ch" }}>
              R908 more than July, almost all of it groceries and one clothing account payment.
            </p>

            <div style={{ marginTop: 36, paddingTop: 24, borderTop: "1px solid var(--line)" }}>
              <span className="landing-eyebrow" style={{ marginBottom: 12 }}>
                What FinMe noticed
              </span>
              <ol style={{ margin: 0, padding: 0, listStyle: "none", display: "flex", flexDirection: "column", gap: 12 }}>
                {[
                  "Groceries have run above R4 000 for three months straight - R5 000 is a more honest limit than the R3 500 set in May.",
                  "Takeaways went R340 past its limit, spread over nine orders under R200 each.",
                  "Four subscriptions renew within three days of each other, right before salary lands.",
                ].map((text, i) => (
                  <li key={i} style={{ display: "flex", gap: 12, fontSize: 15, lineHeight: 1.5, color: "var(--ink-70)" }}>
                    <span style={{ fontFamily: "var(--font-mono)", fontSize: 11, color: "var(--sage)", paddingTop: 3 }}>
                      {String(i + 1).padStart(2, "0")}
                    </span>
                    <span>{text}</span>
                  </li>
                ))}
              </ol>
              <p style={{ margin: "16px 0 0", fontSize: 13, color: "var(--ink-40)" }}>
                Written from figures FinMe calculated. Not advice.
              </p>
            </div>
          </div>

          <div>
            <span className="landing-eyebrow">By category</span>
            <div>
              {CATEGORY_BARS.map((c) => (
                <div className="landing-cat-row" key={c.name}>
                  <span style={{ fontSize: 14.5, fontWeight: 600, color: "var(--ink)" }}>{c.name}</span>
                  <span className="landing-cat-row__track">
                    <span className="landing-cat-row__fill" style={{ width: `${(c.amount / CATEGORY_MAX) * 100}%` }} />
                  </span>
                  <span style={{ fontFamily: "var(--font-mono)", fontSize: 13, color: "var(--ink-50)", textAlign: "right", whiteSpace: "nowrap" }}>
                    {formatR0(c.amount)}
                  </span>
                </div>
              ))}
            </div>

            <span className="landing-eyebrow" style={{ marginTop: 36, display: "block" }}>
              Six-month trend
            </span>
            <div className="landing-trend-card">
              <svg viewBox="0 0 600 170" role="img" aria-label="Monthly spend, March to August" style={{ display: "block", width: "100%", height: "auto" }}>
                <line x1="0" y1="150" x2="600" y2="150" stroke="var(--line)" strokeWidth={1} />
                <line x1="0" y1="86" x2="600" y2="86" stroke="var(--stone)" strokeWidth={1} strokeDasharray="3 5" />
                <polyline points={TREND_POINTS} fill="none" stroke="var(--forest)" strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" />
                {TREND_POINTS.split(" ").map((pt, i, arr) => {
                  const [x, y] = pt.split(",").map(Number);
                  const isLast = i === arr.length - 1;
                  return <circle key={pt} cx={x} cy={y} r={isLast ? 5.5 : 4} fill={isLast ? "var(--forest)" : "var(--paper)"} stroke="var(--forest)" strokeWidth={2.5} />;
                })}
              </svg>
              <div className="landing-trend-axis">
                {TREND_MONTHS.map((m, i) => (
                  <span key={m} style={i === TREND_MONTHS.length - 1 ? { color: "var(--forest)", fontWeight: 700 } : undefined}>
                    {m}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="landing-section landing-section--dark">
        <div className="landing-inner">
          <div className="landing-section-head">
            <div>
              <span className="landing-eyebrow">Calendar</span>
              <h2 style={{ color: "#ffffff" }}>Your spending, seen in time.</h2>
            </div>
            <p style={{ color: "#c6d2bc" }}>
              Days you spent nothing are shown, not skipped. The bar under each date carries the size of the day.
            </p>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: "clamp(24px, 3vw, 44px)", alignItems: "start" }}>
            <div>
              <div className="landing-cal-head">
                <h3>August 2026</h3>
              </div>
              <div className="landing-cal-grid">
                {["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((w) => (
                  <span className="landing-cal-weekday" key={w}>
                    {w}
                  </span>
                ))}
              </div>
              <div className="landing-cal-days">
                {Array.from({ length: 5 }).map((_, i) => (
                  <span key={`b${i}`} />
                ))}
                {Array.from({ length: 31 }, (_, i) => i + 1).map((n) => {
                  const amt = CAL_SPEND[n] ?? 0;
                  const ratio = amt / CAL_MAX;
                  return (
                    <button
                      key={n}
                      type="button"
                      className={`landing-cal-day${day === n ? " active" : ""}`}
                      onClick={() => setDay(n)}
                      aria-pressed={day === n}
                    >
                      <span className="landing-cal-day__n">{n}</span>
                      <span className="landing-cal-day__amt">{amt === 0 ? "-" : formatR0(amt)}</span>
                      <span className="landing-cal-day__bar">
                        <span style={{ width: `${ratio * 100}%` }} />
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="landing-cal-detail">
              <div className="landing-cal-detail__head">
                <span>{day} August 2026</span>
                <span>{dayTotal === 0 ? "R0.00" : formatR(dayTotal)}</span>
              </div>
              {dayTx.length > 0 ? (
                <div>
                  {dayTx.map((t) => (
                    <div className="landing-cal-tx-row" key={t.m}>
                      <span>
                        <span className="landing-cal-tx-row__name">{t.m}</span>
                        <span className="landing-cal-tx-row__cat">{t.c}</span>
                      </span>
                      <span className="landing-cal-tx-row__amt">{formatR(t.a)}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="landing-cal-empty">
                  <p>Nothing spent this day.</p>
                  <p>A zero-spend day is a real result, not missing data.</p>
                </div>
              )}
            </div>
          </div>
        </div>
      </section>

      <section className="landing-section">
        <div className="landing-inner">
          <div className="landing-section-head">
            <h2>Budgets are context, not a cage.</h2>
            <p>Set a monthly limit per category. FinMe shows the gap in both directions - and when you're over, by how much.</p>
          </div>

          <div>
            {BUDGETS.map((b) => {
              const pct = b.spent / b.limit;
              const over = b.spent > b.limit;
              return (
                <div className="landing-budget-row" key={b.name}>
                  <div className="landing-budget-row__name">
                    <strong>{b.name}</strong>
                    <span>{Math.round(pct * 100)}%</span>
                  </div>
                  <div>
                    <span className="landing-cat-row__track" style={{ height: 16 }}>
                      <span
                        className="landing-cat-row__fill"
                        style={{ width: `${Math.min(100, pct * 100)}%`, background: over ? "var(--clay)" : "var(--forest)" }}
                      />
                    </span>
                    <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "space-between", gap: "4px 16px", marginTop: 8, fontFamily: "var(--font-mono)", fontSize: 12.5, color: "var(--ink-50)" }}>
                      <span>
                        {formatR(b.spent)} of {formatR0(b.limit)}
                      </span>
                      <span style={{ fontWeight: 700, color: over ? "var(--clay)" : "var(--ink)" }}>
                        {over ? `${formatR(b.spent - b.limit)} over` : `${formatR(b.limit - b.spent)} left`}
                      </span>
                    </div>
                  </div>
                </div>
              );
            })}
            <div style={{ borderTop: "1px solid var(--line)" }} />
          </div>
        </div>
      </section>

      <section id="credit" className="landing-section landing-section--band">
        <div className="landing-inner">
          <span className="landing-eyebrow">Optional layer</span>
          <div className="landing-section-head">
            <h2>Switch on credit when you want it.</h2>
            <p>Record your score readings, list your accounts, and see what utilisation is actually doing. Never opening this section costs you nothing elsewhere.</p>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(290px, 1fr))", gap: "clamp(24px, 3vw, 44px)", alignItems: "start" }}>
            <div>
              <span className="landing-eyebrow">Latest reading &middot; 12 Aug 2026</span>
              <p style={{ margin: 0, display: "flex", alignItems: "baseline", gap: 4, fontWeight: 800, letterSpacing: "-0.05em", lineHeight: 0.86, color: "var(--ink)" }}>
                <span style={{ fontSize: "clamp(64px, 10vw, 108px)", fontVariantNumeric: "tabular-nums" }}>648</span>
                <span style={{ fontSize: "clamp(22px, 3vw, 32px)", color: "var(--ink-30)" }}>/&nbsp;740</span>
              </p>
              <p style={{ display: "inline-flex", alignItems: "center", gap: 8, margin: "20px 0 0", padding: "7px 13px", border: "1.5px solid var(--forest)", borderRadius: 20, fontSize: 14.5, fontWeight: 600, color: "var(--forest)" }}>
                Up 22 points over 81 days
              </p>
              <p style={{ margin: "20px 0 0", fontSize: 15, lineHeight: 1.55, color: "var(--ink-50)", maxWidth: "38ch" }}>
                You enter each reading yourself - FinMe doesn't contact a bureau, and it can't change your score.
              </p>
            </div>

            <div className="landing-sim-card">
              <div className="landing-sim-card__head">
                <h3 style={{ margin: "0 0 4px", fontSize: 18 }}>What if I paid this down?</h3>
                <p style={{ margin: 0, fontSize: 14, lineHeight: 1.5, color: "var(--ink-50)" }}>
                  A utilisation calculation only. It is not a prediction of your score.
                </p>
              </div>
              <div className="landing-sim-card__body">
                <div className="landing-account-picker">
                  {CREDIT_ACCOUNTS.map((a, i) => (
                    <button
                      key={a.name}
                      type="button"
                      className={`landing-account-chip${simAccount === i ? " active" : ""}`}
                      onClick={() => {
                        setSimAccount(i);
                        setSimBalance(Math.round((a.bal * 0.42) / 100) * 100);
                      }}
                      aria-pressed={simAccount === i}
                    >
                      {a.name}
                    </button>
                  ))}
                </div>

                <label className="landing-sim-range-label" htmlFor="landing-sim">
                  <span>Hypothetical balance</span>
                  <span>{formatR0(clampedSimBalance)}</span>
                </label>
                <input
                  id="landing-sim"
                  type="range"
                  min={0}
                  max={activeAccount.limit}
                  step={100}
                  value={clampedSimBalance}
                  onChange={(e) => setSimBalance(Number(e.target.value))}
                />
                <div className="landing-sim-range-ends">
                  <span>R0</span>
                  <span>{formatR0(activeAccount.limit)} limit</span>
                </div>

                <div className="landing-sim-result">
                  <div className="landing-sim-result__col">
                    <span>Now</span>
                    <span style={{ color: "var(--ink-30)" }}>{currentUtilPct.toFixed(1)}%</span>
                  </div>
                  <span className="landing-sim-arrow">&rarr;</span>
                  <div className="landing-sim-result__col">
                    <span style={{ color: "var(--forest)" }}>Simulated</span>
                    <span style={{ color: utilDelta < -0.05 ? "var(--forest)" : utilDelta > 0.05 ? "var(--clay)" : "var(--ink)" }}>
                      {simulatedUtilPct.toFixed(1)}%
                    </span>
                  </div>
                  <span
                    className="landing-sim-delta"
                    style={{ color: utilDelta < -0.05 ? "var(--forest)" : utilDelta > 0.05 ? "var(--clay)" : "var(--ink)" }}
                  >
                    {Math.abs(utilDelta) < 0.05
                      ? "No change from today"
                      : `${utilDelta < 0 ? "Down" : "Up"} ${Math.abs(utilDelta).toFixed(1)} points of utilisation`}
                  </span>
                </div>
              </div>
              <p className="credit-disclaimer" style={{ margin: 0, borderRadius: 0, borderLeft: "none", borderTop: "1px solid var(--clay-border)" }}>
                <strong>Following these suggestions does not guarantee an increase in your credit score.</strong>{" "}
                Utilisation is one input among many, and bureaus weigh them differently.
              </p>
            </div>
          </div>
        </div>
      </section>

      <section id="faq" className="landing-section landing-section--band">
        <div className="landing-inner landing-inner--narrow">
          <h2 style={{ marginBottom: 32 }}>Questions people ask.</h2>
          <div>
            {FAQS.map((f, i) => (
              <div className="landing-faq-item" key={f.q}>
                <button type="button" aria-expanded={openFaq === i} onClick={() => setOpenFaq(openFaq === i ? null : i)}>
                  <span>{f.q}</span>
                  <span aria-hidden="true">{openFaq === i ? "−" : "+"}</span>
                </button>
                {openFaq === i && <p className="landing-faq-answer">{f.a}</p>}
              </div>
            ))}
            <div style={{ borderTop: "1px solid var(--line-strong)" }} />
          </div>
        </div>
      </section>

      <section className="landing-final-cta">
        <h2>
          Start with one <span style={{ color: "var(--sage)" }}>statement.</span>
        </h2>
        <p>Upload last month's PDF and see the whole month sorted. Credit tracking stays switched off until you want it.</p>
        <div className="landing-cta-row">
          <Link to="/register">
            <button type="button">Create your account</button>
          </Link>
          <Link to="/login">
            <button type="button" className="btn-quiet">
              Sign in
            </button>
          </Link>
        </div>
        <p style={{ marginTop: 20, fontSize: 13.5, color: "var(--ink-40)" }}>Free while FinMe is in development. No card.</p>
      </section>

      <footer className="landing-footer">
        <div className="landing-inner">
          <div className="landing-footer__top">
            <div>
              <p className="landing-footer__brand">
                Fin<span>Me</span>
              </p>
              <p>A personal finance and credit-health tracker. Not a bank, and it never holds or moves money.</p>
            </div>
            <div>
              <h4>Product</h4>
              <ul>
                <li><a href="#how-it-works">How it works</a></li>
                <li><a href="#spending">Spending</a></li>
                <li><a href="#credit">Credit</a></li>
                <li><a href="#faq">FAQ</a></li>
              </ul>
            </div>
            <div>
              <h4>Account</h4>
              <ul>
                <li><Link to="/login">Sign in</Link></li>
                <li><Link to="/register">Create account</Link></li>
              </ul>
            </div>
          </div>
          <div className="landing-footer__bottom">
            <span>&copy; 2026 FinMe. Built in South Africa. All amounts in ZAR.</span>
            <span style={{ fontFamily: "var(--font-mono)", fontSize: 11 }}>Sample data throughout - fictional</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
