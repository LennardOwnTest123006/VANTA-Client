/**
 * The Cosmetics screen (UI themes, menu backgrounds, HUD styles, crosshair presets, menu particles, badges and
 * installed packs) together with the appearance infrastructure shared by the screens registered through
 * {@link dev.vanta.core.screen.Screens2}: {@link dev.vanta.core.screen.cosmetics.ThemeResolver} builds the
 * {@link dev.vanta.core.ui.Theme} from the cosmetic selection and the accessibility state,
 * {@link dev.vanta.core.screen.cosmetics.ThemedScreen} is the live-themed screen base class, and
 * {@link dev.vanta.core.screen.cosmetics.EmptyState}, {@link dev.vanta.core.screen.cosmetics.SectionHeader},
 * {@link dev.vanta.core.screen.cosmetics.InfoBanner} and {@link dev.vanta.core.screen.cosmetics.CardGrid} are the
 * generic building blocks those screens compose.
 */
package dev.vanta.core.screen.cosmetics;
