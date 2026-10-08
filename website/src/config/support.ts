import {
  Bot,
  Coffee,
  Download,
  Gauge,
  Globe,
  KeyRound,
  Layers,
  type LucideIcon,
  Monitor,
} from 'lucide-react';

/**
 * Support page categories. Every link points at a documentation route, a troubleshooting anchor, a
 * FAQ question or a section of the Features or Download page; `support.test.ts` checks each anchor
 * against the real headings in `docs/*.md` and the real section ids so the page can never link to a
 * section that does not exist.
 */

export interface SupportLink {
  readonly label: string;
  /** Site-relative route, optionally with a `#anchor`. */
  readonly to: string;
  /** Short note shown under the label. */
  readonly note: string;
}

export interface SupportCategory {
  readonly id: string;
  readonly title: string;
  readonly icon: LucideIcon;
  readonly summary: string;
  readonly guides: readonly SupportLink[];
  readonly troubleshooting: readonly SupportLink[];
}

export const supportCategories: readonly SupportCategory[] = [
  {
    id: 'installation',
    title: 'Installation',
    icon: Download,
    summary: 'Downloading, verifying and installing the launcher or the client jar.',
    guides: [
      {
        label: 'Installation',
        to: '/documentation/installation',
        note: 'Download, verify the SHA-256, install, first launch, where files live.',
      },
      {
        label: 'Verify the checksum',
        to: '/documentation/installation#2-verify-the-checksum',
        note: 'certutil, shasum and sha256sum commands for every system.',
      },
      {
        label: 'Install into an existing Fabric setup',
        to: '/documentation/fabric#manual-installation-into-an-existing-fabric-profile',
        note: 'Use the client jar without the VANTA Launcher.',
      },
    ],
    troubleshooting: [
      {
        label: 'Checksum mismatch',
        to: '/documentation/troubleshooting#checksum-mismatch',
        note: 'What to do when a download does not match the published SHA-256.',
      },
      {
        label: 'SmartScreen warns about the installer',
        to: '/documentation/troubleshooting#launcher-problems',
        note: 'Expected for unsigned installers; verify first, then run.',
      },
    ],
  },
  {
    id: 'launcher',
    title: 'Launcher',
    icon: Monitor,
    summary:
      'PLAY, the Minecraft Launcher profile, Mods, Versions, Logs, Settings, updates and the command line.',
    guides: [
      {
        label: 'VANTA Launcher',
        to: '/documentation/launcher',
        note: 'Every screen of the launcher explained.',
      },
      {
        label: 'Updates and rollback',
        to: '/documentation/launcher#updates-and-rollback',
        note: 'How verified updates work and how to roll back a client version.',
      },
      {
        label: 'Command line reference',
        to: '/documentation/launcher#command-line-reference',
        note: '--install, --launch, --check-java and the exit codes.',
      },
    ],
    troubleshooting: [
      {
        label: 'The launcher does not start or Windows blocks it',
        to: '/documentation/troubleshooting#the-launcher-does-not-start-or-windows-blocks-it',
        note: 'SmartScreen, Smart App Control and startup-error.txt.',
      },
      {
        label: 'The profile does not show up in the Minecraft Launcher',
        to: '/documentation/troubleshooting#the-profile-vanta-12111-does-not-show-up-in-the-minecraft-launcher',
        note: 'Close the Minecraft Launcher completely, also from the system tray.',
      },
      {
        label: 'Launcher problems',
        to: '/documentation/troubleshooting#launcher-problems',
        note: 'PLAY via Minecraft Launcher, "Not published yet", updates and links.',
      },
      {
        label: 'Where the logs are',
        to: '/documentation/troubleshooting#where-the-logs-are',
        note: 'Launcher log, game output, crash reports and configuration.',
      },
    ],
  },
  {
    id: 'minecraft',
    title: 'Minecraft',
    icon: Layers,
    summary: 'Version, requirements and the game itself.',
    guides: [
      {
        label: 'Minecraft requirements',
        to: '/documentation/minecraft-requirements',
        note: 'Exactly Minecraft Java Edition 1.21.11; account, OS, GPU, memory, disk.',
      },
      {
        label: 'Settings',
        to: '/documentation/settings',
        note: 'Categories, search, reset, keyboard navigation, settings.json.',
      },
      {
        label: 'HUD and crosshair',
        to: '/documentation/hud',
        note: 'Widgets, the HUD editor and the crosshair customizer.',
      },
    ],
    troubleshooting: [
      {
        label: 'The game crashes on start',
        to: '/documentation/troubleshooting#the-game-crashes-on-start',
        note: 'Graphics drivers, Java version, memory and Fabric Loader errors.',
      },
      {
        label: 'Mod conflicts',
        to: '/documentation/troubleshooting#mod-conflicts',
        note: 'Two main menus, overlapping HUDs, crashes after adding a mod.',
      },
    ],
  },
  {
    id: 'fabric',
    title: 'Fabric',
    icon: Layers,
    summary: 'Fabric Loader 0.19.5, Fabric API and other mods.',
    guides: [
      {
        label: 'Fabric',
        to: '/documentation/fabric',
        note: 'What Fabric is and how the launcher installs it.',
      },
      {
        label: 'Using VANTA with other Fabric mods',
        to: '/documentation/fabric#using-vanta-with-other-fabric-mods',
        note: 'What VANTA touches and how it coexists with other mods.',
      },
    ],
    troubleshooting: [
      {
        label: 'Mod conflicts',
        to: '/documentation/troubleshooting#mod-conflicts',
        note: 'Finding the mod pair that breaks the game.',
      },
    ],
  },
  {
    id: 'performance',
    title: 'Performance',
    icon: Gauge,
    summary: 'The Performance Center, presets, the Performance pack, shaders and honest limits.',
    guides: [
      {
        label: 'Performance Center',
        to: '/documentation/performance',
        note: 'What it measures and the exact vanilla values of each preset.',
      },
      {
        label: 'Performance pack',
        to: '/documentation/mods-and-shaders#the-performance-pack',
        note: 'Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling and Iris from Modrinth.',
      },
      {
        label: 'Mods & Shaders',
        to: '/documentation/mods-and-shaders',
        note: 'Mods, shader packs and resource packs from Modrinth, in game and in the launcher.',
      },
      {
        label: 'Render distance suggestions',
        to: '/documentation/performance#render-distance-suggestions',
        note: 'When the advisor speaks up and why it never applies anything by itself.',
      },
      {
        label: 'Honest limits',
        to: '/documentation/performance#honest-limits',
        note: 'Vanilla options only, no renderer replacement, no FPS claims.',
      },
    ],
    troubleshooting: [
      {
        label: 'Low FPS or exactly 30 FPS after a preset or profile in 1.1.0',
        to: '/documentation/troubleshooting#low-fps-or-exactly-30-fps-after-choosing-a-preset-or-profile-in-vanta-110',
        note: 'The caps 1.1.0 wrote, fixed in 1.2.0; choose Unlimited once or press Boost FPS.',
      },
      {
        label: 'Buttons that cannot be clicked in a small window',
        to: '/documentation/troubleshooting#buttons-that-cannot-be-clicked-in-a-small-window',
        note: 'Mods & Shaders, main menu, Settings and HUD editor at small sizes; fixed in 1.2.0.',
      },
      {
        label: 'Does the Performance Center make the game faster?',
        to: '/faq#does-the-performance-center-make-the-game-faster',
        note: 'The short answer, in the FAQ.',
      },
      {
        label: 'Mods & Shaders problems',
        to: '/documentation/troubleshooting#mods--shaders-problems',
        note: 'Modrinth unreachable, a mod that breaks the game, shaders that do nothing.',
      },
    ],
  },
  {
    id: 'nexus',
    title: 'Vanta Nexus and Local AI',
    icon: Bot,
    summary:
      'The assistant of client 1.4.0, the optional Local AI it runs on, what is downloaded and what never leaves your PC.',
    guides: [
      {
        label: 'What Vanta Nexus is',
        to: '/features#nexus',
        note: 'The seven sections, what the assistant may change, Undo, and what it never does.',
      },
      {
        label: 'The Local AI, strictly local',
        to: '/features#local-ai',
        note: 'llama-server and the model on your PC; the two hosts contacted for the one-time download; no cloud, no account.',
      },
      {
        label: 'What the first start downloads',
        to: '/download#local-ai',
        note: 'Sizes per system, hosts, licences and requirements from the Local AI manifest; optional, and it asks first.',
      },
      {
        label: 'Privacy',
        to: '/privacy',
        note: 'Which hosts the client contacts, and that prompts go to 127.0.0.1 only.',
      },
    ],
    troubleshooting: [
      {
        label: 'A Local AI file does not match its checksum',
        to: '/documentation/troubleshooting#checksum-mismatch',
        note: 'The installer deletes a runtime or model file whose size or SHA-256 differs from the manifest and stops; Retry downloads it again.',
      },
      {
        label: 'Where the logs are',
        to: '/documentation/troubleshooting#where-the-logs-are',
        note: 'Launcher log, game output and crash reports. The Local AI writes its own log to logs/llama-server.log inside its folder.',
      },
      {
        label: 'How to report a problem',
        to: '/documentation/troubleshooting#how-to-report-a-problem',
        note: 'What to include so a report about the assistant or the Local AI can be acted on.',
      },
    ],
  },
  {
    id: 'account',
    title: 'Account',
    icon: KeyRound,
    summary: 'Microsoft sign-in, ownership checks and stored tokens.',
    guides: [
      {
        label: 'Account sign-in',
        to: '/documentation/launcher#account-sign-in',
        note: 'The device code flow, step by step.',
      },
      {
        label: 'Microsoft client id',
        to: '/documentation/launcher#microsoft-client-id',
        note: 'Why sign-in can be "not configured" and how to configure it.',
      },
      {
        label: 'What the launcher stores',
        to: '/privacy',
        note: 'Encrypted tokens, profile name, UUID — never a password.',
      },
    ],
    troubleshooting: [
      {
        label: 'Microsoft sign-in errors',
        to: '/documentation/troubleshooting#microsoft-sign-in-errors',
        note: 'XErr codes, child accounts, regions, accounts without the game.',
      },
    ],
  },
  {
    id: 'java',
    title: 'Java 21',
    icon: Coffee,
    summary: 'Finding, installing and selecting the right Java.',
    guides: [
      {
        label: 'Java 21',
        to: '/documentation/java-21',
        note: 'Why Java 21, how to check, install options, PATH and JAVA_HOME.',
      },
      {
        label: 'Java detection and Temurin installation',
        to: '/documentation/launcher#java-detection-and-temurin-installation',
        note: 'How the launcher scans for runtimes and installs a verified one.',
      },
    ],
    troubleshooting: [
      {
        label: 'The launcher cannot find Java',
        to: '/documentation/troubleshooting#the-launcher-cannot-find-java',
        note: 'From the one-click Temurin install to reading JavaDetector log lines.',
      },
    ],
  },
  {
    id: 'website',
    title: 'Website',
    icon: Globe,
    summary: 'Where to download VANTA, what "Not published yet" means, this site and its sources.',
    guides: [
      {
        label: 'Where do I download VANTA?',
        to: '/documentation/faq#where-do-i-download-vanta',
        note: 'The Download page and GitHub Releases. "Not published yet" marks a version whose files are not out yet; the current release stays available.',
      },
      {
        label: 'Privacy',
        to: '/privacy',
        note: 'No analytics, no cookies, no third-party scripts.',
      },
      {
        label: 'Building from source',
        to: '/documentation/building-from-source',
        note: 'Build the client, the launcher and this website from a clean checkout.',
      },
    ],
    troubleshooting: [
      {
        label: 'How to report a problem',
        to: '/documentation/troubleshooting#how-to-report-a-problem',
        note: 'What to include so a report can be acted on.',
      },
    ],
  },
];
