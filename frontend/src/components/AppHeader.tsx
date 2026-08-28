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

/**
 * Shared nav shell for every authenticated screen. Below the mobile breakpoint the inline nav
 * and profile link (desktop-only) are replaced by a hamburger button opening a slide-out
 * drawer with the same links - the flex-wrap it used to fall back to just looked cramped on a
 * narrow screen rather than actually being usable.
 */
export function AppHeader({ active }: { active: ActivePage }) {
  const { email, displayName } = useAuth();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  // Falls back to email only for an account that predates displayName, or a stored session from
  // before that field existed - registration and Google login both set it now.
  const name = displayName || email || "";

  // Closes the drawer on every navigation, not only a click on a link inside it - covers
  // browser back/forward too, where nothing inside the drawer ever gets clicked.
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

        {/* Logout lives on the Settings page now, alongside other account actions, rather than
            sitting in the header as the one thing you could do with your identity. */}
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
