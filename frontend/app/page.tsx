import { redirect } from "next/navigation";

/** Root route isn't in docs/user-flow.md's screen list — send visitors straight to the event list. */
export default function RootPage() {
  redirect("/events");
}
