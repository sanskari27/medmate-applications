import { Field } from "@/components/ui/Field";
import { Input } from "@/components/ui/Input";
import { Text } from "@/components/ui/Text";
import { cn } from "@/utils/className";
import { WEEKDAYS, type OpeningHour } from "./hours";

export function OpeningHoursFields({
  hours,
  onChange,
  className,
}: {
  hours: OpeningHour[];
  onChange: (hours: OpeningHour[]) => void;
  className?: string;
}) {
  function patch(day: string, next: Partial<OpeningHour>) {
    onChange(hours.map((hour) => (hour.day === day ? { ...hour, ...next } : hour)));
  }

  return (
    <fieldset className={cn("flex flex-col gap-3 border-0 p-0", className)}>
      <legend className="text-[15px] font-bold tracking-tight text-ink">Open timings</legend>
      <Text variant="muted" className="text-[12.5px]">
        Each day is closed, or has one open time and one close time. Customers see this on nearby
        pharmacies.
      </Text>
      {WEEKDAYS.map(([day, label]) => {
        const hour = hours.find((item) => item.day === day) ?? {
          day,
          closed: true,
          opensAt: "",
          closesAt: "",
        };
        const closedId = `opening-${day}-closed`;
        const openId = `opening-${day}-opens`;
        const closeId = `opening-${day}-closes`;
        return (
          <div
            key={day}
            className="grid grid-cols-1 items-end gap-2 border-t border-line pt-3 sm:grid-cols-[8rem_auto_minmax(0,1fr)_minmax(0,1fr)]"
          >
            <Text as="span" className="pb-2 text-[13.5px] font-bold text-ink">
              {label}
            </Text>
            <span className="inline-flex items-center gap-2 pb-2">
              <input
                id={closedId}
                type="checkbox"
                checked={hour.closed}
                onChange={(event) =>
                  patch(day, {
                    closed: event.target.checked,
                    opensAt: event.target.checked ? "" : hour.opensAt,
                    closesAt: event.target.checked ? "" : hour.closesAt,
                  })
                }
              />
              <label htmlFor={closedId} className="text-[13px] text-ink-2">
                <span className="sr-only">{label} </span>
                Closed
              </label>
            </span>
            <Field
              label={
                <>
                  <span className="sr-only">{label} </span>
                  Opens
                </>
              }
              htmlFor={openId}
            >
              <Input
                id={openId}
                type="time"
                step={60}
                autoComplete="off"
                disabled={hour.closed}
                value={hour.opensAt}
                onChange={(event) => patch(day, { opensAt: event.target.value.slice(0, 5) })}
              />
            </Field>
            <Field
              label={
                <>
                  <span className="sr-only">{label} </span>
                  Closes
                </>
              }
              htmlFor={closeId}
            >
              <Input
                id={closeId}
                type="time"
                step={60}
                autoComplete="off"
                disabled={hour.closed}
                value={hour.closesAt}
                onChange={(event) => patch(day, { closesAt: event.target.value.slice(0, 5) })}
              />
            </Field>
          </div>
        );
      })}
    </fieldset>
  );
}
