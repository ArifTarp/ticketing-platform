/** Formats a decimal price as USD, e.g. 120 -> "$120.00". Demo-scope: single currency, no locale switch. */
export function formatPrice(amount: number): string {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(amount);
}
