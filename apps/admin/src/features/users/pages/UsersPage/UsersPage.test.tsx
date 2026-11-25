import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createAppStore } from "@/app/store";
import { SESSION_STORAGE_KEY } from "@/constants";
import type { Session } from "@/types";
import { UsersPage } from "./UsersPage";

const session: Session = {
  accessToken: "access",
  refreshToken: "refresh",
  expiresIn: 900,
  account: {
    id: "account-id",
    accountType: "admin",
    phone: "+919876543210",
    displayName: "Ada",
    pharmacyId: null,
    pharmacyRole: null,
    verificationStatus: null,
    rejection: null,
    subtypes: [],
    permissions: [],
    pinSetupRequired: false,
  },
};

type StaffRow = {
  id: string;
  phone: string;
  displayName: string;
  subtypes: string[];
  disabled: boolean;
};

function requestUrl(input: RequestInfo | URL) {
  if (typeof input === "string") return input;
  if (input instanceof URL) return input.toString();
  return input.url;
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

async function callOf(input: RequestInfo | URL, init?: RequestInit) {
  const url = requestUrl(input);
  const method = init?.method ?? (input instanceof Request ? input.method : "GET");
  let raw = "";
  if (typeof init?.body === "string") raw = init.body;
  else if (input instanceof Request) raw = await input.clone().text();
  return { url, method, raw };
}

function renderPage() {
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ session, pinReset: false }));
  return render(
    <Provider store={createAppStore()}>
      <MemoryRouter>
        <UsersPage />
      </MemoryRouter>
    </Provider>,
  );
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessionStorage.clear();
});

describe("UsersPage", () => {
  it("lists staff, creates one, and disables a row", async () => {
    const rows: StaffRow[] = [
      {
        id: "staff-1",
        phone: "+919811111111",
        displayName: "Nia",
        subtypes: ["support", "finance"],
        disabled: false,
      },
    ];
    const calls: { method: string; raw: string }[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const call = await callOf(input, init);
        calls.push(call);
        const path = new URL(call.url, "http://127.0.0.1:8080").pathname;
        if (call.method === "POST" && path === "/staff") {
          const body = JSON.parse(call.raw) as {
            phone: string;
            displayName: string;
            subtypes: string[];
          };
          rows.push({
            id: "staff-2",
            phone: "+919811111112",
            displayName: body.displayName,
            subtypes: body.subtypes,
            disabled: false,
          });
          return json(rows[1], 201);
        }
        if (call.method === "PATCH" && path === "/staff/staff-1") {
          const body = JSON.parse(call.raw) as { disabled?: boolean };
          rows[0] = { ...rows[0], disabled: body.disabled ?? rows[0].disabled };
          return json(rows[0]);
        }
        if (call.method === "GET" && path === "/staff") return json(rows);
        return json({ detail: "Not found" }, 404);
      }),
    );

    renderPage();
    expect(await screen.findByText("Nia")).toBeTruthy();
    expect(screen.getByText("+919811111111")).toBeTruthy();
    expect(screen.getByText("Finance, Support")).toBeTruthy();

    fireEvent.click(screen.getByRole("button", { name: "Add user" }));
    fireEvent.change(await screen.findByLabelText("Name"), { target: { value: "Ada" } });
    fireEvent.change(screen.getByLabelText("Mobile"), { target: { value: "9811111112" } });
    fireEvent.click(screen.getByRole("checkbox", { name: "Compliance" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => {
      expect(calls.some((call) => call.method === "POST" && call.raw.includes("9811111112"))).toBe(
        true,
      );
    });
    const created = calls.find((call) => call.method === "POST");
    expect(JSON.parse(created?.raw ?? "{}")).toEqual({
      displayName: "Ada",
      phone: "9811111112",
      subtypes: ["compliance"],
    });
    expect(await screen.findByText("Ada")).toBeTruthy();

    fireEvent.click(screen.getByRole("switch", { name: "Disable Nia" }));
    await waitFor(() => {
      expect(screen.getByRole("switch", { name: "Enable Nia" })).toBeTruthy();
    });
    const patched = calls.find((call) => call.method === "PATCH");
    expect(JSON.parse(patched?.raw ?? "{}")).toEqual({ disabled: true });
  });

  it("shows the server detail when a phone is already staff", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const call = await callOf(input, init);
        const path = new URL(call.url, "http://127.0.0.1:8080").pathname;
        if (call.method === "POST" && path === "/staff") {
          return json({ detail: "A staff account already exists for this phone" }, 409);
        }
        if (call.method === "GET" && path === "/staff") return json([]);
        return json({ detail: "Not found" }, 404);
      }),
    );

    renderPage();
    fireEvent.click(await screen.findByRole("button", { name: "Add user" }));
    fireEvent.change(await screen.findByLabelText("Name"), { target: { value: "Ada" } });
    fireEvent.change(screen.getByLabelText("Mobile"), { target: { value: "9811111112" } });
    fireEvent.click(screen.getByRole("checkbox", { name: "Sales" }));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText("A staff account already exists for this phone")).toBeTruthy();
  });

  it("asks for a role before saving", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const call = await callOf(input, init);
        if (call.method === "GET") return json([]);
        return json({ detail: "Not found" }, 404);
      }),
    );

    renderPage();
    expect(await screen.findByText("No staff yet.")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Add user" }));
    fireEvent.change(await screen.findByLabelText("Name"), { target: { value: "Ada" } });
    fireEvent.change(screen.getByLabelText("Mobile"), { target: { value: "9811111112" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByText("Choose a staff subtype")).toBeTruthy();
  });

  it("says when staff cannot be loaded", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => json({ detail: "Only an admin can manage platform staff" }, 403)),
    );
    renderPage();
    expect(await screen.findByText("Only an admin can manage platform staff")).toBeTruthy();
  });
});
