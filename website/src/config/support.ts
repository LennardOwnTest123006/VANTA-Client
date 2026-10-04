import {
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
 * Support page categories. Every link points at a documentation route or a troubleshooting anchor;
 * `support.test.ts` checks each anchor against the real headings in `docs/*.md` so the page can
 * never link to a section that does not exist.
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
    summary: 'Play, Versions, Logs, Settings, updates and the command line.',
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
        label: 'Launcher problems',
        to: '/documentation/troubleshooting#launcher-problems',
        note: 'PLAY stays disabled, "Not published yet", the window does not open.',
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
    summary: 'The Performance Center, presets and honest limits.',
    guides: [
      {
        label: 'Performance Center',
        to: '/documentation/performance',
        note: 'What it measures and the exact vanilla values of each preset.',
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
        label: 'Does the Performance Center make the game faster?',
        to: '/faq#does-the-performance-center-make-the-game-faster',
        note: 'The short answer, in the FAQ.',
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
    summary: 'Downloads that are not there yet, this site and its sources.',
    guides: [
      {
        label: 'Why is there no download yet?',
        to: '/faq#why-is-there-no-download-yet',
        note: 'Releases exist only after the release workflow has published them.',
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
