import { message } from "../lib/message";

export default function Page() {
  return (
    <main>
      <h1>{message("__NAME__")}</h1>
    </main>
  );
}
