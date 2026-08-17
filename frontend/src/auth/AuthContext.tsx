import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import { apiPostJson } from "../api/client";

interface AuthResponse {
  token: string;
  email: string;
}

interface AuthContextValue {
  token: string | null;
  email: string | null;
  register: (email: string, password: string) => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const STORAGE_KEY = "finme.auth";

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function readStoredAuth(): { token: string; email: string } | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as { token: string; email: string };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const stored = readStoredAuth();
  const [token, setToken] = useState<string | null>(stored?.token ?? null);
  const [email, setEmail] = useState<string | null>(stored?.email ?? null);

  const applyAuth = (auth: AuthResponse) => {
    setToken(auth.token);
    setEmail(auth.email);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(auth));
  };

  const register = async (registerEmail: string, password: string) => {
    const auth = await apiPostJson<AuthResponse>("/api/auth/register", { email: registerEmail, password });
    applyAuth(auth);
  };

  const login = async (loginEmail: string, password: string) => {
    const auth = await apiPostJson<AuthResponse>("/api/auth/login", { email: loginEmail, password });
    applyAuth(auth);
  };

  const logout = () => {
    setToken(null);
    setEmail(null);
    localStorage.removeItem(STORAGE_KEY);
  };

  const value = useMemo(
    () => ({ token, email, register, login, logout }),
    [token, email],
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
