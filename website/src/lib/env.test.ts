import { describe, expect, it } from 'vitest';
import { DEFAULT_GITHUB_URL, readEnv, sanitizeEmail, sanitizeHttpUrl } from './env';

describe('sanitizeHttpUrl', () => {
  it('accepts http(s) URLs and trims whitespace', () => {
    expect(sanitizeHttpUrl(' https://example.com/x ')).toBe('https://example.com/x');
    expect(sanitizeHttpUrl('http://localhost:4173')).toBe('http://localhost:4173');
  });

  it('rejects empty, non-string and non-http values', () => {
    expect(sanitizeHttpUrl('')).toBeUndefined();
    expect(sanitizeHttpUrl('   ')).toBeUndefined();
    expect(sanitizeHttpUrl(undefined)).toBeUndefined();
    expect(sanitizeHttpUrl(true)).toBeUndefined();
    expect(sanitizeHttpUrl('javascript:alert(1)')).toBeUndefined();
    expect(sanitizeHttpUrl('ftp://example.com')).toBeUndefined();
    expect(sanitizeHttpUrl('not a url')).toBeUndefined();
  });
});

describe('sanitizeEmail', () => {
  it('accepts a plain address', () => {
    expect(sanitizeEmail(' hello@vanta.example ')).toBe('hello@vanta.example');
  });
  it('rejects garbage', () => {
    expect(sanitizeEmail('hello')).toBeUndefined();
    expect(sanitizeEmail('a@b')).toBeUndefined();
    expect(sanitizeEmail('')).toBeUndefined();
    expect(sanitizeEmail(42)).toBeUndefined();
  });
});

describe('readEnv', () => {
  it('returns undefined for everything that is not configured, except the GitHub default', () => {
    const env = readEnv({});
    expect(env.downloadLauncherUrl).toBeUndefined();
    expect(env.downloadClientUrl).toBeUndefined();
    expect(env.releasesBaseUrl).toBeUndefined();
    expect(env.supportEmail).toBeUndefined();
    expect(env.discordUrl).toBeUndefined();
    expect(env.githubUrl).toBe(DEFAULT_GITHUB_URL);
  });

  it('reads configured values and strips trailing slashes from the releases base URL', () => {
    const env = readEnv({
      VITE_DOWNLOAD_LAUNCHER_URL: 'https://cdn.example/launcher.msi',
      VITE_DOWNLOAD_CLIENT_URL: 'https://cdn.example/client.jar',
      VITE_RELEASES_BASE_URL: 'https://cdn.example/releases///',
      VITE_SUPPORT_EMAIL: 'support@vanta.example',
      VITE_DISCORD_URL: 'https://discord.gg/abc',
      VITE_GITHUB_URL: 'https://github.com/example/vanta',
    });
    expect(env.downloadLauncherUrl).toBe('https://cdn.example/launcher.msi');
    expect(env.downloadClientUrl).toBe('https://cdn.example/client.jar');
    expect(env.releasesBaseUrl).toBe('https://cdn.example/releases');
    expect(env.supportEmail).toBe('support@vanta.example');
    expect(env.discordUrl).toBe('https://discord.gg/abc');
    expect(env.githubUrl).toBe('https://github.com/example/vanta');
  });

  it('allows GitHub to be switched off with an explicitly empty value', () => {
    expect(readEnv({ VITE_GITHUB_URL: '' }).githubUrl).toBeUndefined();
  });
});
