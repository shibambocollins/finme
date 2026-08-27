import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

type ActivePage = "dashboard" | "calendar" | "budgets" | "credit";

const NAV_ITEMS: { to: string; label: string; key: ActivePage }[] = [
  { to: "/dashboard", label: "Dashboard", key: "dashboard" },
  { to: "/calendar", label: "Calendar", key: "calendar" },
  { to: "/budgets", label: "Budgets", key: "budgets" },
  { to: "/credit", label: "Credit", key: "credit" },
];

/**
 * Shared nav shell for every authenticated screen - previously each page (Calendar, Budgets,
 * Credit) rolled its own ad hoc "<h1> + back link" header while only Dashboard had the real
 * nav, sign-out and identity display. One header keeps all four screens navigable from each
 * other and keeps the identity/logout affordance in the same place everywhere.
 */
export function AppHeader({ active }: { active: ActivePage }) {
  const { email, displayName, logout } = useAuth();

  return (
    <header className="app-header">
      <Link to="/dashboard" className="app-header__brand">
        Fin<span>Me</span>
      </Link>
      <nav className="app-header__nav">
        {NAV_ITEMS.map((item) => (
          <Link key={item.key} to={item.to} className={active === item.key ? "active" : undefined}>
            {item.label}
          </Link>
        ))}
      </nav>
      <div className="app-header__user">
        {/* Falls back to email only for an account that predates displayName, or a stored
            session from before that field existed - registration and Google login both set it
            now. */}
        <span className="app-header__name">{displayName || email}</span>
        <button type="button" className="btn-quiet btn-small" onClick={logout}>
          Log out
        </button>
      </div>
    </header>
  );
}
