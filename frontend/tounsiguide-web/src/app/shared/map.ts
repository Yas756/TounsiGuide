import type { Map as MapLibreMap, MapOptions, Marker } from 'maplibre-gl';

/**
 * Shared map setup: MapLibre GL (WebGL vector map) with the OpenFreeMap "liberty" style.
 * OpenFreeMap serves OpenStreetMap vector tiles for free, with no API key and no request limit.
 * The library (~800 kB) is loaded only when a map is first shown, so the feed stays fast.
 */
export const MAP_STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty';

/** [longitude, latitude] of Tunis: the starting view when no place is chosen. */
export const TUNIS: [number, number] = [10.1815, 36.8065];

/** Tunisia, so the map cannot be dragged to the other side of the world: [[west, south], [east, north]]. */
const TUNISIA_AREA: [[number, number], [number, number]] = [[5.5, 29.0], [13.5, 38.5]];

type MapLibre = typeof import('maplibre-gl');
let library: Promise<MapLibre> | null = null;

/** Loads MapLibre once. The worker file is copied to /maplibre/ by angular.json. */
export function loadMapLibre(): Promise<MapLibre> {
  library ??= import('maplibre-gl').then(lib => {
    lib.setWorkerUrl(new URL('maplibre/maplibre-gl-worker.mjs', document.baseURI).href);
    return lib;
  });
  return library;
}

/** Street and place labels in French when OpenStreetMap has a French name, otherwise the local one. */
const FRENCH_LABEL = ['coalesce', ['get', 'name:fr'], ['get', 'name:latin'], ['get', 'name']];

function useFrenchLabels(map: MapLibreMap): void {
  for (const layer of map.getStyle().layers ?? []) {
    if (layer.type !== 'symbol') continue;
    const textField = map.getLayoutProperty(layer.id, 'text-field');
    // Only labels made from names: house numbers and road shields keep their own text.
    if (textField && JSON.stringify(textField).includes('name')) {
      // A MapLibre style expression; the library's own type for it is not exported.
      map.setLayoutProperty(layer.id, 'text-field', FRENCH_LABEL as never);
    }
  }
}

export interface CreateMapOptions {
  center?: [number, number];
  zoom?: number;
  /** A read-only map (post page) ignores scrolling so the page can still scroll over it. */
  interactive?: boolean;
}

export async function createMap(container: HTMLElement, options: CreateMapOptions = {}): Promise<MapLibreMap> {
  const lib = await loadMapLibre();
  const interactive = options.interactive ?? true;
  const mapOptions: MapOptions = {
    container,
    style: MAP_STYLE_URL,
    center: options.center ?? TUNIS,
    zoom: options.zoom ?? 11,
    maxBounds: TUNISIA_AREA,
    attributionControl: { compact: true },
    cooperativeGestures: !interactive,
    dragRotate: false,
    pitchWithRotate: false,
    locale: {
      'NavigationControl.ZoomIn': 'Zoomer',
      'NavigationControl.ZoomOut': 'Dézoomer',
      'GeolocateControl.FindMyLocation': 'Ma position',
      'GeolocateControl.LocationNotAvailable': 'Position indisponible',
      'CooperativeGesturesHandler.WindowsHelpText': 'Utilisez Ctrl + molette pour zoomer',
      'CooperativeGesturesHandler.MacHelpText': 'Utilisez ⌘ + molette pour zoomer',
      'CooperativeGesturesHandler.MobileHelpText': 'Utilisez deux doigts pour déplacer la carte',
    },
  };
  const map = new lib.Map(mapOptions);
  map.touchZoomRotate.disableRotation();
  map.addControl(new lib.NavigationControl({ showCompass: false }), 'top-right');
  if (interactive) {
    map.addControl(
      new lib.GeolocateControl({ positionOptions: { enableHighAccuracy: true }, trackUserLocation: false }),
      'top-right',
    );
  }
  map.on('style.load', () => useFrenchLabels(map));
  return map;
}

/** The red TounsiGuide pin. */
export async function createPin(lngLat: [number, number]): Promise<Marker> {
  const lib = await loadMapLibre();
  const element = document.createElement('div');
  element.className = 'map-pin';
  element.innerHTML =
    '<svg viewBox="0 0 28 36" aria-hidden="true"><path d="M14 0C6.3 0 0 6.2 0 13.9 0 24.4 14 36 14 36s14-11.6 14-22.1C28 6.2 21.7 0 14 0z"/><circle cx="14" cy="14" r="5"/></svg>';
  return new lib.Marker({ element, anchor: 'bottom' }).setLngLat(lngLat);
}

/** Distance in meters between two [lng, lat] points (haversine). */
export function distanceMeters(a: [number, number], b: [number, number]): number {
  const rad = Math.PI / 180;
  const dLat = (b[1] - a[1]) * rad;
  const dLng = (b[0] - a[0]) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a[1] * rad) * Math.cos(b[1] * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * 6_371_000 * Math.asin(Math.min(1, Math.sqrt(h)));
}
