import { useState } from "react";

/** A password field with an optional "Show" toggle - off by default, so nothing changes for
 *  anyone who doesn't touch it. Text label rather than an eye icon, matching how the rest of
 *  this app favours words over iconography (see AppHeader, buttons throughout). */
export function PasswordInput({
  id,
  value,
  onChange,
  required,
  minLength,
  disabled,
  autoComplete = "current-password",
}: {
  id: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  minLength?: number;
  disabled?: boolean;
  /** "new-password" on Register so password managers offer to generate one instead of
   *  autofilling an old login; "current-password" (the default) everywhere else. */
  autoComplete?: "current-password" | "new-password";
}) {
  const [visible, setVisible] = useState(false);

  return (
    <div className="password-field">
      <input
        id={id}
        type={visible ? "text" : "password"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required={required}
        minLength={minLength}
        disabled={disabled}
        autoComplete={autoComplete}
      />
      <button
        type="button"
        className="password-field__toggle"
        onClick={() => setVisible((v) => !v)}
        disabled={disabled}
        aria-label={visible ? "Hide password" : "Show password"}
        aria-pressed={visible}
      >
        {visible ? "Hide" : "Show"}
      </button>
    </div>
  );
}
