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
 * endpoint). Client-side validation only; "Pay now" confirms intent and transitions the screen
 * straight to the processing overlay, it does not call any backend endpoint itself.
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
    <form onSubmit={handleSubmit} className="flex flex-col gap-4 rounded-lg border border-zinc-200 bg-white p-4">
      <h2 className="text-sm font-semibold text-zinc-900">Payment details</h2>
      <FormError message={error} />
      <label className="flex flex-col gap-1 text-sm">
        Card number
        <input
          type="text"
          inputMode="numeric"
          placeholder="4242 4242 4242 4242"
          value={cardNumber}
          onChange={(e) => setCardNumber(e.target.value)}
          disabled={isDisabled}
          className="rounded-md border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none disabled:bg-zinc-100"
        />
      </label>
      <div className="flex gap-3">
        <label className="flex flex-1 flex-col gap-1 text-sm">
          Expiry
          <input
            type="text"
            placeholder="MM/YY"
            value={expiry}
            onChange={(e) => setExpiry(e.target.value)}
            disabled={isDisabled}
            className="rounded-md border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none disabled:bg-zinc-100"
          />
        </label>
        <label className="flex flex-1 flex-col gap-1 text-sm">
          CVC
          <input
            type="text"
            inputMode="numeric"
            placeholder="123"
            value={cvc}
            onChange={(e) => setCvc(e.target.value)}
            disabled={isDisabled}
            className="rounded-md border border-zinc-300 px-3 py-2 text-sm focus:border-zinc-500 focus:outline-none disabled:bg-zinc-100"
          />
        </label>
      </div>
      <button
        type="submit"
        disabled={isDisabled}
        className="w-full rounded-md bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-700 disabled:cursor-not-allowed disabled:bg-zinc-300"
      >
        Pay now
      </button>
    </form>
  );
}
