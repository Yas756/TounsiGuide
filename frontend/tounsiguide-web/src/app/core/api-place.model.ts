/** A place chosen with Google Maps. Same shape in requests and responses. */
export interface ApiPlace {
  name: string;
  address: string | null;
  latitude: number;
  longitude: number;
  googlePlaceId: string | null;
}

/** Link that opens the place in Google Maps (in a new tab, or in the Maps app on a phone). */
export function googleMapsUrl(place: ApiPlace): string {
  const params = new URLSearchParams({ api: '1', query: `${place.latitude},${place.longitude}` });
  if (place.googlePlaceId) {
    params.set('query_place_id', place.googlePlaceId);
  }
  return `https://www.google.com/maps/search/?${params.toString()}`;
}
