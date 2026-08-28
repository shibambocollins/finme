import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { Register } from "./pages/Register";
import { Login } from "./pages/Login";
import { Dashboard } from "./pages/Dashboard";
import { Credit } from "./pages/Credit";
import { Budgets } from "./pages/Budgets";
import { CalendarPage } from "./pages/Calendar";
import { AuthCallback } from "./pages/AuthCallback";
import { Landing } from "./pages/Landing";
import { Settings } from "./pages/Settings";
import { Privacy } from "./pages/Privacy";
import { Terms } from "./pages/Terms";
import { NotFound } from "./pages/NotFound";
import { CookieBanner } from "./components/CookieBanner";
import "./App.css";

/** The public marketing page at "/" for a logged-out visitor; a signed-in one goes straight to
 *  the dashboard instead of seeing sample data for an app they're already inside. */
function RootRoute() {
  const { token } = useAuth();
  return token ? <Navigate to="/dashboard" replace /> : <Landing />;
}

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/" element={<RootRoute />} />
        <Route path="/register" element={<Register />} />
        <Route path="/login" element={<Login />} />
        <Route path="/auth-callback" element={<AuthCallback />} />
        <Route path="/privacy" element={<Privacy />} />
        <Route path="/terms" element={<Terms />} />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="/credit"
          element={
            <ProtectedRoute>
              <Credit />
            </ProtectedRoute>
          }
        />
        <Route
          path="/budgets"
          element={
            <ProtectedRoute>
              <Budgets />
            </ProtectedRoute>
          }
        />
        <Route
          path="/calendar"
          element={
            <ProtectedRoute>
              <CalendarPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/settings"
          element={
            <ProtectedRoute>
              <Settings />
            </ProtectedRoute>
          }
        />
        {/* Catch-all - must stay last. Anything that doesn't match a real route above (a typo,
            a stale bookmark, a removed page) gets an explicit "not found" instead of a blank
            screen. */}
        <Route path="*" element={<NotFound />} />
      </Routes>
      <CookieBanner />
    </AuthProvider>
  );
}

export default App;
