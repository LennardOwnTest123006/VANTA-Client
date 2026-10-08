import {
  Accessibility,
  Bell,
  Bot,
  Crosshair,
  FlaskConical,
  Gauge,
  Keyboard,
  LayoutDashboard,
  LayoutTemplate,
  type LucideIcon,
  MapPin,
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
import { formatBytes } from '../lib/format';
import {
  type LocalAiManifest,
  isLocalAiResolved,
  localAi,
  localAiDownloadHosts,
  localAiModelLabel,
  localAiPlatformLabel,
  localAiRedirectTargets,
  localAiRuntimeLabel,
  localAiRuntimeSizeRange,
} from '../lib/localAi';

/**
 * Feature catalogue of VANTA Client. Every bullet describes something the client actually does
 * (see the project brief and CHANGELOG.md); nothing here is aspirational. Numbers about the Local AI
 * (versions, sizes, hosts, licences, requirements) come from `shared/local-ai/local-ai.json`, never
 * from prose.
 */

export interface FeatureHighlight {
  /** Anchor on the features page (`/features#<id>`). */
  readonly id: string;
  readonly title: string;
  readonly icon: LucideIcon;
  readonly summary: string;
}

/**
 * The twelve cards on the home page. Titles are uppercase labels by design. Mods & Shaders and the
 * Performance pack exist from VANTA Client 1.1.0 / VANTA Launcher 1.1.0 on, Boost FPS and the pack in the
 * mods bundle from VANTA Client 1.2.0 on, Smart Boost from VANTA Client 1.3.0 on, Vanta Nexus, the Local
 * AI, waypoints and Vanta Lab from VANTA Client 1.4.0 on, so their summaries say so: the copy stays true
 * while the Download page still offers an older published release.
 */
export const featureHighlights: readonly FeatureHighlight[] = [
  {
    id: 'nexus',
    title: 'Vanta Nexus',
    icon: Bot,
    summary:
      'New in 1.4.0: one screen for the assistant, HUD Designer, profiles, performance, waypoints and Vanta Lab. The assistant runs on your own PC and changes only your client, when you ask. No cloud, no account.',
  },
  {
    id: 'performance',
    title: 'Performance',
    icon: Gauge,
    summary:
      'Live FPS, frame time, memory and distance readouts with five presets built from vanilla video options that never cap the frame rate, a one-click Boost FPS (new in 1.2.0) and Smart Boost (new in 1.3.0).',
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
      'Sixteen movable widgets (FPS, coordinates, ping, clock, armor, effects and more) arranged in a drag-and-drop editor; from 1.4.0 named layouts and six presets in the Nexus HUD Designer.',
  },
  {
    id: 'waypoints',
    title: 'Waypoints',
    icon: MapPin,
    summary:
      'New in 1.4.0: saved places per world with a name, category and colour, shown as markers with their distance on your screen. Stored in config/vanta/waypoints.json, shared with nobody.',
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
      'Switch between DEFAULT, PVP, BUILDING, PERFORMANCE and RECORDING setups in one click (from 1.4.0 also SURVIVAL and MINIMAL), or export yours as JSON.',
  },
  {
    id: 'accessibility',
    title: 'Accessibility',
    icon: Accessibility,
    summary:
      'UI scale, reduced motion, high contrast, larger text and reduced transparency; every screen works with a keyboard.',
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

/** The sixteen HUD widgets shipped with 1.0.0, plus the frame time graph of 1.4.0 (a Vanta Lab feature). */
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
  'Frame time graph (Vanta Lab, from 1.4.0)',
];

/**
 * The HUD presets of Vanta Nexus (1.4.0): the `hud.preset` action of the assistant and the preset
 * buttons of the HUD Designer apply the same widget sets. The crosshair always stays.
 */
export const nexusHudPresets: readonly { readonly name: string; readonly widgets: string }[] = [
  { name: 'Minimal', widgets: 'FPS and coordinates' },
  { name: 'PvP', widgets: 'FPS, ping, CPS, keystrokes, armor, durability and effects' },
  {
    name: 'Recording',
    widgets:
      'FPS, clock, coordinates, direction, keystrokes and version at 90 %; no server address',
  },
  {
    name: 'Survival',
    widgets: 'FPS, coordinates, direction, biome, clock, armor, durability and effects',
  },
  { name: 'Building', widgets: 'coordinates, direction, biome and clock at 90 %' },
  { name: 'Full', widgets: 'every widget' },
];

/**
 * Profiles shipped with the client. Every profile is a plain JSON file in `config/vanta/profiles/`.
 * From 1.4.0 the HUD layouts of PvP, Building, Recording, Survival and Minimal are the Nexus HUD presets
 * applied to the Default layout; Survival and Minimal are new in 1.4.0 and are added once to existing
 * installs. What each one sets is exactly what `BuiltInProfiles` in the client defines.
 */
export const builtInProfiles: readonly { readonly name: string; readonly purpose: string }[] = [
  {
    name: 'DEFAULT',
    purpose: 'The default HUD layout, the Balanced preset and the default crosshair.',
  },
  {
    name: 'PVP',
    purpose:
      'PvP HUD (FPS, ping, CPS, keystrokes, armor, durability, effects), High preset, no frame-rate cap, Bold crosshair, HUD text shadow, no menu particles.',
  },
  {
    name: 'SURVIVAL',
    purpose:
      'New in 1.4.0: Survival HUD (FPS, coordinates, direction, biome, clock, armor, durability, effects), Balanced preset, FOV 75, HUD text shadow, default crosshair.',
  },
  {
    name: 'BUILDING',
    purpose:
      'Building HUD (coordinates, direction, biome, clock at 90 %), Ultra preset, FOV 85, HUD opacity 80 %, Thin crosshair.',
  },
  {
    name: 'RECORDING',
    purpose:
      'Recording HUD (FPS, clock, coordinates, direction, keystrokes, version at 90 %; no server address), High preset, HUD opacity 85 %, 2.5 s notifications, server statistics off, version label hidden, Dot crosshair.',
  },
  {
    name: 'PERFORMANCE',
    purpose:
      'Performance HUD, the MAX FPS preset, no frame-rate cap, solid menu background, no menu particles, no HUD text shadow.',
  },
  {
    name: 'MINIMAL',
    purpose:
      'New in 1.4.0: FPS and coordinates only, Balanced preset, HUD opacity 90 %, Thin crosshair, no menu particles, 2.5 s notifications.',
  },
];

/** "11.5 MB (macOS Intel) to 19.4 MB (Windows x64)" from the manifest, or an honest placeholder. */
function runtimeSizeSentence(manifest: LocalAiManifest): string {
  const range = localAiRuntimeSizeRange(manifest);
  if (!range || !isLocalAiResolved(manifest)) return 'size recorded in the manifest once resolved';
  const smallest = formatBytes(range.smallest.size) ?? '';
  const largest = formatBytes(range.largest.size) ?? '';
  return `${smallest} (${localAiPlatformLabel(range.smallest.key)}) to ${largest} (${localAiPlatformLabel(range.largest.key)}) depending on your system`;
}

/** The Local AI bullets, built from the manifest so no version, size or host is typed by hand. */
function localAiBullets(manifest: LocalAiManifest | undefined): string[] {
  if (!manifest) {
    return [
      'The Local AI manifest is missing from this checkout; the Download page and the documentation name the runtime, the model, their sizes and hosts once it is committed',
    ];
  }
  const hosts = localAiDownloadHosts(manifest);
  const redirects = localAiRedirectTargets(manifest);
  const redirectNote =
    redirects.length > 0
      ? ` (each redirects it to its own file host, which the client follows: ${redirects.join(', ')})`
      : '';
  const modelSize = isLocalAiResolved(manifest)
    ? (formatBytes(manifest.model.size) ?? 'size not resolved yet')
    : 'size recorded in the manifest once resolved';
  const runtimeHost = new URL(manifest.runtime.platforms[0]?.url ?? manifest.runtime.sourceUrl)
    .hostname;
  const modelHost = new URL(manifest.model.url).hostname;
  return [
    `Two downloads, once, only after you agree: the ${localAiRuntimeLabel(manifest)} ${manifest.runtime.component} archive for your system (${runtimeSizeSentence(manifest)}, ${manifest.runtime.license}) from ${runtimeHost}, and the ${localAiModelLabel(manifest)} model ${manifest.model.file} (${modelSize}, ${manifest.model.license}) from ${modelHost}`,
    'Two ways to install it: the VANTA Launcher asks once at its first start and has Install, Verify files, Remove and an automatic-install switch under Settings → Local AI (it writes a note so the game uses that install read-only); without the launcher, Nexus → Install Local AI puts it into config/vanta/local-ai',
    'Every file is checked against the size and SHA-256 of the committed Local AI manifest; a quick check runs at every start, Verify files re-hashes on demand, Reinstall repairs, and Remove Local AI deletes only that folder',
    `Offline afterwards: the client starts ${manifest.runtime.component} on 127.0.0.1 on a free port with a ${manifest.model.contextSize}-token context on the CPU (no GPU offload); it stops after 10 minutes without a question (a setting, or keep it running) and starts again on demand; CPU threads automatic (at least 2, at most 8, two fewer than your cores) or a fixed number`,
    `Needs about ${manifest.requirements.diskMb} MB of disk space and ${manifest.requirements.ramMb} MB of RAM while it runs. Available for ${manifest.runtime.platforms.map((p) => localAiPlatformLabel(p.key)).join(', ')}; any other system gets an honest "Local AI is not available on this system" state and everything else keeps working`,
    `The client contacts Modrinth (Mods & Shaders) and, only when you install the Local AI, ${hosts.join(' and ')} for this download${redirectNote}. Nothing else, ever. The assistant's prompts go to 127.0.0.1 only: no cloud AI, no API key, no account, no telemetry`,
    'A small model answering on your CPU can misread a request. Nexus therefore validates every action against what really exists, lists what it did not apply and why, and keeps Undo one click away',
  ];
}

export const featureFamilies: readonly FeatureFamily[] = [
  {
    id: 'nexus',
    title: 'Vanta Nexus',
    icon: Bot,
    eyebrow: 'New in 1.4.0',
    description:
      'One screen for the assistant, the HUD Designer, profiles, performance, waypoints, Vanta Lab and its own settings. Open it with N, the Vanta Nexus button on the main menu, the command palette or /vanta nexus.',
    bullets: [
      'Seven sections in one window: AI Assistant, HUD Designer, Profiles, Performance, Waypoints, Vanta Lab and Settings; every button stays reachable in a small window, like on every VANTA screen',
      'The assistant changes your own client when you ask, in plain English: HUD layout (show, hide, move, scale, opacity, presets, saved layouts), settings of the Video, HUD, Performance and Accessibility categories, profiles (switch, or create one from the current state), performance presets and Smart Boost, waypoints (add at your position, remove, enable or disable) and Vanta Lab features',
      'Every action is checked against what really exists before it runs. What was not applied is listed with the reason, and Undo takes back the last assistant change (the last 20 turns are kept)',
      'Example prompts to start with: "Make my HUD minimal", "Only show FPS and coordinates", "Create a recording profile", "Move the FPS counter to the bottom right"',
      'It never acts in the game world, never touches other players, keybinds or the network, and never changes anything you did not ask for',
      'The conversation stays in config/vanta/nexus-chat.json on your PC (the last 50 turns); Clear chat empties it',
      'Without the Local AI the other six sections work as before; only the assistant needs it, and its status pill says so honestly: Ready, Starting, Busy, Not installed or Not available on this system',
    ],
    link: { to: '/download#local-ai', label: 'What the Local AI downloads' },
  },
  {
    id: 'local-ai',
    title: 'Local AI',
    icon: Sparkles,
    eyebrow: 'New in 1.4.0, optional',
    description: localAi
      ? `The assistant of Vanta Nexus runs on your own PC: ${localAi.runtime.component} from ${localAiRuntimeLabel(localAi)} with the ${localAiModelLabel(localAi)} model. No cloud AI, no API key, no account, no remote fallback. Nothing downloads before you click Install, and after the download it works offline.`
      : 'The assistant of Vanta Nexus runs on your own PC with a llama.cpp server and a small open model. No cloud AI, no API key, no account, no remote fallback.',
    bullets: localAiBullets(localAi),
    link: { to: '/download#local-ai', label: 'Sizes and hosts on the Download page' },
  },
  {
    id: 'main-menu',
    title: 'Main menu',
    icon: Sparkles,
    eyebrow: 'Interface',
    description:
      'A redesigned title screen that keeps every vanilla destination one click away and shows exactly what you are running.',
    bullets: [
      'PLAY, SINGLEPLAYER, MULTIPLAYER, VANTA NEXUS (from 1.4.0), OPTIONS, LANGUAGE, RESOURCE PACKS, MODS & SHADERS (from 1.1.0), ACCESSIBILITY and QUIT',
      'Returning from any vanilla screen ends on the VANTA menu, since 1.1.0 also when you cancel Create New World',
      'Version label with VANTA Client, Minecraft 1.21.11 and Fabric Loader versions',
      'Setting to fall back to the vanilla title screen at any time',
      'Menu backgrounds and particles from the cosmetics module, blur-aware and reduced-motion safe',
      'Full keyboard navigation with a visible focus ring',
      'From 1.4.0: one notice per client version when the Local AI is not installed, with Open Nexus and Not now; nothing downloads from it',
    ],
  },
  {
    id: 'hud',
    title: 'HUD widgets',
    icon: LayoutDashboard,
    eyebrow: 'HUD',
    description:
      'Sixteen widgets that read vanilla game state and nothing else, plus a frame time graph behind Vanta Lab from 1.4.0. Each one can be shown, hidden, moved, scaled and recoloured.',
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
      'Per-widget scale, opacity, text, background and accent colours, plus widget-specific options such as clock format or coordinate decimals',
      'Layout presets to start from, and reset per widget or for the whole HUD',
      'Layouts are saved per profile as JSON in config/vanta/hud/',
      'From 1.4.0 the frame time graph widget is listed only while the Vanta Lab feature is on',
    ],
  },
  {
    id: 'hud-designer',
    title: 'HUD Designer',
    icon: LayoutTemplate,
    eyebrow: 'New in 1.4.0',
    description:
      'The HUD Designer section of Vanta Nexus manages named layouts and presets. The HUD editor stays the place to move, resize, scale and set the opacity of every widget.',
    bullets: [
      ...nexusHudPresets.map(
        (preset, index) =>
          `${index === 0 ? 'Six presets, the same ones the assistant applies, each one undoable change that keeps your widget positions: ' : ''}${preset.name}: ${preset.widgets}`,
      ),
      'Built-in and saved layouts with Load, Duplicate and Delete (your own layouts), Save current under a name, and New layout from a preset',
      'Open editor leads to the HUD editor with drag, resize, anchors, per-widget scale and opacity, colours and widget options',
    ],
  },
  {
    id: 'performance',
    title: 'Performance Center',
    icon: Gauge,
    eyebrow: 'Performance',
    description:
      'See what your machine is doing and change the vanilla video options that matter, in one screen, with honest numbers.',
    bullets: [
      'Live FPS, average and 1% low frame time, used / allocated / maximum memory, render and simulation distance, entity count and CPU load',
      'MAX FPS, LOW, BALANCED, HIGH and ULTRA presets that only set vanilla video options and never cap the frame rate (1.1.0 wrote 60 / 120 FPS caps with LOW and BALANCED; fixed in 1.2.0), plus quick FPS-limit choices',
      'New in 1.2.0: one-click Boost FPS applies MAX FPS, removes the frame-rate cap, turns VSync off, uses a plain menu and installs the Performance pack when it is missing',
      'New in 1.3.0: Smart Boost measures real gameplay once after an install or update and applies the preset your PC runs smoothly: local rules, no network, never Fast / Fancy / Fabulous, the frame-rate limit or VSync, never an option you changed; Re-tune and Undo on its card',
      'New in 1.4.0: the Performance section of Vanta Nexus shows live values only: FPS now, frame time p50 and p99, hitches over 50 ms, ping (Singleplayer when there is no server), render distance, GPU, memory and the Smart Boost status, with "n/a" where a value is missing, next to the presets, Boost FPS and a link to the Performance Center',
      'Detects which preset matches your current options and shows "Custom" when you changed anything',
      'Render-distance advisor that suggests ±2 chunks from measured frame rate (never below 6 chunks), applied only when you say so',
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
    id: 'waypoints',
    title: 'Waypoints',
    icon: MapPin,
    eyebrow: 'New in 1.4.0',
    description:
      'Saved places per world, shown as markers with their distance on your screen. Stored on your PC, shared with nobody, and nothing is revealed that vanilla does not render: you place every waypoint yourself.',
    bullets: [
      'Per world: singleplayer worlds by their folder name, servers by their address; the waypoints of one world never show in another',
      'Add one at your position (coordinates prefilled) with a name of up to 48 characters, a category (Home, Base, Farm, Portal, Resource, Other or your own) and one of eight colours; edit, enable or disable, delete with confirmation',
      'Screen markers with name and distance for the enabled waypoints within the marker distance (512 blocks by default, 16 to 4096), a marker size setting and the distance on or off; nothing with F1, the F3 overlay or on a HUD-free screenshot',
      'The Waypoints section of Vanta Nexus searches, sorts by name, distance or newest, and has an All worlds view; up to 500 waypoints live in config/vanta/waypoints.json',
      'The assistant adds, removes, enables or disables waypoints of the current world when you ask',
      'Light beams in the world at every enabled waypoint are a Vanta Lab feature, off by default',
    ],
  },
  {
    id: 'lab',
    title: 'Vanta Lab',
    icon: FlaskConical,
    eyebrow: 'New in 1.4.0',
    description:
      'Optional features you switch on one by one, in Nexus → Vanta Lab or Settings → Vanta Lab. All off by default; each is a plain setting that profiles store too and the assistant can flip when you ask.',
    bullets: [
      'Dynamic HUD: the HUD fades out after 10 seconds without input while you are in a world and comes back on any input',
      'Animated crosshair: the crosshair spreads while you move and when you attack',
      'Waypoint beams: a light beam in the world at every enabled waypoint; the screen markers work without it',
      'Frame time HUD graph: a HUD element that draws the last 120 frame times as a sparkline with p50 and p99 labels, frames over 50 ms highlighted',
      'Screen transitions: VANTA screens fade and slide when they open and close; off under reduced motion',
    ],
  },
  {
    id: 'settings',
    title: 'Settings and search',
    icon: Search,
    eyebrow: 'Settings',
    description:
      'Every VANTA option lives in one categorised settings screen with a search box that also finds actions.',
    bullets: [
      'Categories: General, Video, Audio, Controls, HUD, Waypoints (1.4.0), Performance, Accessibility, Language, Cosmetics, Vanta Nexus (1.4.0), Privacy, Vanta Lab (1.4.0)',
      'Vanilla options such as GUI scale, FOV or chat opacity appear next to VANTA settings, so there is one place to look',
      'Global search over settings and actions such as "Open HUD editor" or "Open Vanta Nexus", with keyword aliases',
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
      'VANTA keys: Right Shift opens the settings, C zooms, N opens Vanta Nexus (1.4.0); all rebindable',
      'No macros, no automation: a key always does exactly one vanilla action',
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
      'Six presets (Default, Thin, Bold, Dot, Circle, Precision) and the vanilla crosshair one click away',
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
      'A profile stores settings, HUD layout, keybinds and visuals together so switching between tasks takes one click. Seven built-in profiles from 1.4.0 (five before); each changes only what it is about.',
    bullets: [
      ...builtInProfiles.map((profile) => `${profile.name}: ${profile.purpose}`),
      'Create, duplicate, rename and delete profiles; import and export as JSON',
      'From 1.4.0 the HUD layouts of PvP, Building and Recording are the Nexus HUD presets, refreshed once for built-in profiles you never changed; Survival and Minimal are added once to existing installs. Edited built-ins and your own profiles keep their content',
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
      'Worlds and servers you joined; each list can be switched off separately',
      'Pause tracking, reset or delete the data at any time from the Privacy settings',
      'Nothing is uploaded: there is no account and no telemetry',
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
      'Pure FOV change on your own camera; nothing is revealed that vanilla does not render',
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
      'Every feature is visual, quality-of-life, performance or accessibility, on your side of the screen only.',
  },
  {
    title: 'No data collection, no cloud AI',
    detail:
      'No accounts, no telemetry, no analytics. Statistics stay in your config folder, and the Vanta Nexus assistant runs on your PC: its prompts go to 127.0.0.1 only.',
  },
];
