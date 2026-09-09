"use client";

import { useState, type FormEvent } from "react";
import { FormError } from "@/components/common/FormError";

interface PaymentFormProps {
  isDisabled: boolean;
  onPay: () => void;
}

const CARD_NUMBER_PATTERN = /^\d{16}$/;
const EXPIRY_PATTERN = /^(0[1-9]|1[0-2])\/\d{2}$/;
const CVC_PATTERN = /^\d{3,4}$/;

/**
 * Mock card fields — cosmetic UX only (business-rules.md: no real PSP, no backend payment
 * endpoint). Client-side validation only; "Pay now" confirms intent and delegates to `onPay`,
 * which transitions the screen to the processing overlay. `onPay` itself may call
 * `POST /bookings/{id}/checkout` as a fallback if the checkout saga hasn't already been triggered
 * from the seat-selection screen (see the checkout page for that logic) — this form has no
 * knowledge of the backend call, it only confirms user intent.
 */
export function PaymentForm({ isDisabled, onPay }: PaymentFormProps) {
  const [cardNumber, setCardNumber] = useState("");
  const [expiry, setExpiry] = useState("");
  const [cvc, setCvc] = useState("");
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!CARD_NUMBER_PATTERN.test(cardNumber.replace(/\s/g, ""))) {
      setError("Enter a valid 16-digit card number.");
      return;
    }
    if (!EXPIRY_PATTERN.test(expiry)) {
      setError("Enter expiry as MM/YY.");
      return;
    }
    if (!CVC_PATTERN.test(cvc)) {
      setError("Enter a valid CVC.");
      return;
    }
    setError(null);
    onPay();
  }

  return (
    <form onSubmit={handleSubmit} className="panel flex flex-col gap-4 p-5">
      <div className="flex items-center gap-3">
        <span className="value-mono flex h-5 w-5 shrink-0 items-center justify-center rounded-full border border-[var(--accent-border)] bg-[var(--accent-soft)] text-xs text-[var(--accent)]">
          2
        </span>
        <h2 className="label-mono">Payment details</h2>
      </div>
      <FormError message={error} />
      <label className="flex flex-col gap-1.5 text-sm text-[var(--text-secondary)]">
        Card number
        <input
          type="text"
          inputMode="numeric"
          placeholder="4242 4242 4242 4242"
          value={cardNumber}
          onChange={(e) => setCardNumber(e.target.value)}
          disabled={isDisabled}
          className="input-field value-mono disabled:opacity-50"
        />
      </label>
      <div className="flex gap-3">
        <label className="flex flex-1 flex-col gap-1.5 text-sm text-[var(--text-secondary)]">
          Expiry
          <input
            type="text"
            placeholder="MM/YY"
            value={expiry}
            onChange={(e) => setExpiry(e.target.value)}
            disabled={isDisabled}
            className="input-field value-mono disabled:opacity-50"
          />
        </label>
        <label className="flex flex-1 flex-col gap-1.5 text-sm text-[var(--text-secondary)]">
          CVC
          <input
            type="text"
            inputMode="numeric"
            placeholder="123"
            value={cvc}
            onChange={(e) => setCvc(e.target.value)}
            disabled={isDisabled}
            className="input-field value-mono disabled:opacity-50"
          />
        </label>
      </div>
      <button type="submit" disabled={isDisabled} className="btn btn-primary w-full">
        Pay now
      </button>
    </form>
  );
}
