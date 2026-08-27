import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import { apiPostJson } from "../api/client";

interface AuthResponse {
  token: string;
  email: string;
  displayName: string;
}

interface AuthContextValue {
  token: string | null;
  email: string | null;
  displayName: string | null;
  login: (email: string, password: string) => Promise<void>;
  completeOAuthLogin: (token: string, email: string, displayName: string | null) => void;
  logout: () => void;
}

const STORAGE_KEY = "finme.auth";

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function readStoredAuth(): { token: string; email: string; displayName: string | null } | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as { token: string; email: string; displayName: string | null };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const stored = readStoredAuth();
  const [token, setToken] = useState<string | null>(stored?.token ?? null);
  const [email, setEmail] = useState<string | null>(stored?.email ?? null);
  const [displayName, setDisplayName] = useState<string | null>(stored?.displayName ?? null);

  const applyAuth = (auth: { token: string; email: string; displayName: string | null }) => {
    setToken(auth.token);
    setEmail(auth.email);
    setDisplayName(auth.displayName);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
  };

  const login = async (loginEmail: string, password: string) => {
    const auth = await apiPostJson<AuthResponse>("/api/auth/login", { email: loginEmail, password });
    applyAuth(auth);
  };

  const completeOAuthLogin = (oauthToken: string, oauthEmail: string, oauthDisplayName: string | null) => {
    applyAuth({ token: oauthToken, email: oauthEmail, displayName: oauthDisplayName });
  };

  const logout = () => {
    setToken(null);
    setEmail(null);
    setDisplayName(null);
    localStorage.removeItem(STORAGE_KEY);
  };

  const value = useMemo(
    () => ({ token, email, displayName, login, completeOAuthLogin, logout }),
    [token, email, displayName],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
