/** Countries offered in pickers: the region first, then common trading partners. */
export const COUNTRY_GROUPS: { label: string; codes: string[] }[] = [
  { label: "Levant & Gulf", codes: ["JO", "SA", "AE", "KW", "QA", "BH", "OM", "EG", "LB", "IQ", "PS", "SY", "YE"] },
  { label: "North Africa", codes: ["MA", "DZ", "TN", "LY", "SD"] },
  {
    label: "International",
    codes: ["US", "GB", "DE", "FR", "IT", "ES", "NL", "TR", "CA", "AU", "IN", "PK", "CN", "JP", "SG", "MY"],
  },
];

const names = new Intl.DisplayNames(["en"], { type: "region" });

export function countryName(code: string | null | undefined): string {
  if (!code) return "";
  try {
    return names.of(code) ?? code;
  } catch {
    return code;
  }
}
