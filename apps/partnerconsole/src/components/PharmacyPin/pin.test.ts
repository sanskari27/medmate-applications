import { describe, expect, it } from "vitest";
import { addressFromPin, readPinMessage } from "./pin";

const origin = "https://partner.medmate.test";

describe("readPinMessage", () => {
  it("accepts a same-origin pin and ignores every other message", () => {
    expect(
      readPinMessage(
        {
          origin,
          data: {
            type: "medmate-pharmacy-pin",
            latitude: 12.9716,
            longitude: 77.5946,
            city: "Bengaluru",
            state: "Karnataka",
            pincode: "560001",
          },
        },
        origin,
      ),
    ).toEqual({
      latitude: 12.9716,
      longitude: 77.5946,
      address: undefined,
      city: "Bengaluru",
      state: "Karnataka",
      pincode: "560001",
    });

    expect(
      readPinMessage(
        {
          origin: "https://other.example",
          data: { type: "medmate-pharmacy-pin", latitude: 1, longitude: 2 },
        },
        origin,
      ),
    ).toBeNull();
    expect(
      readPinMessage({ origin, data: { type: "other", latitude: 1, longitude: 2 } }, origin),
    ).toBeNull();
    expect(
      readPinMessage(
        { origin, data: { type: "medmate-pharmacy-pin", latitude: "12", longitude: 77 } },
        origin,
      ),
    ).toBeNull();
  });
});

describe("addressFromPin", () => {
  const empty = { address: "", city: "", state: "", pincode: "" };

  it("fills only blank fields and keeps an unknown state blank", () => {
    expect(
      addressFromPin(
        empty,
        { address: "12 Market Road", city: "Bengaluru", state: "Karnataka", pincode: "560001" },
        ["Karnataka"],
      ),
    ).toEqual({
      address: "12 Market Road",
      city: "Bengaluru",
      state: "Karnataka",
      pincode: "560001",
    });
    expect(
      addressFromPin(
        { address: "Already typed", city: "Mysuru", state: "Karnataka", pincode: "570001" },
        { address: "12 Market Road", city: "Bengaluru", state: "Goa", pincode: "560001" },
        ["Karnataka"],
      ),
    ).toEqual({});
    expect(
      addressFromPin(empty, { state: "Not A State", pincode: "056001" }, ["Karnataka"]),
    ).toEqual({});
  });
});
