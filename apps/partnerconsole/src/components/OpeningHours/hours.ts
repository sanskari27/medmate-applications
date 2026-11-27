export const WEEKDAYS = [
  ["monday", "Monday"],
  ["tuesday", "Tuesday"],
  ["wednesday", "Wednesday"],
  ["thursday", "Thursday"],
  ["friday", "Friday"],
  ["saturday", "Saturday"],
  ["sunday", "Sunday"],
] as const;

export type OpeningHour = {
  day: string;
  closed: boolean;
  opensAt: string;
  closesAt: string;
};

export type OpeningHourWire = {
  day: string;
  closed: boolean;
  opensAt: string | null;
  closesAt: string | null;
};

export function closedWeek(): OpeningHour[] {
  return WEEKDAYS.map(([day]) => ({ day, closed: true, opensAt: "", closesAt: "" }));
}

export function openingHoursFrom(
  hours: OpeningHourWire[] | null | undefined,
): OpeningHour[] | null {
  if (!hours || hours.length === 0) return null;
  const byDay = new Map(hours.map((hour) => [hour.day, hour]));
  return WEEKDAYS.map(([day]) => {
    const hour = byDay.get(day);
    if (!hour) return { day, closed: true, opensAt: "", closesAt: "" };
    return {
      day,
      closed: hour.closed,
      opensAt: hour.closed ? "" : (hour.opensAt ?? ""),
      closesAt: hour.closed ? "" : (hour.closesAt ?? ""),
    };
  });
}

export function serializeOpeningHours(hours: OpeningHour[]): OpeningHourWire[] {
  return hours.map((hour) => ({
    day: hour.day,
    closed: hour.closed,
    opensAt: hour.closed || !hour.opensAt ? null : hour.opensAt,
    closesAt: hour.closed || !hour.closesAt ? null : hour.closesAt,
  }));
}
