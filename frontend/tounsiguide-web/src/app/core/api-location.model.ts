/** Where a Location comes from: the curated directory, or a place a member picked on the map. */
export type LocationSource = 'DIRECTORY' | 'MAP';

/** A Location of the Location & Matching Service (GET /api/v1/locations...). */
export interface ApiLocation {
  id: number;
  name: string;
  city: string | null;
  address: string | null;
  latitude: number | null;
  longitude: number | null;
  /** Kind of place in OpenStreetMap vocabulary: cafe, restaurant, museum, beach... */
  category: string | null;
  source: LocationSource;
  aliases: string[];
  createdAt: string;
}

/** A place found on the map, not stored yet. POST /api/v1/locations/resolve turns it into an ApiLocation. */
export interface PlaceSuggestion {
  name: string;
  address: string | null;
  city: string | null;
  latitude: number;
  longitude: number;
  category: string | null;
  externalId: string | null;
}

/** Answer of GET /api/v1/locations/search: the directory first, then places from OpenStreetMap. */
export interface PlaceSearchResponse {
  directory: ApiLocation[];
  external: PlaceSuggestion[];
  /** False when the map search provider could not be reached (the directory still answers). */
  externalAvailable: boolean;
}

/** Second line under a place name: its address, and its city when the address does not already say it. */
export function placeDetails(place: { city: string | null; address: string | null; name: string }): string | null {
  const { address, city, name } = place;
  const parts: string[] = [];
  if (address && address !== name) parts.push(address);
  if (city && city !== name && !(address ?? '').includes(city)) parts.push(city);
  return parts.join(' · ') || null;
}

const CATEGORY_LABELS: Record<string, string> = {
  cafe: 'Café',
  restaurant: 'Restaurant',
  fast_food: 'Restauration rapide',
  bar: 'Bar',
  ice_cream: 'Glacier',
  bakery: 'Boulangerie',
  pastry: 'Pâtisserie',
  hotel: 'Hôtel',
  guest_house: "Maison d'hôtes",
  museum: 'Musée',
  attraction: 'Site touristique',
  viewpoint: 'Point de vue',
  archaeological_site: 'Site archéologique',
  monument: 'Monument',
  place_of_worship: 'Lieu de culte',
  beach: 'Plage',
  park: 'Parc',
  marina: 'Port de plaisance',
  marketplace: 'Marché',
  mall: 'Centre commercial',
  city: 'Ville',
  town: 'Ville',
  village: 'Village',
  suburb: 'Quartier',
  neighbourhood: 'Quartier',
  street: 'Rue',
  residential: 'Rue',
};

/** French label of an OpenStreetMap category, or null when it is not a common one. */
export function categoryLabel(category: string | null): string | null {
  return category ? CATEGORY_LABELS[category] ?? null : null;
}

/** Link that opens the place in the phone's or computer's map app (Google Maps by coordinates). */
export function externalMapUrl(location: ApiLocation): string | null {
  if (location.latitude === null || location.longitude === null) return null;
  const params = new URLSearchParams({ api: '1', query: `${location.latitude},${location.longitude}` });
  return `https://www.google.com/maps/search/?${params.toString()}`;
}
