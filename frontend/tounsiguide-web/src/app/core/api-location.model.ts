/**
 * Entry of the temporary Sprint 1 dummy-location catalog.
 * This is not the future Location & Matching Service model.
 */
export interface ApiLocation {
  id: number;
  name: string;
  description?: string | null;
  city?: string | null;
}
