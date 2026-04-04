package dev.muhofy.furnaceboard.data;

/**
 * Represents the current operational state of a tracked furnace.
 * Used for UI color coding, notification triggers, and ETA display logic.
 *
 * State transitions:
 *   EMPTY → SMELTING (input added + fuel present)
 *   SMELTING → DONE (cookTime reaches cookTimeTotal)
 *   SMELTING → NO_FUEL (burnTime reaches 0 mid-smelt)
 *   DONE → EMPTY (player collects output)
 *   NO_FUEL → SMELTING (fuel added)
 */
public enum FurnaceState {

    /**
     * Furnace is actively cooking. Fuel present, input present, output not full.
     * HUD color: green (#55FF55)
     */
    SMELTING,

    /**
     * Output slot has items ready to collect.
     * Triggers a toast notification (once per transition).
     * HUD color: gold (#FFAA00)
     */
    DONE,

    /**
     * Input is present but fuel has run out.
     * HUD color: red (#FF5555)
     */
    NO_FUEL,

    /**
     * No input item in furnace. Nothing to track.
     * HUD color: gray (#AAAAAA)
     */
    EMPTY
}