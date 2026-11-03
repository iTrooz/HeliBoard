// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin;

import android.view.Window;
import android.view.WindowManager;

/** Temporarily dims the host behind the IME without changing window focus. */
final class EmojiSearchWindowDim {
    private static final float DIM_AMOUNT = 0.25f;
    private Window window;
    private boolean wasDimmed;
    private float previousAmount;

    void open(final Window target) {
        if (window != null || target == null) return;
        window = target;
        final WindowManager.LayoutParams attributes = target.getAttributes();
        wasDimmed = (attributes.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0;
        previousAmount = attributes.dimAmount;
        target.setDimAmount(DIM_AMOUNT);
        target.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    void close() {
        if (window == null) return;
        window.setDimAmount(previousAmount);
        if (wasDimmed) window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        else window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window = null;
    }
}
