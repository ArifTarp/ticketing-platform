"use client";

import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";

interface QrCodeDisplayProps {
  /** Value encoded into the QR — the booking id, per docs/user-flow.md screen 7. */
  value: string;
  size?: number;
}

/**
 * Client-side-generated QR code of a booking id (no real ticketing/QR backend, per
 * docs/user-flow.md screen 7). Renders to a <canvas> using the `qrcode` package.
 */
export function QrCodeDisplay({ value, size = 128 }: QrCodeDisplayProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!canvasRef.current) return;
    setError(false);
    QRCode.toCanvas(canvasRef.current, value, { width: size, margin: 1 }).catch(() => {
      setError(true);
    });
  }, [value, size]);

  if (error) {
    return (
      <div
        className="label-mono flex items-center justify-center rounded-[var(--radius-sm)] border border-dashed border-[var(--border-strong)] text-center"
        style={{ width: size, height: size }}
      >
        QR unavailable
      </div>
    );
  }

  return (
    <div
      className="flex items-center justify-center rounded-[var(--radius-sm)] border border-[var(--border)] bg-[var(--surface-sunken)] p-2"
    >
      <canvas ref={canvasRef} width={size} height={size} aria-label={`QR code for booking ${value}`} />
    </div>
  );
}
