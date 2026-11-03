/** Currencies offered in pickers, grouped for the region first. Decimals follow ISO 4217. */
export type CurrencyInfo = { code: string; name: string; decimals: number };

export const CURRENCY_GROUPS: { label: string; currencies: CurrencyInfo[] }[] = [
  {
    label: "Levant & Gulf",
    currencies: [
      { code: "JOD", name: "Jordanian dinar", decimals: 3 },
      { code: "SAR", name: "Saudi riyal", decimals: 2 },
      { code: "AED", name: "UAE dirham", decimals: 2 },
      { code: "KWD", name: "Kuwaiti dinar", decimals: 3 },
      { code: "QAR", name: "Qatari riyal", decimals: 2 },
      { code: "BHD", name: "Bahraini dinar", decimals: 3 },
      { code: "OMR", name: "Omani rial", decimals: 3 },
      { code: "EGP", name: "Egyptian pound", decimals: 2 },
    ],
  },
  {
    label: "International",
    currencies: [
      { code: "USD", name: "US dollar", decimals: 2 },
      { code: "EUR", name: "Euro", decimals: 2 },
      { code: "GBP", name: "Pound sterling", decimals: 2 },
      { code: "TRY", name: "Turkish lira", decimals: 2 },
      { code: "JPY", name: "Japanese yen", decimals: 0 },
    ],
  },
];

export const CURRENCIES: CurrencyInfo[] = CURRENCY_GROUPS.flatMap((g) => g.currencies);

export function currencyDecimals(code: string): number {
  return CURRENCIES.find((c) => c.code === code)?.decimals ?? 2;
}
