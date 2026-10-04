/**
 * Building blocks shared by every VANTA screen: the {@link dev.vanta.core.screen.common.VantaUiScreen} base class,
 * navigation ({@link dev.vanta.core.screen.common.ScreenNavigator}), theme resolution
 * ({@link dev.vanta.core.screen.common.ThemeFactory}), the generic {@link dev.vanta.core.screen.common.SettingRow}
 * editor and small composite nodes (empty states, banners, link rows, section headers).
 * <p>
 * Everything here is UI-toolkit level and free of screen-specific logic so the screen packages
 * ({@code menu}, {@code settings}, {@code profiles}, {@code hud.editor}, …) can compose them freely.
 */
package dev.vanta.core.screen.common;
