const rtf = new Intl.RelativeTimeFormat('fr', { numeric: 'auto' });

/** "il y a 5 minutes", "hier", ... */
export function relativeTime(isoDate: string): string {
  const seconds = Math.round((Date.parse(isoDate) - Date.now()) / 1000);
  const steps: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 31_536_000], ['month', 2_592_000], ['week', 604_800],
    ['day', 86_400], ['hour', 3_600], ['minute', 60],
  ];
  for (const [unit, size] of steps) {
    if (Math.abs(seconds) >= size) return rtf.format(Math.round(seconds / size), unit);
  }
  return "à l'instant";
}

export function fullDate(isoDate: string): string {
  return new Date(isoDate).toLocaleString('fr-FR', { dateStyle: 'long', timeStyle: 'short' });
}

export function initials(name: string): string {
  return name.split(/\s+/).filter(Boolean).map(p => p[0]).join('').slice(0, 2).toUpperCase() || '?';
}

/** True when the resource was edited after creation (ignores sub-second drift). */
export function wasEdited(createdAt: string, updatedAt: string): boolean {
  return Date.parse(updatedAt) - Date.parse(createdAt) > 1000;
}
