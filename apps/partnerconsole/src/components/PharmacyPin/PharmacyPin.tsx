import { useEffect, useRef } from "react";
import { PIN_MESSAGE, readPinMessage, type PinPayload } from "./pin";

type PharmacyPinProps = {
  latitude: number | null;
  longitude: number | null;
  onPin: (pin: PinPayload) => void;
};

export function PharmacyPin({ latitude, longitude, onPin }: PharmacyPinProps) {
  const frame = useRef<HTMLIFrameElement>(null);
  const onPinRef = useRef(onPin);
  const pinRef = useRef({ latitude, longitude });
  onPinRef.current = onPin;
  pinRef.current = { latitude, longitude };

  useEffect(() => {
    function onMessage(event: MessageEvent) {
      const pin = readPinMessage(event, window.location.origin);
      if (!pin || event.source !== frame.current?.contentWindow) return;
      onPinRef.current(pin);
    }
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, []);

  useEffect(() => {
    const target = frame.current?.contentWindow;
    if (!target) return;
    target.postMessage({ type: PIN_MESSAGE, latitude, longitude }, window.location.origin);
  }, [latitude, longitude]);

  function publish() {
    const target = frame.current?.contentWindow;
    if (!target) return;
    target.postMessage(
      { type: PIN_MESSAGE, latitude: pinRef.current.latitude, longitude: pinRef.current.longitude },
      window.location.origin,
    );
  }

  return (
    <iframe
      ref={frame}
      title="Pharmacy location map"
      src="/pharmacy-pin/index.html"
      className="h-[420px] w-full rounded-md border border-line bg-paper"
      onLoad={publish}
    />
  );
}
