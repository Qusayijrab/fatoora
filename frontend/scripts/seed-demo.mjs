#!/usr/bin/env node
/**
 * Creates a demo organization with six months of realistic data, through the public API
 * (the same rules as the app: numbering, tax per line, frozen exchange rates, audit log).
 *
 *   npm run seed:demo                       # API on http://localhost:8080
 *   API_URL=https://api.example.com npm run seed:demo
 *
 * Prints the login it created. Safe to run more than once: each run makes a new organization.
 */

const API = `${process.env.API_URL ?? "http://localhost:8080"}/api/v1`;
const email = process.env.DEMO_EMAIL ?? `demo-${Date.now().toString(36)}@fatoora.test`;
const password = process.env.DEMO_PASSWORD ?? "demo-password-2026";

async function call(method, path, body, token) {
  const res = await fetch(API + path, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${text.slice(0, 300)}`);
  return text ? JSON.parse(text) : null;
}

const iso = (d) => d.toISOString().slice(0, 10);
function daysAgo(n) {
  const d = new Date();
  d.setUTCHours(0, 0, 0, 0);
  d.setUTCDate(d.getUTCDate() - n);
  return d;
}
const plusDays = (d, n) => new Date(d.getTime() + n * 86_400_000);

const { accessToken: token } = await call("POST", "/auth/register", {
  email,
  password,
  fullName: "Lina Haddad",
  organizationName: "Nabati Design Studio",
  defaultCurrency: "JOD",
  timeZone: "Asia/Amman",
});
const t = (method, path, body) => call(method, path, body, token);

await t("PATCH", "/organizations/current", {
  name: "Nabati Design Studio",
  legalName: "Nabati Design Studio LLC",
  taxId: "JO-018273645",
  email: "billing@nabati.studio",
  phone: "+962 6 555 0142",
  addressLine1: "Rainbow Street 24",
  addressLine2: "Jabal Amman",
  city: "Amman",
  country: "JO",
  defaultCurrency: "JOD",
  timeZone: "Asia/Amman",
  remindersEnabled: true,
  reminderDays: [1, 7, 14],
});

const vat = await t("POST", "/tax-rates", { name: "Sales tax 16%", rate: "16", isDefault: true });
await t("POST", "/tax-rates", { name: "Exempt export", rate: "0", isDefault: false });

const product = (name, description, unit, unitPrice, currency = "JOD") =>
  t("POST", "/products", { name, description, unit, unitPrice, currency, taxRateId: vat.id });
const logo = await product("Logo design", "Three concepts, two rounds of changes", "project", "450.000");
const brand = await product("Brand guidelines", "Colour, type and usage, as a PDF book", "project", "780.000");
const social = await product("Social media month", "12 posts and 4 stories, Arabic and English", "month", "320.000");
const landing = await product("Landing page", "Design and build, bilingual", "page", "950.000");
const cards = await product("Business cards", "Matte 400gsm, double sided", "box", "35.000");

const client = (name, email, city, country, extra = {}) =>
  t("POST", "/clients", { name, email, city, country, ...extra });
const petra = await client("Petra Heritage Tours", "accounts@petraheritage.jo", "Wadi Musa", "JO", { taxId: "JO-004417290" });
const ufuq = await client("شركة الأفق للتجارة", "finance@alufuq.jo", "Amman", "JO");
const gulf = await client("Gulf Freight Logistics LLC", "ap@gulffreight.ae", "Dubai", "AE", { currency: "USD" });
const deadSea = await client("Dead Sea Wellness Resort", "billing@deadseawellness.jo", "Sweimeh", "JO");
const rum = await client("Wadi Rum Night Camps", "hello@rumnight.jo", "Aqaba", "JO");
const saraya = await client("Saraya Events", "events@saraya.jo", "Amman", "JO");

const line = (p, quantity = "1", unitPrice = p.unitPrice.amount) => ({
  productId: p.id,
  description: `${p.name} — ${p.description}`,
  quantity,
  unit: p.unit,
  unitPrice,
  taxRateId: vat.id,
});

/** Draft → (issue) → (payments). Dates are in the past so the dashboard has history. */
async function invoice({ c, currency = "JOD", lines, issuedDaysAgo, terms = 30, pay = [], issue = true, notes }) {
  const issueDate = daysAgo(issuedDaysAgo);
  let inv = await t("POST", "/invoices", {
    clientId: c.id,
    currency,
    issueDate: iso(issueDate),
    dueDate: iso(plusDays(issueDate, terms)),
    notes: notes ?? "Thank you for working with us.",
    terms: "Bank transfer to Arab Bank, IBAN JO71 ARAB 1180 0000 0011 8000 1234 56, within 30 days.",
    lines,
  });
  if (!issue) return inv;
  // History: keep the past dates (issuing otherwise dates an invoice today).
  inv = await t("POST", `/invoices/${inv.id}/issue`, { keepDates: true });
  for (const [amount, afterDays, method] of pay) {
    const paidOn = plusDays(issueDate, afterDays);
    await t("POST", `/invoices/${inv.id}/payments`, {
      amount: amount === "all" ? inv.balanceDue.amount : amount,
      paidOn: iso(paidOn > daysAgo(0) ? daysAgo(0) : paidOn),
      method: method ?? "BANK_TRANSFER",
    });
    inv = await t("GET", `/invoices/${inv.id}`);
  }
  return inv;
}

// Six months of work: paid, part-paid, open, overdue and a draft.
await invoice({ c: petra, lines: [line(logo), line(cards, "4")], issuedDaysAgo: 170, pay: [["all", 12]] });
await invoice({ c: ufuq, lines: [line(brand)], issuedDaysAgo: 150, pay: [["all", 20]] });
await invoice({ c: deadSea, lines: [line(social, "3")], issuedDaysAgo: 128, pay: [["all", 9, "CARD"]] });
await invoice({ c: gulf, currency: "USD", lines: [{ ...line(landing, "1", "1340.00"), taxRateId: null }], issuedDaysAgo: 110, pay: [["all", 25]] });
await invoice({ c: rum, lines: [line(logo), line(social)], issuedDaysAgo: 96, pay: [["all", 14, "CASH"]] });
await invoice({ c: saraya, lines: [line(cards, "10"), line(social)], issuedDaysAgo: 80, pay: [["all", 30]] });
await invoice({ c: petra, lines: [line(landing), line(social, "2")], issuedDaysAgo: 64, pay: [["800.000", 20]] });
await invoice({ c: ufuq, lines: [line(social, "2")], issuedDaysAgo: 52, pay: [["all", 6]] });
await invoice({ c: deadSea, lines: [line(brand), line(cards, "6")], issuedDaysAgo: 45 }); // overdue
await invoice({ c: gulf, currency: "USD", lines: [{ ...line(social, "2", "450.00"), taxRateId: null }], issuedDaysAgo: 33, pay: [["450.00", 10]] });
await invoice({ c: rum, lines: [line(landing)], issuedDaysAgo: 21, pay: [["all", 8, "CARD"]] });
await invoice({ c: saraya, lines: [line(brand), line(logo)], issuedDaysAgo: 12 });
await invoice({ c: deadSea, lines: [line(cards, "8")], issuedDaysAgo: 3, pay: [["all", 1, "CARD"]] });
await invoice({ c: petra, lines: [line(social)], issuedDaysAgo: 2 });
await invoice({ c: ufuq, lines: [line(landing), line(cards, "2")], issuedDaysAgo: 0, issue: false, notes: "Draft: waiting for final page count." });

// Quotes: one accepted and converted, one waiting for an answer, one draft.
const q1 = await t("POST", "/quotes", { clientId: rum.id, lines: [line(brand), line(cards, "3")], notes: "Valid for 30 days." });
await t("POST", `/quotes/${q1.id}/send`, {});
await t("POST", `/quotes/${q1.id}/accept`, {});
await t("POST", `/quotes/${q1.id}/convert`, {});
const q2 = await t("POST", "/quotes", { clientId: deadSea.id, lines: [line(landing), line(social, "3")], notes: "Includes Arabic and English copywriting." });
await t("POST", `/quotes/${q2.id}/send`, {});
await t("POST", "/quotes", { clientId: saraya.id, lines: [line(social, "6", "290.000")], notes: "Six-month retainer price." });

// A retainer that bills itself on the first of every month.
const next = new Date();
next.setUTCMonth(next.getUTCMonth() + 1, 1);
await t("POST", "/recurring", {
  clientId: deadSea.id,
  name: "Monthly social media retainer",
  frequency: "MONTHLY",
  intervalCount: 1,
  startDate: iso(next),
  paymentTermsDays: 14,
  autoIssue: true,
  autoEmail: false,
  lines: [line(social)],
});

console.log(`\nDemo ready: Nabati Design Studio\n  email:    ${email}\n  password: ${password}\n`);
