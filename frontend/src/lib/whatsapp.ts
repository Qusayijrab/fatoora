/**
 * WhatsApp links (wa.me) need the number in international form with digits only.
 * Local numbers are converted with the client's country: in Jordan "0791234567"
 * becomes "962791234567"; "+962 79 123 4567" and "00962791234567" work too.
 */
const CALLING_CODES: Record<string, string> = {
  JO: "962",
  SA: "966",
  AE: "971",
  KW: "965",
  QA: "974",
  BH: "973",
  OM: "968",
  EG: "20",
  PS: "970",
  IQ: "964",
  LB: "961",
  SY: "963",
  TR: "90",
  GB: "44",
  US: "1",
};

/** Digits for wa.me, or null if this doesn't look like a phone number. */
export function whatsappNumber(
  phone: string | null | undefined,
  country: string | null | undefined,
): string | null {
  if (!phone) return null;
  const raw = phone.trim();
  let digits = raw.replace(/[^\d]/g, "");
  if (raw.startsWith("+")) {
    // already international
  } else if (digits.startsWith("00")) {
    digits = digits.slice(2);
  } else if (digits.startsWith("0")) {
    const code = CALLING_CODES[(country ?? "JO").toUpperCase()];
    if (!code) return null;
    digits = code + digits.slice(1);
  } else {
    const code = CALLING_CODES[(country ?? "JO").toUpperCase()];
    // A local number written without its leading 0 (e.g. "791234567" in Jordan).
    if (code && digits.length <= 9) digits = code + digits;
  }
  return digits.length >= 8 && digits.length <= 15 ? digits : null;
}

/** https://wa.me/<number>?text=... (without a number, WhatsApp asks who to send it to). */
export function whatsappUrl(number: string | null, text: string): string {
  return `https://wa.me/${number ?? ""}?text=${encodeURIComponent(text)}`;
}
