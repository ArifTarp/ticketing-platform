/** Formats a decimal price as Turkish lira, e.g. 120 -> "₺120,00". Turkish-market demo (root CLAUDE.md). */
export function formatPrice(amount: number): string {
  return new Intl.NumberFormat("tr-TR", {
    style: "currency",
    currency: "TRY",
  }).format(amount);
}
