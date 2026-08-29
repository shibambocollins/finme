import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

type ActivePage = "dashboard" | "calendar" | "budgets" | "credit" | "settings";

const NAV_ITEMS: { to: string; label: string; key: ActivePage }[] = [
  { to: "/dashboard", label: "Dashboard", key: "dashboard" },
  { to: "/calendar", label: "Calendar", key: "calendar" },
  { to: "/budgets", label: "Budgets", key: "budgets" },
  { to: "/credit", label: "Credit", key: "credit" },
];

export function AppHeader({ active }: { active: ActivePage }) {
  const { email, displayName } = useAuth();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const name = displayName || email || "";

  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!menuOpen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") setMenuOpen(false);
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [menuOpen]);

  return (
    <>
      <a href="#main-content" className="skip-link">
        Skip to content
      </a>

      <header className="app-header">
        <Link to="/dashboard" className="app-header__brand">
          Fin<span>Me</span>
        </Link>

        <nav className="app-header__nav app-header__nav--desktop">
          {NAV_ITEMS.map((item) => (
            <Link key={item.key} to={item.to} className={active === item.key ? "active" : undefined}>
              {item.label}
            </Link>
          ))}
        </nav>

        <Link
          to="/settings"
          className={`app-header__profile app-header__profile--desktop${active === "settings" ? " active" : ""}`}
        >
          <span className="app-header__avatar">{name.charAt(0).toUpperCase()}</span>
          <span className="app-header__name">{name}</span>
        </Link>

        <button
          type="button"
          className="app-header__menu-btn"
          aria-label={menuOpen ? "Close menu" : "Open menu"}
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
        >
          <span />
          <span />
          <span />
        </button>
      </header>

      <div className={`app-header__backdrop${menuOpen ? " open" : ""}`} onClick={() => setMenuOpen(false)} />
      <nav className={`app-header__drawer${menuOpen ? " open" : ""}`} aria-label="Mobile navigation" aria-hidden={!menuOpen}>
        <Link to="/settings" className={`app-header__profile${active === "settings" ? " active" : ""}`}>
          <span className="app-header__avatar">{name.charAt(0).toUpperCase()}</span>
          <span className="app-header__name">{name}</span>
        </Link>
        <div className="app-header__drawer-links">
          {NAV_ITEMS.map((item) => (
            <Link key={item.key} to={item.to} className={active === item.key ? "active" : undefined}>
              {item.label}
            </Link>
          ))}
        </div>
      </nav>
    </>
  );
}
