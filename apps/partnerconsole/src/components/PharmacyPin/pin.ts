export const PIN_MESSAGE = "medmate-pharmacy-pin";

export type PinPayload = {
  latitude: number;
  longitude: number;
  address?: string;
  city?: string;
  state?: string;
  pincode?: string;
};

type AddressFields = {
  address: string;
  city: string;
  state: string;
  pincode: string;
};

export function readPinMessage(
  event: { origin: string; data: unknown },
  expectedOrigin: string,
): PinPayload | null {
  if (event.origin !== expectedOrigin) return null;
  if (!event.data || typeof event.data !== "object") return null;
  const message = event.data as Record<string, unknown>;
  if (message.type !== PIN_MESSAGE) return null;
  if (typeof message.latitude !== "number" || typeof message.longitude !== "number") return null;
  if (!Number.isFinite(message.latitude) || !Number.isFinite(message.longitude)) return null;
  return {
    latitude: message.latitude,
    longitude: message.longitude,
    address: text(message.address),
    city: text(message.city),
    state: text(message.state),
    pincode: text(message.pincode),
  };
}

export function addressFromPin(
  current: AddressFields,
  suggestion: Pick<PinPayload, "address" | "city" | "state" | "pincode">,
  regions: readonly string[],
): Partial<AddressFields> {
  const patch: Partial<AddressFields> = {};
  if (!current.address.trim() && suggestion.address)
    patch.address = suggestion.address.slice(0, 300);
  if (!current.city.trim() && suggestion.city) patch.city = suggestion.city.slice(0, 80);
  if (!current.state.trim() && suggestion.state && regions.includes(suggestion.state)) {
    patch.state = suggestion.state;
  }
  if (!current.pincode.trim() && suggestion.pincode && /^[1-9][0-9]{5}$/.test(suggestion.pincode)) {
    patch.pincode = suggestion.pincode;
  }
  return patch;
}

function text(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}
