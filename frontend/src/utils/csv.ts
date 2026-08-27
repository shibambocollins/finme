/**
 * Client-side only - the full (filtered) transaction list is already sitting in memory from the
 * dashboard's own fetch, so building a CSV in the browser and handing it straight to the user
 * avoids a redundant round trip and a new backend endpoint for what is fundamentally a text
 * reformat of data the client already has.
 */
function escapeCsvField(value: string): string {
  if (/[",\n\r]/.test(value)) {
    return '"' + value.replace(/"/g, '""') + '"';
  }
  return value;
}

export function downloadCsv(filename: string, headers: string[], rows: (string | number)[][]): void {
  const lines = [headers, ...rows].map((row) => row.map((cell) => escapeCsvField(String(cell))).join(","));
  // A leading BOM so Excel (still the most common opener for a downloaded CSV) reads UTF-8
  // correctly instead of mangling anything outside plain ASCII.
  const blob = new Blob(["﻿" + lines.join("\r\n")], { type: "text/csv;charset=utf-8;" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
