package com.cucumber;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Shows the bomb countdown on the game screen while the menu is closed. */
public final class HudOverlay {
    private HudOverlay() {}

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float ease(float p) {
        float q = 1f - p;
        return 1f - q * q * q;
    }

    private static void roundR(DrawContext c, int rx, int ry, int rw, int rh, int rad, int col) {
        rad = Math.max(0, Math.min(rad, Math.min(rw, rh) / 2));
        if (rad == 0) {
            c.fill(rx, ry, rx + rw, ry + rh, col);
            return;
        }
        for (int i = 0; i < rad; i++) {
            double dy = rad - i - 0.5;
            int inset = rad - (int) Math.round(Math.sqrt(rad * rad - dy * dy));
            c.fill(rx + inset, ry + i, rx + rw - inset, ry + i + 1, col);
            c.fill(rx + inset, ry + rh - i - 1, rx + rw - inset, ry + rh - i, col);
        }
        c.fill(rx, ry + rad, rx + rw, ry + rh - rad, col);
    }

    private static int alpha(int col, int a) {
        return (col & 0x00FFFFFF) | (a << 24);
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen instanceof CucumberScreen) return;

        long now = System.currentTimeMillis();
        boolean armed = Bomb.armed();
        long sinceFire = now - Bomb.firedAt;
        boolean boom = !armed && Bomb.firedAt > 0 && sinceFire < 1600;
        if (!armed && !boom) return;

        Theme t = Theme.ALL[Config.theme];
        int w = 176, h = 40;
        int px = (mc.getWindow().getScaledWidth() - w) / 2;

        float slide;
        if (armed) slide = -(1f - ease(clamp01((now - Bomb.armedAt) / 250f))) * (h + 20);
        else slide = -ease(clamp01((sinceFire - 1100) / 500f)) * (h + 20);
        int py = 10 + (int) slide;

        boolean danger = armed && Bomb.secondsLeft() <= 3;
        if (danger) px += ((now / 60) % 2 == 0) ? 1 : -1;

        int edge = danger
                ? alpha(0xFFFF3B52, 0x70 + (int) (0x60 * Math.abs(Math.sin(now / 120.0))))
                : alpha(t.accent(), 0x70);
        int rad = Config.corner + 2;
        roundR(ctx, px - 1, py - 1, w + 2, h + 2, rad + 1, edge);
        roundR(ctx, px, py, w, h, rad, alpha(t.bg(), 0xEE));

        var tr = mc.textRenderer;
        if (armed) {
            ctx.drawText(tr, "BOMB", px + 10, py + 8, danger ? 0xFFFF3B52 : t.accent(), false);
            ctx.drawText(tr, Bomb.target, px + 10, py + 20, t.text(), false);

            String sec = Bomb.secondsLeft() + "s";
            int tw = tr.getWidth(sec);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(px + w - 12 - tw * 2, py + 9, 0f);
            ctx.getMatrices().scale(2f, 2f, 1f);
            ctx.drawText(tr, sec, 0, 0, danger ? 0xFFFF3B52 : t.text(), false);
            ctx.getMatrices().pop();

            roundR(ctx, px + 10, py + h - 8, w - 20, 4, 2, t.panel());
            int fw = Math.max(3, (int) ((w - 20) * (1f - Bomb.fraction())));
            roundR(ctx, px + 10, py + h - 8, fw, 4, 2, danger ? 0xFFFF3B52 : t.accent());
        } else {
            String big = "BOOM";
            int tw = tr.getWidth(big);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(px + 12, py + 9, 0f);
            ctx.getMatrices().scale(2f, 2f, 1f);
            ctx.drawText(tr, big, 0, 0, 0xFFFF3B52, false);
            ctx.getMatrices().pop();
            ctx.drawText(tr, Bomb.target + " went boom", px + 12 + tw * 2 + 10, py + 16, t.text(), false);
        }
    }
}
