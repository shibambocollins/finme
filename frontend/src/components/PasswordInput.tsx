import { useState } from "react";

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
