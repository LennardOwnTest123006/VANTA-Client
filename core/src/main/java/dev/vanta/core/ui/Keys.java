package dev.vanta.core.ui;

/**
 * GLFW key codes, mouse buttons and modifier bits used by the UI kit. Values match
 * {@code org.lwjgl.glfw.GLFW} so the client can pass Minecraft's input events straight through.
 */
public final class Keys {

    private Keys() {
    }

    // ---------------------------------------------------------------- mouse buttons
    /** Left mouse button. */
    public static final int MOUSE_LEFT = 0;
    /** Right mouse button. */
    public static final int MOUSE_RIGHT = 1;
    /** Middle mouse button. */
    public static final int MOUSE_MIDDLE = 2;

    // ---------------------------------------------------------------- modifier bits
    /** Shift modifier bit. */
    public static final int MOD_SHIFT = 0x1;
    /** Control modifier bit. */
    public static final int MOD_CONTROL = 0x2;
    /** Alt modifier bit. */
    public static final int MOD_ALT = 0x4;
    /** Super (Windows/Command) modifier bit. */
    public static final int MOD_SUPER = 0x8;

    // ---------------------------------------------------------------- keys
    /** Unknown key. */
    public static final int UNKNOWN = -1;
    public static final int SPACE = 32;
    public static final int APOSTROPHE = 39;
    public static final int COMMA = 44;
    public static final int MINUS = 45;
    public static final int PERIOD = 46;
    public static final int SLASH = 47;
    public static final int KEY_0 = 48;
    public static final int KEY_1 = 49;
    public static final int KEY_2 = 50;
    public static final int KEY_3 = 51;
    public static final int KEY_4 = 52;
    public static final int KEY_5 = 53;
    public static final int KEY_6 = 54;
    public static final int KEY_7 = 55;
    public static final int KEY_8 = 56;
    public static final int KEY_9 = 57;
    public static final int SEMICOLON = 59;
    public static final int EQUAL = 61;
    public static final int A = 65;
    public static final int B = 66;
    public static final int C = 67;
    public static final int D = 68;
    public static final int E = 69;
    public static final int F = 70;
    public static final int G = 71;
    public static final int H = 72;
    public static final int I = 73;
    public static final int J = 74;
    public static final int K = 75;
    public static final int L = 76;
    public static final int M = 77;
    public static final int N = 78;
    public static final int O = 79;
    public static final int P = 80;
    public static final int Q = 81;
    public static final int R = 82;
    public static final int S = 83;
    public static final int T = 84;
    public static final int U = 85;
    public static final int V = 86;
    public static final int W = 87;
    public static final int X = 88;
    public static final int Y = 89;
    public static final int Z = 90;
    public static final int LEFT_BRACKET = 91;
    public static final int BACKSLASH = 92;
    public static final int RIGHT_BRACKET = 93;
    public static final int GRAVE_ACCENT = 96;
    public static final int ESCAPE = 256;
    public static final int ENTER = 257;
    public static final int TAB = 258;
    public static final int BACKSPACE = 259;
    public static final int INSERT = 260;
    public static final int DELETE = 261;
    public static final int RIGHT = 262;
    public static final int LEFT = 263;
    public static final int DOWN = 264;
    public static final int UP = 265;
    public static final int PAGE_UP = 266;
    public static final int PAGE_DOWN = 267;
    public static final int HOME = 268;
    public static final int END = 269;
    public static final int CAPS_LOCK = 280;
    public static final int SCROLL_LOCK = 281;
    public static final int NUM_LOCK = 282;
    public static final int PRINT_SCREEN = 283;
    public static final int PAUSE = 284;
    public static final int F1 = 290;
    public static final int F2 = 291;
    public static final int F3 = 292;
    public static final int F4 = 293;
    public static final int F5 = 294;
    public static final int F6 = 295;
    public static final int F7 = 296;
    public static final int F8 = 297;
    public static final int F9 = 298;
    public static final int F10 = 299;
    public static final int F11 = 300;
    public static final int F12 = 301;
    public static final int KP_0 = 320;
    public static final int KP_9 = 329;
    public static final int KP_DECIMAL = 330;
    public static final int KP_DIVIDE = 331;
    public static final int KP_MULTIPLY = 332;
    public static final int KP_SUBTRACT = 333;
    public static final int KP_ADD = 334;
    public static final int KP_ENTER = 335;
    public static final int KP_EQUAL = 336;
    public static final int LEFT_SHIFT = 340;
    public static final int LEFT_CONTROL = 341;
    public static final int LEFT_ALT = 342;
    public static final int LEFT_SUPER = 343;
    public static final int RIGHT_SHIFT = 344;
    public static final int RIGHT_CONTROL = 345;
    public static final int RIGHT_ALT = 346;
    public static final int RIGHT_SUPER = 347;
    public static final int MENU = 348;

    /** Whether the Shift bit is set. */
    public static boolean hasShift(int mods) {
        return (mods & MOD_SHIFT) != 0;
    }

    /** Whether the Control bit is set. On macOS the client also maps Super onto this bit. */
    public static boolean hasControl(int mods) {
        return (mods & MOD_CONTROL) != 0 || (mods & MOD_SUPER) != 0;
    }

    /** Whether the Alt bit is set. */
    public static boolean hasAlt(int mods) {
        return (mods & MOD_ALT) != 0;
    }

    /** Whether the key is Enter, keypad Enter or Space (the "activate" keys). */
    public static boolean isActivate(int key) {
        return key == ENTER || key == KP_ENTER || key == SPACE;
    }

    /** Whether the key is a modifier key (shift/control/alt/super, either side). */
    public static boolean isModifier(int key) {
        return key >= LEFT_SHIFT && key <= RIGHT_SUPER;
    }

    /** Whether the key is one of the four arrows. */
    public static boolean isArrow(int key) {
        return key == LEFT || key == RIGHT || key == UP || key == DOWN;
    }

    /** Whether a code point is printable text (excludes control characters). */
    public static boolean isPrintable(int codePoint) {
        if (codePoint < 32 || codePoint == 127) {
            return false;
        }
        int type = Character.getType(codePoint);
        return type != Character.CONTROL && type != Character.UNASSIGNED && type != Character.SURROGATE
                && type != Character.PRIVATE_USE;
    }

    /**
     * Short human-readable name for a key code, e.g. {@code "A"}, {@code "Right Shift"}, {@code "F3"},
     * {@code "Mouse 2"} is not covered (mouse buttons use a different code space in Minecraft).
     * Unknown codes yield {@code "Key <code>"}.
     */
    public static String displayName(int key) {
        if (key >= A && key <= Z) {
            return String.valueOf((char) key);
        }
        if (key >= KEY_0 && key <= KEY_9) {
            return String.valueOf((char) key);
        }
        if (key >= F1 && key <= F12) {
            return "F" + (key - F1 + 1);
        }
        if (key >= KP_0 && key <= KP_9) {
            return "Numpad " + (key - KP_0);
        }
        return switch (key) {
            case UNKNOWN -> "Not bound";
            case SPACE -> "Space";
            case APOSTROPHE -> "'";
            case COMMA -> ",";
            case MINUS -> "-";
            case PERIOD -> ".";
            case SLASH -> "/";
            case SEMICOLON -> ";";
            case EQUAL -> "=";
            case LEFT_BRACKET -> "[";
            case BACKSLASH -> "\\";
            case RIGHT_BRACKET -> "]";
            case GRAVE_ACCENT -> "`";
            case ESCAPE -> "Escape";
            case ENTER -> "Enter";
            case TAB -> "Tab";
            case BACKSPACE -> "Backspace";
            case INSERT -> "Insert";
            case DELETE -> "Delete";
            case RIGHT -> "Right";
            case LEFT -> "Left";
            case DOWN -> "Down";
            case UP -> "Up";
            case PAGE_UP -> "Page Up";
            case PAGE_DOWN -> "Page Down";
            case HOME -> "Home";
            case END -> "End";
            case CAPS_LOCK -> "Caps Lock";
            case SCROLL_LOCK -> "Scroll Lock";
            case NUM_LOCK -> "Num Lock";
            case PRINT_SCREEN -> "Print Screen";
            case PAUSE -> "Pause";
            case KP_DECIMAL -> "Numpad .";
            case KP_DIVIDE -> "Numpad /";
            case KP_MULTIPLY -> "Numpad *";
            case KP_SUBTRACT -> "Numpad -";
            case KP_ADD -> "Numpad +";
            case KP_ENTER -> "Numpad Enter";
            case KP_EQUAL -> "Numpad =";
            case LEFT_SHIFT -> "Left Shift";
            case LEFT_CONTROL -> "Left Control";
            case LEFT_ALT -> "Left Alt";
            case LEFT_SUPER -> "Left Super";
            case RIGHT_SHIFT -> "Right Shift";
            case RIGHT_CONTROL -> "Right Control";
            case RIGHT_ALT -> "Right Alt";
            case RIGHT_SUPER -> "Right Super";
            case MENU -> "Menu";
            default -> "Key " + key;
        };
    }
}
