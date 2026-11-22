import type { StyleSpecification } from 'maplibre-gl';
import { patchStyle } from './map';

describe('patchStyle', () => {
  it('makes road shield filters null-safe without changing other layers', () => {
    const style = {
      version: 8,
      sources: {},
      layers: [
        {
          id: 'road_shield_us',
          type: 'symbol',
          source: 'openmaptiles',
          filter: ['all', ['<=', ['get', 'ref_length'], 6], ['match', ['get', 'network'], ['us-highway'], true, false]],
        },
        { id: 'water', type: 'fill', source: 'openmaptiles', filter: ['==', ['get', 'class'], 'ocean'] },
        { id: 'background', type: 'background' },
      ],
    } as unknown as StyleSpecification;

    const patched = patchStyle(style);

    expect((patched.layers[0] as { filter: unknown }).filter).toEqual([
      'all',
      ['<=', ['coalesce', ['get', 'ref_length'], 99], 6],
      ['match', ['get', 'network'], ['us-highway'], true, false],
    ]);
    expect(patched.layers[1]).toEqual(style.layers[1]);
    expect(patched.layers[2]).toEqual(style.layers[2]);
  });
});
