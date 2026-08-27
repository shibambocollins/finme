/**
 * A starting point for category autocomplete, not a restriction - categories remain free text
 * everywhere in this app (Transaction.category has always been an unconstrained string; this
 * list only seeds the <datalist> suggestions a new user sees before they've typed any category
 * of their own). Chosen to reflect real South African spending patterns rather than the
 * generic, typically US-centric category sets most finance-app templates ship with.
 */
export const SUGGESTED_CATEGORIES = [
  "Groceries",
  "Eating Out",
  "Transport",
  "Taxi/E-hailing",
  "Fuel",
  "Rent",
  "Electricity & Utilities",
  "Airtime & Data",
  "Entertainment",
  "Shopping",
  "Personal Care",
  "Health",
  "Education",
  "Insurance",
  "Debt Repayments",
  "Bank Fees",
  "Subscriptions",
  "Family & Support",
  "Gifts",
  "Stokvel/Savings",
  "Savings",
  "Cash Withdrawal",
  "Transfers",
  "Salary/Income",
  "Refunds",
  "Other",
];
