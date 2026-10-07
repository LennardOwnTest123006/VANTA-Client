import {
  Accessibility,
  Bell,
  Crosshair,
  Gauge,
  Keyboard,
  LayoutDashboard,
  type LucideIcon,
  Package,
  Palette,
  ChartColumn,
  Puzzle,
  Search,
  SlidersHorizontal,
  Sparkles,
  UserRound,
  Zap,
  ZoomIn,
  Layers,
} from 'lucide-react';

/**
 * Feature catalogue of VANTA Client. Every bullet describes something the client actually does
 * (see the project brief and CHANGELOG.md); nothing here is aspirational.
 */

export interface FeatureHighlight {
  /** Anchor on the features page (`/features#<id>`). */
  readonly id: string;
  readonly title: string;
  readonly icon: LucideIcon;
  readonly summary: string;
}

/**
 * The ten cards on the home page. Titles are uppercase labels by design. Mods & Shaders and the
 * Performance pack exist from VANTA Client 1.1.0 / VANTA Launcher 1.1.0 on, Boost FPS and the pack in the
 * mods bundle from VANTA Client 1.2.0 on, so their summaries say so: the copy stays true while the Download
 * page still offers an older published release.
 */
export const featureHighlights: readonly FeatureHighlight[] = [
  {
    id: 'performance',
    title: 'Performance',
    icon: Gauge,
    summary:
      'Live FPS, frame time, memory and distance readouts with five presets built from vanilla video options that never cap the frame rate, and (new in 1.2.0) a one-click Boost FPS.',
  },
  {
    id: 'performance-pack',
    title: 'Performance pack',
    icon: Zap,
    summary:
      'Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling and Iris from Modrinth in one click, checked with SHA-512; from 1.2.0 the redistributable five come in the mods bundle.',
  },
  {
    id: 'mods',
    title: 'Mods & Shaders',
    icon: Puzzle,
    summary:
      'New in 1.1.0: search Modrinth in game for Fabric mods, Iris shader packs and resource packs for 1.21.11, with their dependencies.',
  },
  {
    id: 'hud',
    title: 'HUD',
    icon: LayoutDashboard,
    summary:
      'Sixteen movable widgets — FPS, coordinates, ping, clock, armor, effects and more — arranged in a drag-and-drop editor.',
  },
  {
    id: 'customization',
    title: 'Customization',
    icon: Palette,
    summary:
      'UI themes, menu backgrounds, HUD themes, menu particles and a crosshair designer. Visual only, always client-side.',
  },
  {
    id: 'profiles',
    title: 'Profiles',
    icon: UserRound,
    summary:
      'Switch between DEFAULT, PVP, BUILDING, PERFORMANCE and RECORDING setups in one click, or export yours as JSON.',
  },
  {
    id: 'accessibility',
    title: 'Accessibility',
    icon: Accessibility,
    summary:
      'UI scale, reduced motion, high contrast, larger text and reduced transparency — every screen works with a keyboard.',
  },
  {
    id: 'resource-packs',
    title: 'Resource packs',
    icon: Package,
    summary:
      'Browse, enable, reorder and apply packs from a redesigned manager that uses the vanilla pack repository underneath.',
  },
  {
    id: 'statistics',
    title: 'Statistics',
    icon: ChartColumn,
    summary:
      'Playtime, sessions and activity in a local dashboard. Stored in your config folder, never uploaded anywhere.',
  },
  {
    id: 'settings',
    title: 'Settings',
    icon: SlidersHorizontal,
    summary:
      'Categorised settings with global search, tooltips, reset-to-default and migrations so updates never lose your config.',
  },
];

export interface FeatureFamily {
  readonly id: string;
  readonly title: string;
  readonly icon: LucideIcon;
  readonly eyebrow: string;
  readonly description: string;
  readonly bullets: readonly string[];
  /** Optional link to a dedicated page. */
  readonly link?: { readonly to: string; readonly label: string };
}

/** The sixteen HUD widgets shipped with 1.0.0. */
export const hudWidgets: readonly string[] = [
  'FPS',
  'Ping',
  'Coordinates',
  'Direction',
  'Biome',
  'Server',
  'CPS',
  'Clock',
  'Armor',
  'Item durability',
  'Potion effects',
  'Keystrokes',
  'Memory',
  'CPU',
  'Entity count',
  'Minecraft version',
];

/** Profiles shipped with the client. Every profile is a plain JSON file in `config/vanta/profiles/`. */
export const builtInProfiles: readonly { readonly name: string; readonly purpose: string }[] = [
  { name: 'DEFAULT', purpose: 'Vanilla-like HUD, balanced performance preset.' },
  { name: 'PVP', purpose: 'Keystrokes, CPS, ping and armor widgets; compact crosshair.' },
  { name: 'BUILDING', purpose: 'Coordinates, direction and biome; long render distance.' },
  {
    name: 'PERFORMANCE',
    purpose: 'Minimal HUD with the MAX FPS preset applied and no frame-rate cap.',
  },
  { name: 'RECORDING', purpose: 'Clean HUD, notifications muted, no menu particles.' },
];

export const featureFamilies: readonly FeatureFamily[] = [
  {
    id: 'main-menu',
    title: 'Main menu',
    icon: Sparkles,
    eyebrow: 'Interface',
    description:
      'A redesigned title screen that keeps every vanilla destination one click away and shows exactly what you are running.',
    bullets: [
      'PLAY, SINGLEPLAYER, MULTIPLAYER, OPTIONS, LANGUAGE, RESOURCE PACKS, MODS & SHADERS (from 1.1.0), ACCESSIBILITY and QUIT',
      'Returning from any vanilla screen ends on the VANTA menu, since 1.1.0 also when you cancel Create New World',
      'Version label with VANTA Client, Minecraft 1.21.11 and Fabric Loader versions',
      'Setting to fall back to the vanilla title screen at any time',
      'Menu backgrounds and particles from the cosmetics module, blur-aware and reduced-motion safe',
      'Full keyboard navigation with a visible focus ring',
    ],
  },
  {
    id: 'hud',
    title: 'HUD widgets',
    icon: LayoutDashboard,
    eyebrow: 'HUD',
    description:
      'Sixteen widgets that read vanilla game state and nothing else. Each one can be shown, hidden, moved, scaled and recoloured.',
    bullets: hudWidgets.map((name) => name),
  },
  {
    id: 'hud-editor',
    title: 'HUD editor',
    icon: Layers,
    eyebrow: 'HUD',
    description: 'Arrange the HUD on a live preview of your screen instead of typing coordinates.',
    bullets: [
      'Drag and resize widgets on a preview of your screen; nine anchors keep positions correct on every window size and GUI scale',
      'Per-widget scale, text, background and accent colours, plus widget-specific options such as clock format or coordinate decimals',
      'Layout presets to start from, and reset per widget or for the whole HUD',
      'Layouts are saved per profile as JSON in config/vanta/hud/',
    ],
  },
  {
    id: 'performance',
    title: 'Performance Center',
    icon: Gauge,
    eyebrow: 'Performance',
    description:
      'See what your machine is doing and change the vanilla video options that matter — in one screen, with honest numbers.',
    bullets: [
      'Live FPS, average and 1% low frame time, used / allocated / maximum memory, render and simulation distance, entity count and CPU load',
      'MAX FPS, LOW, BALANCED, HIGH and ULTRA presets that only set vanilla video options and never cap the frame rate (1.1.0 wrote 60 / 120 FPS caps with LOW and BALANCED; fixed in 1.2.0), plus quick FPS-limit choices',
      'New in 1.2.0: one-click Boost FPS applies MAX FPS, removes the frame-rate cap, turns VSync off, uses a plain menu and installs the Performance pack when it is missing',
      'Detects which preset matches your current options and shows "Custom" when you changed anything',
      'Render-distance advisor that suggests ±2 chunks from measured frame rate — applied only when you say so',
      'No renderer replacement of its own and no unverified performance claims; for more, it points to the Performance pack',
    ],
    link: { to: '/performance', label: 'How the presets work' },
  },
  {
    id: 'performance-pack',
    title: 'Performance pack',
    icon: Zap,
    eyebrow: 'New in 1.1.0, in the bundle since 1.2.0',
    description:
      'Six well-known optimisation mods from Modrinth, installed in one step. They are independent projects by their own authors; VANTA installs them unmodified under their own licences.',
    bullets: [
      'Sodium (rendering engine), Lithium (game logic), FerriteCore (memory), ImmediatelyFast (HUD, text and entity rendering), EntityCulling (skips what you cannot see) and Iris Shaders (shader packs)',
      'The newest Fabric version of each for Minecraft 1.21.11 is looked up on Modrinth when you install; nothing is pinned in VANTA',
      'Every file is checked against the SHA-512 Modrinth publishes; a mod without a 1.21.11 version is skipped with a message',
      'In game: one click on the Mods & Shaders screen, with a tick box per mod. In the VANTA Launcher, from 1.1.0 on: installed by default, switchable in Settings',
      'New in 1.2.0: the mods bundle for manual installs carries the five mods whose licences allow redistribution (Sodium under PolyForm Shield, licence texts included); EntityCulling is offered in game with one click, once, at the main menu',
      'No FPS promises: how much faster the game runs depends on your computer',
    ],
    link: {
      to: '/documentation/mods-and-shaders#the-performance-pack',
      label: 'About the Performance pack',
    },
  },
  {
    id: 'mods',
    title: 'Mods & Shaders',
    icon: Puzzle,
    eyebrow: 'New in 1.1.0',
    description:
      'An in-game browser for Modrinth, and the same on the Mods page of the VANTA Launcher. Search, install, switch off and remove without leaving VANTA.',
    bullets: [
      'Tabs for Fabric mods, shader packs for Iris and resource packs, all with a version for Minecraft 1.21.11; the most downloaded first while the search is empty',
      'Required dependencies are installed with a project; every download is checked with SHA-512 and only then moved into place',
      'Disable, enable and remove what VANTA installed; files you added yourself are never changed in game',
      'A restart banner after mod changes, “Restart game” when the VANTA Launcher started the game, and “Open shader settings” for Iris',
      'Talks to Modrinth only while you use it; no account data is sent',
    ],
    link: { to: '/documentation/mods-and-shaders', label: 'How Mods & Shaders works' },
  },
  {
    id: 'settings',
    title: 'Settings and search',
    icon: Search,
    eyebrow: 'Settings',
    description:
      'Every VANTA option lives in one categorised settings screen with a search box that also finds actions.',
    bullets: [
      'Categories: General, Video, Audio, Controls, HUD, Performance, Accessibility, Language, Cosmetics, Privacy',
      'Vanilla options such as GUI scale, FOV or chat opacity appear next to VANTA settings, so there is one place to look',
      'Global search over settings and actions such as "Open HUD editor", with keyword aliases',
      'Tooltips with the default value and reset-to-default on every control',
      'JSON store with schemaVersion and migrations; your config survives updates',
    ],
  },
  {
    id: 'keybinds',
    title: 'Keybind manager',
    icon: Keyboard,
    eyebrow: 'Controls',
    description:
      'A faster way to manage the key mappings Minecraft and your mods already register.',
    bullets: [
      'Search across all categories, including other Fabric mods',
      'Rebind with key capture, reset a single key or everything',
      'Conflict detection highlights keys bound twice before you leave the screen',
      'No macros, no automation — a key always does exactly one vanilla action',
    ],
  },
  {
    id: 'crosshair',
    title: 'Crosshair',
    icon: Crosshair,
    eyebrow: 'Customization',
    description: 'Replace the vanilla crosshair with one you can actually see on your monitor.',
    bullets: [
      'Shapes: cross, dot, circle, square, chevron and cross with centre dot',
      'Size, thickness, gap, outline, opacity and colour',
      'Six presets — Default, Thin, Bold, Dot, Circle, Precision — and the vanilla crosshair one click away',
    ],
  },
  {
    id: 'customization',
    title: 'Cosmetics',
    icon: Palette,
    eyebrow: 'Customization',
    description:
      'Make the interface yours. Cosmetics are purely visual, rendered on your machine, and never visible to other players.',
    bullets: [
      'UI themes for every VANTA screen and the HUD',
      'Menu backgrounds and menu particles',
      'Profile badges shown in your own menus',
      'No paid items, no unlockables, no server-side cosmetics',
    ],
  },
  {
    id: 'profiles',
    title: 'Profiles',
    icon: UserRound,
    eyebrow: 'Workflow',
    description:
      'A profile stores settings, HUD layout, keybinds and visuals together so switching between tasks takes one click.',
    bullets: [
      ...builtInProfiles.map((profile) => `${profile.name} — ${profile.purpose}`),
      'Create, duplicate, rename and delete profiles; import and export as JSON',
    ],
  },
  {
    id: 'notifications',
    title: 'Notifications',
    icon: Bell,
    eyebrow: 'Interface',
    description: 'Quiet, consistent toasts for things that happen on your side of the screen.',
    bullets: [
      'Profile switched, preset applied, resource packs reloaded, screenshot saved',
      'Launcher update available (read from the release manifest)',
      'Position, duration and sound are configurable; everything can be muted',
      'Never used for advertising or news',
    ],
  },
  {
    id: 'resource-packs',
    title: 'Resource packs',
    icon: Package,
    eyebrow: 'Content',
    description: 'The vanilla pack repository with a manager that is pleasant to use.',
    bullets: [
      'List available and selected packs with icon, description and compatibility',
      'Enable, disable and reorder with the keyboard or the mouse',
      'Apply and reload without leaving the screen',
      'Built-in and server packs keep their vanilla rules',
    ],
  },
  {
    id: 'statistics',
    title: 'Statistics',
    icon: ChartColumn,
    eyebrow: 'Insights',
    description:
      'A local dashboard about your own play. Everything is stored in config/vanta/stats.json on your computer.',
    bullets: [
      'Playtime, sessions, distance travelled, blocks broken and placed, screenshots taken and peak FPS',
      'Worlds and servers you joined — each list can be switched off separately',
      'Pause tracking, reset or delete the data at any time from the Privacy settings',
      'Nothing is uploaded — there is no account and no telemetry',
    ],
  },
  {
    id: 'accessibility',
    title: 'Accessibility',
    icon: Accessibility,
    eyebrow: 'Accessibility',
    description: 'Built into the UI kit, so every VANTA screen benefits from the same options.',
    bullets: [
      'UI scale independent of the vanilla GUI scale',
      'Reduced motion disables animations and menu particles',
      'High contrast theme, large text, reduced transparency and colour-blind palettes',
      'Vanilla accessibility options (high contrast resource pack, text background and chat opacity) in the same screen',
      'Tab / Shift+Tab, Enter, Space, arrows and Escape work everywhere; focus is always visible',
    ],
  },
  {
    id: 'zoom',
    title: 'Zoom',
    icon: ZoomIn,
    eyebrow: 'Quality of life',
    description: 'Hold a key to lower the field of view and look at something far away.',
    bullets: [
      'Hold C (rebindable) to zoom 3× by default; the factor goes from 1.5× to 8×',
      'Scroll wheel adjusts the zoom while held; smooth transition that respects reduced motion',
      'Pure FOV change on your own camera — nothing is revealed that vanilla does not render',
    ],
  },
];

/** The lines VANTA does not cross. */
export const neverList: readonly { readonly title: string; readonly detail: string }[] = [
  {
    title: 'No cheats',
    detail:
      'No kill aura, aim assist, reach, auto-clicker, auto-crit or any other combat automation.',
  },
  {
    title: 'No packet manipulation',
    detail: 'VANTA never crafts, modifies or delays packets. Network code is untouched vanilla.',
  },
  {
    title: 'No anti-cheat bypass',
    detail: 'Nothing hides from or interferes with server-side anti-cheat systems.',
  },
  {
    title: 'No player tracking',
    detail: 'No ESP, X-ray, tracers, nametag tricks or information vanilla does not render.',
  },
  {
    title: 'No unfair advantage',
    detail:
      'Every feature is visual, quality-of-life, performance or accessibility — on your side of the screen only.',
  },
  {
    title: 'No data collection',
    detail: 'No accounts, no telemetry, no analytics. Statistics stay in your config folder.',
  },
];
