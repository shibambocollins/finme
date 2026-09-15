/**
 * Date presentation for a South African audience.
 *
 * Two problems this fixes. The transaction table rendered the backend's ISO date verbatim
 * ("2026-08-04"), which is a wire format, not something to show a user. Everywhere else
 * called toLocaleDateString() with no locale, which follows the *browser's* setting - so a
 * machine configured US-English silently rendered 04/08/2026 as August 4th in American
 * order, indistinguishable from 8 April and wrong without ever looking wrong.
 *
 * Pinning to en-ZA makes the format a product decision rather than an accident of whatever
 * the viewer's OS happens to be set to. FinMe reports in rands; it should read in the same
 * conventions.
 */
const LOCALE = "en-ZA";

/**
 * Parses the backend's "yyyy-MM-dd" without going through Date's string parser.
 *
 * new Date("2026-08-04") is treated as UTC midnight, which in any timezone behind UTC
 * renders as the previous day. Constructing from parts keeps it local and exact - the kind
 * of off-by-one that only shows up for some users, in some timezones.
 */
function parseIsoDate(iso: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!match) return null;
  const [, year, month, day] = match;
  return new Date(Number(year), Number(month) - 1, Number(day));
}

/**
 * "2026-08-04" -> "04/08/2026". Falls back to the raw value rather than rendering junk.
 *
 * Built by hand rather than via toLocaleDateString("en-ZA"): Chromium's en-ZA numeric
 * format is yyyy/MM/dd ("2026/08/04"), which is not what South Africans write. The
 * conventional form is day-first, so it is spelled out here instead of trusting the
 * locale data to agree.
 */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "-";
  const date = parseIsoDate(iso);
  if (!date || Number.isNaN(date.getTime())) return iso;
  const day = String(date.getDate()).padStart(2, "0");
  const month = String(date.getMonth() + 1).padStart(2, "0");
  return `${day}/${month}/${date.getFullYear()}`;
}

/** "2026-08-04" -> "04 August 2026", for headings where the long form reads better. */
export function formatDateLong(iso: string | null | undefined): string {
  if (!iso) return "-";
  const date = parseIsoDate(iso);
  if (!date || Number.isNaN(date.getTime())) return iso;
  return date.toLocaleDateString(LOCALE, {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

/**
 * For timestamps the API returns as full ISO instants, not plain dates. Parsed by Date
 * (correct here - an instant carries its own zone) then rendered day-first to match
 * {@link formatDate}.
 */
export function formatTimestamp(iso: string | null | undefined): string {
  if (!iso) return "-";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  const day = String(date.getDate()).padStart(2, "0");
  const month = String(date.getMonth() + 1).padStart(2, "0");
  return `${day}/${month}/${date.getFullYear()}`;
}
