/**
 * The five Performance Center presets and the vanilla video options each one sets.
 *
 * This table mirrors `dev.vanta.core.perf.PerformancePreset` in the core library value for value.
 * A preset only writes the vanilla options listed here — it never changes FOV, GUI scale, view
 * bobbing or any control setting, never the frame-rate limit or VSync (those live only in the
 * frame-rate limit chooser), and VANTA does not replace Minecraft's renderer.
 */

export type PresetId = 'MAX FPS' | 'LOW' | 'BALANCED' | 'HIGH' | 'ULTRA';

export interface PresetDefinition {
  readonly id: PresetId;
  readonly name: string;
  readonly summary: string;
  /** Who the preset is for, in one sentence. */
  readonly audience: string;
}

export const presets: readonly PresetDefinition[] = [
  {
    id: 'MAX FPS',
    name: 'Max FPS',
    summary:
      '5 chunks, every costly effect off, menu blur off — the most frames vanilla options alone can give. Boost FPS applies it in one click.',
    audience:
      'Anyone who wants frames first and will raise the render distance later if the world ends too close.',
  },
  {
    id: 'LOW',
    name: 'Low',
    summary: '6 chunks, fast graphics and no extras — light, without capping your frame rate.',
    audience: 'Integrated graphics, older laptops, or when a steady frame rate matters most.',
  },
  {
    id: 'BALANCED',
    name: 'Balanced',
    summary: 'The vanilla look at 10 chunks. The default for most machines.',
    audience: 'Mid-range desktops and gaming laptops.',
  },
  {
    id: 'HIGH',
    name: 'High',
    summary: 'Fancy graphics, 16 chunks, all particles and shadows.',
    audience: 'Dedicated GPUs with headroom to spare.',
  },
  {
    id: 'ULTRA',
    name: 'Ultra',
    summary:
      '24 chunks, entities drawn 25% further. Stays on Fancy — Fabulous is one click away if you want it.',
    audience: 'High-end systems that already run 1.21.11 far above the refresh rate.',
  },
];

export interface PresetOptionRow {
  /** Vanilla option label exactly as shown in Video Settings. */
  readonly option: string;
  /** Vanilla options key (`options.txt`). */
  readonly key: string;
  readonly values: Readonly<Record<PresetId, string>>;
}

/** Rows in the order the Performance Center lists them. Values are vanilla's display values. */
export const presetOptions: readonly PresetOptionRow[] = [
  {
    option: 'Graphics',
    key: 'graphicsMode',
    values: { 'MAX FPS': 'Fast', LOW: 'Fast', BALANCED: 'Fancy', HIGH: 'Fancy', ULTRA: 'Fancy' },
  },
  {
    option: 'Render Distance',
    key: 'renderDistance',
    values: {
      'MAX FPS': '5 chunks',
      LOW: '6 chunks',
      BALANCED: '10 chunks',
      HIGH: '16 chunks',
      ULTRA: '24 chunks',
    },
  },
  {
    option: 'Simulation Distance',
    key: 'simulationDistance',
    values: {
      'MAX FPS': '5 chunks',
      LOW: '6 chunks',
      BALANCED: '8 chunks',
      HIGH: '12 chunks',
      ULTRA: '16 chunks',
    },
  },
  {
    option: 'Smooth Lighting',
    key: 'ao',
    values: { 'MAX FPS': 'Off', LOW: 'Off', BALANCED: 'On', HIGH: 'On', ULTRA: 'On' },
  },
  {
    option: 'Clouds',
    key: 'renderClouds',
    values: { 'MAX FPS': 'Off', LOW: 'Off', BALANCED: 'Fast', HIGH: 'Fancy', ULTRA: 'Fancy' },
  },
  {
    option: 'Particles',
    key: 'particles',
    values: {
      'MAX FPS': 'Minimal',
      LOW: 'Minimal',
      BALANCED: 'Decreased',
      HIGH: 'All',
      ULTRA: 'All',
    },
  },
  {
    option: 'Entity Shadows',
    key: 'entityShadows',
    values: { 'MAX FPS': 'Off', LOW: 'Off', BALANCED: 'On', HIGH: 'On', ULTRA: 'On' },
  },
  {
    option: 'Entity Distance',
    key: 'entityDistanceScaling',
    values: { 'MAX FPS': '50%', LOW: '50%', BALANCED: '100%', HIGH: '100%', ULTRA: '125%' },
  },
  {
    option: 'Biome Blend',
    key: 'biomeBlendRadius',
    values: {
      'MAX FPS': 'Off',
      LOW: 'Off',
      BALANCED: '5x5 (2)',
      HIGH: '9x9 (4)',
      ULTRA: '13x13 (6)',
    },
  },
  {
    option: 'Mipmap Levels',
    key: 'mipmapLevels',
    values: { 'MAX FPS': '0', LOW: '0', BALANCED: '2', HIGH: '4', ULTRA: '4' },
  },
  {
    option: 'Menu Background Blur',
    key: 'menuBackgroundBlurriness',
    values: {
      'MAX FPS': 'Off (0)',
      LOW: 'unchanged',
      BALANCED: 'unchanged',
      HIGH: 'unchanged',
      ULTRA: 'unchanged',
    },
  },
];

/** Vanilla options the presets deliberately leave alone. */
export const untouchedOptions: readonly string[] = [
  'Max Framerate and VSync (only the frame-rate limit chooser sets them)',
  'FOV',
  'GUI Scale',
  'View Bobbing',
  'Brightness',
  'Fullscreen and resolution',
  'Controls and key bindings',
];

/**
 * Quick frame-rate limit choices offered next to the presets (`dev.vanta.core.perf.FpsLimitPreset`).
 * Vanilla steps the limit in tens, so "144" is applied as 140.
 */
export const fpsLimitChoices: readonly { readonly label: string; readonly detail: string }[] = [
  {
    label: 'VSync',
    detail: 'VSync on, limit unlimited — the monitor refresh rate caps the frame rate.',
  },
  { label: '60', detail: 'Limit 60 fps, VSync off.' },
  { label: '120', detail: 'Limit 120 fps, VSync off.' },
  { label: '144', detail: 'Limit 140 fps (vanilla steps in tens), VSync off.' },
  { label: '240', detail: 'Limit 240 fps, VSync off.' },
  { label: 'Unlimited', detail: 'No limit, VSync off.' },
];

/**
 * The render-distance advisor (`dev.vanta.core.perf.RenderDistanceAdvisor`), stated as its rules.
 * The target frame rate is the configured FPS limit, or 60 when the limit is unlimited.
 */
export const advisorRules: readonly { readonly condition: string; readonly suggestion: string }[] =
  [
    {
      condition: 'Average FPS over the last 10 seconds stays below 75% of the target',
      suggestion: 'Lower the render distance by 2 chunks (never below 6).',
    },
    {
      condition:
        'Average FPS over the last 10 seconds stays above 160% of the target and the frame rate is not capped by VSync or a limit',
      suggestion: 'Raise the render distance by 2 chunks (never above 32).',
    },
    {
      condition:
        'A suggestion was made less than 60 seconds ago, or the render distance just changed',
      suggestion: 'Nothing — the advisor waits for fresh samples before it speaks again.',
    },
  ];
