import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { Provider } from "react-redux";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createAppStore } from "@/app/store";
import { SESSION_STORAGE_KEY } from "@/constants";
import type { Session } from "@/types";
import { AppSectionsPage } from "./AppSectionsPage";

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
    subtypes: ["sales"],
    permissions: [],
    pinSetupRequired: false,
  },
};

function requestUrl(input: RequestInfo | URL) {
  if (typeof input === "string") return input;
  if (input instanceof URL) return input.toString();
  return input.url;
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessionStorage.clear();
});

describe("AppSectionsPage", () => {
  it("lists a category tile and opens the editor", async () => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ session, pinReset: false }));
    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const url = requestUrl(input);
        if (url.includes("/api/v1/catalog/categories")) {
          return Response.json({ categories: ["Pain", "Skin"] });
        }
        if (url.includes("/api/v1/app-sections")) {
          return Response.json({
            sections: [
              {
                id: "section-1",
                title: "Everyday care",
                position: 1,
                image: { contentType: "image/png", url: "http://127.0.0.1/section.png" },
                banner: { contentType: "image/webp", url: "http://127.0.0.1/banner.png" },
                categories: [
                  {
                    id: "category-1",
                    category: "Pain",
                    position: 0,
                    images: [
                      {
                        id: "image-1",
                        contentType: "image/jpeg",
                        url: "http://127.0.0.1/pain.png",
                      },
                    ],
                  },
                ],
              },
            ],
          });
        }
        return Response.json({ detail: "Not found" }, { status: 404 });
      }),
    );

    render(
      <Provider store={createAppStore()}>
        <AppSectionsPage />
      </Provider>,
    );

    expect(await screen.findByText("Everyday care")).toBeTruthy();
    expect(screen.getByText("Pain")).toBeTruthy();
    expect(screen.getByText("Position 1")).toBeTruthy();
    expect(document.querySelectorAll("article img")).toHaveLength(3);
    fireEvent.click(screen.getByRole("button", { name: "Edit" }));
    expect(await screen.findByRole("heading", { name: "Edit section" })).toBeTruthy();
    expect(screen.getByLabelText("Name")).toBeTruthy();
    expect(screen.getByLabelText("Banner")).toBeTruthy();
    expect((screen.getByLabelText("Category") as HTMLSelectElement).value).toBe("Pain");
  });
});
