package com.careercoach.gamification.service;

/**
 * Deterministic exp-to-level curve (T3: deliberately simple/experimental).
 * The ExpEvent ledger is the source of truth, so this curve can be reworked later
 * and levels rebuilt from history.
 */
public final class LevelCurve {

    public static final long EXP_PER_LEVEL = 100L;

    private LevelCurve() {
    }

    public static int levelForExp(long totalExp) {
        if (totalExp < 0) {
            return 1;
        }
        return (int) (totalExp / EXP_PER_LEVEL) + 1;
    }
}
