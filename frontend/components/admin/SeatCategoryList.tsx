import type { SeatCategoryDto } from "@/types/event";

/** One editable, not-yet-submitted seat category row — price kept as a string for the input. */
export interface SeatCategoryRow {
  name: string;
  price: string;
  section: string;
}

export function createEmptySeatCategoryRow(): SeatCategoryRow {
  return { name: "", price: "", section: "" };
}

/** A row is valid once all three fields are filled and price is a positive number. */
export function isSeatCategoryRowValid(row: SeatCategoryRow): boolean {
  const price = Number(row.price);
  return row.name.trim() !== "" && row.section.trim() !== "" && price > 0 && Number.isFinite(price);
}

interface SeatCategoryListProps {
  /**
   * Categories the event already has (edit mode only) — read-only here. `POST
   * /events/{id}/seat-categories` only ever adds new tiers, and the detail response's
   * SeatCategoryDto doesn't carry `section`, so an existing row can't be resubmitted/edited.
   */
  existingCategories: SeatCategoryDto[];
  rows: SeatCategoryRow[];
  onRowsChange: (rows: SeatCategoryRow[]) => void;
  isDisabled: boolean;
}

/**
 * Repeatable rows of price tiers (name, price, section); add/remove client-side before submit.
 * No per-seat editing UI — event owns the static seat map, but seat-level admin editing is out of
 * scope for this screen (docs/user-flow.md screen 8).
 */
export function SeatCategoryList({
  existingCategories,
  rows,
  onRowsChange,
  isDisabled,
}: SeatCategoryListProps) {
  function updateRow(index: number, patch: Partial<SeatCategoryRow>) {
    onRowsChange(rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  function removeRow(index: number) {
    onRowsChange(rows.filter((_, i) => i !== index));
  }

  function addRow() {
    onRowsChange([...rows, createEmptySeatCategoryRow()]);
  }

  return (
    <div className="flex flex-col gap-2">
      <span className="label-mono">Seat categories</span>

      {existingCategories.length > 0 && (
        <div className="flex flex-col gap-1 rounded-[var(--radius-sm)] border border-[var(--border)] bg-[var(--surface-sunken)] p-2 text-sm text-[var(--text-secondary)]">
          {existingCategories.map((category) => (
            <div key={category.id} className="flex justify-between">
              <span>{category.name}</span>
              <span className="value-mono">${category.price}</span>
            </div>
          ))}
        </div>
      )}

      {rows.map((row, index) => (
        <div key={index} className="flex gap-2">
          <input
            type="text"
            placeholder="Name (e.g. VIP)"
            value={row.name}
            onChange={(e) => updateRow(index, { name: e.target.value })}
            disabled={isDisabled}
            className="input-field flex-1 disabled:opacity-50"
          />
          <input
            type="text"
            placeholder="Section"
            value={row.section}
            onChange={(e) => updateRow(index, { section: e.target.value })}
            disabled={isDisabled}
            className="input-field w-28 disabled:opacity-50"
          />
          <input
            type="number"
            min="0"
            step="0.01"
            placeholder="Price"
            value={row.price}
            onChange={(e) => updateRow(index, { price: e.target.value })}
            disabled={isDisabled}
            className="input-field value-mono w-28 disabled:opacity-50"
          />
          <button
            type="button"
            onClick={() => removeRow(index)}
            disabled={isDisabled || rows.length <= 1}
            className="btn btn-secondary disabled:cursor-not-allowed"
          >
            Remove
          </button>
        </div>
      ))}

      <button type="button" onClick={addRow} disabled={isDisabled} className="btn btn-secondary self-start">
        + Add seat category
      </button>
    </div>
  );
}
