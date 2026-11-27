import { OpeningHoursFields } from "@/components/OpeningHours";
import { Stack } from "@/components/ui/Stack";
import type { ProfileForm } from "../../model";
import { cardClass } from "../formStyles";

export function OpeningHoursCard({
  draft,
  onChange,
}: {
  draft: ProfileForm;
  onChange: (patch: Partial<ProfileForm>) => void;
}) {
  return (
    <Stack className={cardClass}>
      <OpeningHoursFields
        hours={draft.openingHours}
        onChange={(openingHours) => onChange({ openingHours, includeOpeningHours: true })}
      />
    </Stack>
  );
}
