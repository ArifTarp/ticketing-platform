import type { Metadata } from "next";
import { RegisterForm } from "@/components/auth/RegisterForm";

export const metadata: Metadata = { title: "Register — Ticketing" };

export default function RegisterPage() {
  return <RegisterForm />;
}
