package com.cucumber;

import net.minecraft.client.MinecraftClient;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class CucumberScreen extends Screen {
    private static final int W = 640, H = 340, SIDE = 110, ROW = 22;
    // vertical positions inside the detail panel (relative to list top)
    private static final int BOMB_Y = 188, TIMER_Y = 224, TRACK_Y = 240, BTN_Y = 248;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final String[] SLOT_NAMES = {"Helmet", "Chestplate", "Leggings", "Boots", "Main hand", "Off hand"};
    private static final int MIN_RADIUS = 100, MAX_RADIUS = 10000;
    private static final String[] TABS = {"Players", "Target", "Themes", "Settings", "About"};
    private static final String[] SUBTITLES = {"players nearby", "anyone online", "pick a theme", "tweak the menu", "info"};

    // slider ids
    private static final int S_RADIUS = 0, S_DIM = 1, S_CORNER = 2, S_DELAY = 3;

    private static int tab = 0;

    private int x, y;
    private float s = 1f; // auto-shrink so the GUI always fits the screen
    private final long openedAt = System.currentTimeMillis();
    private int scroll = 0;
    private int dragging = -1;
    private UUID selected = null;
    private UUID clickedId = null;
    private long clickedAt = 0;
    private long bombClickAt = 0;
    private String targetText = "";
    private boolean fieldFocused = false;
    private int scroll2 = 0;
    private int slotSel = -1;
    private List<PlayerEntity> players = new ArrayList<>();

    public CucumberScreen() {
        super(Text.literal("cucumber"));
    }

    @Override
    protected void init() {
        s = Math.min(1f, Math.min((width - 8f) / W, (height - 8f) / H));
        x = (int) ((width / s - W) / 2);
        y = (int) ((height / s - H) / 2);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        Config.save();
    }

    private Theme theme() {
        return Theme.ALL[Config.theme];
    }

    // ---------- geometry ----------
    private int cx() { return x + SIDE + 14; }
    private int contentW() { return W - SIDE - 28; }
    private int listY() { return y + 50; }
    private int listW() { return 250; }
    private int detailX() { return cx() + listW() + 12; }
    private int bottom() { return y + H - 14; }
    private int visibleRows() { return (bottom() - listY()) / ROW; }
    private int r() { return Config.corner; }

    /** returns {trackX, trackY, trackW} */
    private int[] track(int id) {
        if (id == S_RADIUS) {
            int sx = cx() + 170;
            return new int[]{sx, y + 32, x + W - 14 - sx};
        }
        if (id == S_DELAY && tab <= 1) {
            int dx = detailX();
            return new int[]{dx + 12, listY() + TRACK_Y, x + W - 14 - dx - 24};
        }
        int row = id == S_DIM ? 3 : (id == S_CORNER ? 4 : 6);
        int ry = listY() + row * 32;
        return new int[]{cx() + contentW() - 170, ry + 12, 150};
    }

    private double fraction(int id) {
        switch (id) {
            case S_RADIUS: return Math.log((double) Config.radius / MIN_RADIUS) / Math.log((double) MAX_RADIUS / MIN_RADIUS);
            case S_DIM: return Config.dim / 90.0;
            case S_DELAY: return (Config.bombDelay - 1) / 119.0;
            default: return Config.corner / 10.0;
        }
    }

    private void setFromMouse(int id, double mx) {
        int[] tr = track(id);
        double f = Math.max(0.0, Math.min(1.0, (mx - tr[0]) / tr[2]));
        switch (id) {
            case S_RADIUS: {
                double v = MIN_RADIUS * Math.pow((double) MAX_RADIUS / MIN_RADIUS, f);
                int step = v < 1000 ? 10 : 100;
                Config.radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, (int) (Math.round(v / step) * step)));
                break;
            }
            case S_DIM: Config.dim = (int) Math.round(f * 90); break;
            case S_DELAY: Config.bombDelay = 1 + (int) Math.round(f * 119); break;
            default: Config.corner = (int) Math.round(f * 10); break;
        }
    }

    // ---------- drawing helpers ----------
    private void roundR(DrawContext c, int rx, int ry, int rw, int rh, int rad, int col) {
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

    private void round(DrawContext c, int rx, int ry, int rw, int rh, int col) {
        roundR(c, rx, ry, rw, rh, r(), col);
    }

    private static int alpha(int col, int a) {
        return (col & 0x00FFFFFF) | (a << 24);
    }

    private static int mix(int a, int b, float f) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int rr = (int) (ar + (br - ar) * f), rg = (int) (ag + (bg - ag) * f), rb = (int) (ab + (bb - ab) * f);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }

    private float prog(long since, int durMs) {
        if (since == 0) return 1f;
        return Math.max(0f, Math.min(1f, (System.currentTimeMillis() - since) / (float) durMs));
    }

    private float easeBack(float p) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float q = p - 1f;
        return 1f + c3 * q * q * q + c1 * q * q;
    }

    private float ease(float p) {
        float q = 1f - p;
        return 1f - q * q * q;
    }

    private boolean in(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    private void text(DrawContext c, String s, int tx, int ty, int col) {
        c.drawText(textRenderer, s, tx, ty, col, false);
    }

    private void textRight(DrawContext c, String s, int rightX, int ty, int col) {
        c.drawText(textRenderer, s, rightX - textRenderer.getWidth(s), ty, col, false);
    }

    private void refreshPlayers() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            players = new ArrayList<>();
            return;
        }
        List<PlayerEntity> list = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && mc.player.distanceTo(p) <= Config.radius) list.add(p);
        }
        if (Config.sortByName) {
            list.sort(Comparator.comparing((PlayerEntity p) -> p.getName().getString().toLowerCase()));
        } else {
            list.sort(Comparator.comparingDouble(mc.player::distanceTo));
        }
        players = list;
        int max = Math.max(0, players.size() - visibleRows());
        scroll = Math.max(0, Math.min(scroll, max));
    }

    // ---------- render ----------
    @Override
    public void render(DrawContext ctx, int rawMx, int rawMy, float delta) {
        int mx = (int) (rawMx / s), my = (int) (rawMy / s);
        Theme t = theme();
        refreshPlayers();

        float oe = ease(prog(openedAt, 300));
        float ob = easeBack(prog(openedAt, 380));
        ctx.fill(0, 0, width, height, ((int) (Config.dim * 255 / 100 * oe)) << 24);
        ctx.getMatrices().push();
        ctx.getMatrices().scale(s, s, 1f);
        ctx.getMatrices().translate(0f, (1f - oe) * 18f, 0f);
        float k = 0.80f + 0.20f * ob;
        float mcx = x + W / 2f, mcy = y + H / 2f;
        ctx.getMatrices().translate(mcx, mcy, 0f);
        ctx.getMatrices().scale(k, k, 1f);
        ctx.getMatrices().translate(-mcx, -mcy, 0f);

        int wr = r() + 2;
        roundR(ctx, x - 1, y - 1, W + 2, H + 2, wr + 1, alpha(t.accent(), 0x55 + (int) (0xAA * (1f - oe))));
        roundR(ctx, x, y, W, H, wr, t.bg());
        roundR(ctx, x, y, SIDE, H, wr, t.sidebar());
        ctx.fill(x + SIDE - wr, y, x + SIDE, y + H, t.sidebar());

        // logo
        ctx.getMatrices().push();
        ctx.getMatrices().scale(1.5f, 1.5f, 1f);
        ctx.drawText(textRenderer, "cucumber", (int) ((x + 12) / 1.5f), (int) ((y + 14) / 1.5f), t.accent(), false);
        ctx.getMatrices().pop();
        roundR(ctx, x + 12, y + 40, SIDE - 24, 1, 0, t.panel());
        int segW = 30, lineW = SIDE - 24;
        int pos = (int) ((System.currentTimeMillis() / 12) % (lineW + segW)) - segW;
        int sx0 = Math.max(0, pos), sx1 = Math.min(lineW, pos + segW);
        if (sx1 > sx0) ctx.fill(x + 12 + sx0, y + 40, x + 12 + sx1, y + 41, t.accent());

        // tabs
        for (int i = 0; i < TABS.length; i++) {
            int ty = y + 54 + i * 26;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(-(1f - ease(prog(openedAt + i * 50L, 260))) * 24f, 0f, 0f);
            boolean active = tab == i;
            boolean hover = in(mx, my, x + 8, ty, SIDE - 16, 22);
            if (active) round(ctx, x + 8, ty, SIDE - 16, 22, t.panel());
            else if (hover) round(ctx, x + 8, ty, SIDE - 16, 22, alpha(t.panel(), 0x90));
            roundR(ctx, x + 16, ty + 9, 4, 4, 2, active ? t.accent() : t.dim());
            text(ctx, TABS[i], x + 26, ty + 7, active ? t.text() : t.dim());
            ctx.getMatrices().pop();
        }
        text(ctx, "radius: " + Config.radius, x + 12, y + H - 16, t.dim());

        // header
        text(ctx, TABS[tab], cx(), y + 16, t.text());
        text(ctx, SUBTITLES[tab], cx(), y + 28, t.dim());

        switch (tab) {
            case 0: renderSlider(ctx, S_RADIUS, t); renderPlayers(ctx, mx, my, t); break;
            case 1: renderTarget(ctx, mx, my, t); break;
            case 2: renderThemes(ctx, mx, my, t); break;
            case 3: renderSettings(ctx, mx, my, t); break;
            default: renderAbout(ctx, t); break;
        }
        // white flash when the bomb goes off
        if (Bomb.firedAt > 0) {
            float fp = (System.currentTimeMillis() - Bomb.firedAt) / 600f;
            if (fp < 1f) roundR(ctx, x, y, W, H, r() + 2, ((int) (0x70 * (1f - fp)) << 24) | 0xFFFFFF);
        }
        ctx.getMatrices().pop();
    }

    private void renderSlider(DrawContext ctx, int id, Theme t) {
        int[] tr = track(id);
        int fillW = (int) (tr[2] * fraction(id));
        roundR(ctx, tr[0], tr[1], tr[2], 4, 2, t.panel());
        roundR(ctx, tr[0], tr[1], Math.max(4, fillW), 4, 2, t.accent());
        roundR(ctx, tr[0] + fillW - 4, tr[1] - 3, 8, 10, 4, dragging == id ? t.accent() : t.text());
    }

    private void renderPlayers(DrawContext ctx, int mx, int my, Theme t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int[] tr = track(S_RADIUS);
        text(ctx, "Radius", tr[0], y + 16, t.dim());
        textRight(ctx, Config.radius + " blocks", tr[0] + tr[2], y + 16, t.text());

        int lx = cx(), ly = listY(), lw = listW();

        if (players.isEmpty()) {
            round(ctx, lx, ly, lw, 28, t.panel());
            text(ctx, "Nobody within " + Config.radius + " blocks", lx + 10, ly + 10, t.dim());
        }

        int end = Math.min(players.size(), scroll + visibleRows());
        for (int i = scroll; i < end; i++) {
            PlayerEntity p = players.get(i);
            int ry = ly + (i - scroll) * ROW;
            boolean sel = p.getUuid().equals(selected);
            boolean hover = in(mx, my, lx, ry, lw, ROW - 2);
            int bg = sel ? mix(t.panel(), t.accent(), 0.30f) : (hover ? mix(t.panel(), t.accent(), 0.12f) : t.panel());
            round(ctx, lx, ry, lw, ROW - 2, bg);
            if (p.getUuid().equals(clickedId)) {
                float pr = prog(clickedAt, 350);
                if (pr < 1f) {
                    int sw = Math.max(2, (int) (lw * ease(pr)));
                    roundR(ctx, lx, ry, sw, ROW - 2, r(), alpha(t.accent(), (int) (0x70 * (1f - pr))));
                }
            }
            if (sel) {
                int barW = (int) Math.ceil(3 * ease(prog(clickedAt, 200)));
                if (barW > 0) roundR(ctx, lx, ry + 4, barW, ROW - 10, 1, t.accent());
            }

            String name = p.getName().getString();
            drawHead(ctx, p.getUuid(), name, lx + 7, ry + 3, 14, tier(p.getArmor(), t), t);
            text(ctx, name, lx + 27, ry + 6, t.text());
            drawArmorRow(ctx, p, lx + lw - 86, ry + 6, t);
            if (Config.showDistance) {
                textRight(ctx, (int) mc.player.distanceTo(p) + "m", lx + lw - 8, ry + 6, t.dim());
            }
        }

        // detail panel
        int dx = detailX(), dw = x + W - 14 - dx;
        round(ctx, dx, ly, dw, bottom() - ly, t.panel());
        drawTimerControls(ctx, dx, ly, dw, mx, my, t);

        PlayerEntity sp = getSelectedPlayer();
        if (sp == null) {
            text(ctx, "Select a player", dx + 12, ly + 12, t.dim());
            text(ctx, "to see armor and enchants", dx + 12, ly + 24, t.dim());
            return;
        }
        renderDetail(ctx, mx, my, t, sp.getUuid(), sp.getName().getString(), sp,
                (int) mc.player.distanceTo(sp) + " blocks away", dx, ly, dw);
    }

    // ---------- heads, armor, details ----------
    private static int tier(int armor, Theme t) {
        if (armor <= 0) return (t.dim() & 0x00FFFFFF) | 0xAA000000;
        if (armor < 6) return 0xFF8BC34A;
        if (armor < 12) return 0xFFFFC107;
        if (armor < 18) return 0xFFFF9800;
        return 0xFFFF3B52;
    }

    private void drawHead(DrawContext ctx, UUID id, String name, int hx, int hy, int size, int frame, Theme t) {
        roundR(ctx, hx - 1, hy - 1, size + 2, size + 2, Math.min(r(), size / 3), frame);
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerListEntry e = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(id);
        if (e != null) {
            PlayerSkinDrawer.draw(ctx, e.getSkinTextures(), hx, hy, size);
        } else {
            roundR(ctx, hx, hy, size, size, size / 2, t.accent());
            String ini = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            text(ctx, ini, hx + (size - textRenderer.getWidth(ini)) / 2, hy + (size - 8) / 2, 0xFFFFFFFF);
        }
    }

    private void drawArmorRow(DrawContext ctx, PlayerEntity p, int ax, int ay, Theme t) {
        for (int i = 0; i < 4; i++) {
            ItemStack st = p.getEquippedStack(SLOTS[i]);
            int cx0 = ax + i * 10;
            if (st.isEmpty()) {
                roundR(ctx, cx0 + 1, ay + 1, 6, 6, 2, alpha(t.dim(), 0x70));
            } else {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(cx0, ay, 0f);
                ctx.getMatrices().scale(0.5f, 0.5f, 1f);
                ctx.drawItem(st, 0, 0);
                ctx.getMatrices().pop();
            }
        }
    }

    private boolean slotClick(double mx, double my) {
        int dx = detailX(), ly = listY();
        for (int i = 0; i < 6; i++) {
            if (in(mx, my, dx + 12 + i * 36, ly + 58, 32, 32)) {
                slotSel = i;
                return true;
            }
        }
        return false;
    }

    private void renderDetail(DrawContext ctx, int mx, int my, Theme t, UUID id, String name,
                              PlayerEntity p, String status, int dx, int ly, int dw) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate((1f - ease(prog(clickedAt, 250))) * 14f, 0f, 0f);

        int armor = p == null ? 0 : p.getArmor();
        drawHead(ctx, id, name, dx + 12, ly + 10, 32, p == null ? t.accent() : tier(armor, t), t);
        text(ctx, name, dx + 52, ly + 12, t.text());
        text(ctx, status, dx + 52, ly + 24, t.dim());

        if (p != null && Config.showHealth) {
            float hp = Math.max(0f, Math.min(1f, p.getHealth() / Math.max(1f, p.getMaxHealth())));
            int bx = dx + 52, bw = dw - 64;
            roundR(ctx, bx, ly + 36, bw, 6, 3, t.bg());
            roundR(ctx, bx, ly + 36, Math.max(3, (int) (bw * hp)), 6, 3, mix(0xFFFF3B52, 0xFF4CD964, hp));
            text(ctx, "HP " + (int) p.getHealth() + "/" + (int) p.getMaxHealth() + "    Armor " + armor,
                    dx + 12, ly + 47, t.dim());
        }

        if (p == null) {
            text(ctx, "Out of range, no gear info", dx + 12, ly + 66, t.dim());
        } else {
            int sel = slotSel;
            if (sel < 0) {
                sel = 0;
                for (int i = 0; i < 6; i++) {
                    if (!p.getEquippedStack(SLOTS[i]).isEmpty()) { sel = i; break; }
                }
            }
            // slot tiles
            for (int i = 0; i < 6; i++) {
                int tx = dx + 12 + i * 36, ty = ly + 58;
                boolean isSel = i == sel;
                boolean hover = in(mx, my, tx, ty, 32, 32);
                if (isSel) roundR(ctx, tx - 1, ty - 1, 34, 34, r(), t.accent());
                roundR(ctx, tx, ty, 32, 32, r(), hover ? mix(t.bg(), t.accent(), 0.25f) : t.bg());
                ItemStack st = p.getEquippedStack(SLOTS[i]);
                if (st.isEmpty()) {
                    roundR(ctx, tx + 12, ty + 12, 8, 8, 4, alpha(t.dim(), 0x60));
                } else {
                    ctx.drawItem(st, tx + 8, ty + 8);
                }
            }

            // selected slot info
            ItemStack st = p.getEquippedStack(SLOTS[sel]);
            text(ctx, SLOT_NAMES[sel], dx + 12, ly + 96, t.dim());
            if (st.isEmpty()) {
                text(ctx, "Empty", dx + 12, ly + 112, t.dim());
            } else {
                if (st.isDamageable()) {
                    int max = st.getMaxDamage(), left = max - st.getDamage();
                    float f = Math.max(0f, Math.min(1f, left / (float) max));
                    textRight(ctx, left + "/" + max, dx + dw - 12, ly + 96, t.dim());
                    roundR(ctx, dx + 12, ly + 108, dw - 24, 4, 2, t.bg());
                    roundR(ctx, dx + 12, ly + 108, Math.max(2, (int) ((dw - 24) * f)), 4, 2, mix(0xFFFF3B52, 0xFF4CD964, f));
                }
                text(ctx, st.getName().getString(), dx + 12, ly + 116, t.text());

                var comp = st.getEnchantments();
                int n = 0, total = comp.getSize();
                for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : comp.getEnchantmentEntries()) {
                    if (n >= 5) break;
                    text(ctx, Enchantment.getName(e.getKey(), e.getIntValue()).getString(), dx + 12, ly + 129 + n * 10, t.accent());
                    n++;
                }
                if (total == 0) text(ctx, "No enchants", dx + 12, ly + 129, t.dim());
                else if (total > 5) text(ctx, "+" + (total - 5) + " more", dx + 12, ly + 129 + 5 * 10, t.dim());
            }
        }

        drawBombButton(ctx, dx, ly, dw, mx, my, t);
        ctx.getMatrices().pop();
    }

    private void drawTimerControls(DrawContext ctx, int dx, int ly, int dw, int mx, int my, Theme t) {
        // timer controls (always visible)
        text(ctx, "Timer", dx + 12, ly + TIMER_Y, t.dim());
        textRight(ctx, Config.bombDelay + "s", dx + dw - 12, ly + TIMER_Y, t.text());
        renderSlider(ctx, S_DELAY, t);
        button(ctx, dx + 12, ly + BTN_Y, 24, 18, "-", mx, my, t);
        button(ctx, dx + dw - 36, ly + BTN_Y, 24, 18, "+", mx, my, t);
    }

    private void drawBombButton(DrawContext ctx, int dx, int ly, int dw, int mx, int my, Theme t) {
        int bx = dx + 12, by = ly + BOMB_Y, bw = dw - 24, bh = 26;
        boolean hover = in(mx, my, bx, by, bw, bh);
        boolean armed = Bomb.armed();

        // press animation: squash then release, with a white flash
        float bp = prog(bombClickAt, 260);
        int inset = bp < 1f ? (int) Math.round(3 * Math.sin(Math.PI * bp)) : 0;
        // shake during the last 3 seconds
        int shake = (armed && Bomb.secondsLeft() <= 3) ? ((System.currentTimeMillis() / 60) % 2 == 0 ? 1 : -1) : 0;
        int rx = bx + inset + shake, ry2 = by + inset, rw = bw - 2 * inset, rh = bh - 2 * inset;

        int base = armed ? alpha(t.accent(), 0x44) : (hover ? t.accent() : alpha(t.accent(), 0xCC));
        int col = bp < 1f ? mix(t.accent(), 0xFFFFFFFF, 0.6f * (1f - bp)) : base;
        roundR(ctx, rx, ry2, rw, rh, r() + 1, col);
        if (armed) {
            // fills up as the timer runs out
            int fw = Math.max(2, (int) (rw * (1f - Bomb.fraction())));
            roundR(ctx, rx, ry2, fw, rh, r() + 1, alpha(t.accent(), 0xCC));
        }
        String label = armed ? "BOMB  " + Bomb.secondsLeft() + "s" : "BOMB";
        text(ctx, label, rx + (rw - textRenderer.getWidth(label)) / 2, ry2 + (rh - 8) / 2, 0xFFFFFFFF);
    }

    private List<PlayerListEntry> allOnline() {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<PlayerListEntry> out = new ArrayList<>();
        if (mc.getNetworkHandler() == null) return out;
        String me = mc.player == null ? "" : mc.player.getName().getString();
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
            if (!e.getProfile().getName().equalsIgnoreCase(me)) out.add(e);
        }
        out.sort(Comparator.comparing((PlayerListEntry e) -> e.getProfile().getName().toLowerCase()));
        return out;
    }

    private PlayerListEntry findTarget() {
        if (targetText.isEmpty()) return null;
        for (PlayerListEntry e : allOnline()) {
            if (e.getProfile().getName().equalsIgnoreCase(targetText)) return e;
        }
        return null;
    }

    private List<PlayerListEntry> filteredOnline() {
        List<PlayerListEntry> all = allOnline();
        if (targetText.isEmpty() || findTarget() != null) return all;
        String f = targetText.toLowerCase();
        List<PlayerListEntry> out = new ArrayList<>();
        for (PlayerListEntry e : all) {
            if (e.getProfile().getName().toLowerCase().contains(f)) out.add(e);
        }
        return out;
    }

    private int targetRows() { return (bottom() - (listY() + 30)) / ROW; }

    private void renderTarget(DrawContext ctx, int mx, int my, Theme t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int lx = cx(), ly = listY(), lw = listW();

        // text field
        if (fieldFocused) roundR(ctx, lx - 1, ly - 1, lw + 2, 26, r() + 1, alpha(t.accent(), 0x90));
        round(ctx, lx, ly, lw, 24, fieldFocused ? mix(t.panel(), t.accent(), 0.15f) : t.panel());
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        if (targetText.isEmpty() && !fieldFocused) {
            text(ctx, "type a nickname...", lx + 8, ly + 8, t.dim());
        } else {
            text(ctx, targetText + (fieldFocused && blink ? "|" : ""), lx + 8, ly + 8, t.text());
        }

        // online list
        List<PlayerListEntry> on = filteredOnline();
        int top = ly + 30, rows = targetRows();
        scroll2 = Math.max(0, Math.min(scroll2, Math.max(0, on.size() - rows)));
        if (on.isEmpty()) {
            round(ctx, lx, top, lw, 28, t.panel());
            text(ctx, "Nobody found", lx + 10, top + 10, t.dim());
        }
        int end = Math.min(on.size(), scroll2 + rows);
        for (int i = scroll2; i < end; i++) {
            PlayerListEntry le = on.get(i);
            String name = le.getProfile().getName();
            UUID id = le.getProfile().getId();
            PlayerEntity pe = mc.world == null ? null : mc.world.getPlayerByUuid(id);
            int ry = top + (i - scroll2) * ROW;
            boolean sel = name.equalsIgnoreCase(targetText);
            boolean hover = in(mx, my, lx, ry, lw, ROW - 2);
            round(ctx, lx, ry, lw, ROW - 2, sel ? mix(t.panel(), t.accent(), 0.30f) : (hover ? mix(t.panel(), t.accent(), 0.12f) : t.panel()));
            if (sel) {
                float pr = prog(clickedAt, 350);
                if (pr < 1f) {
                    roundR(ctx, lx, ry, Math.max(2, (int) (lw * ease(pr))), ROW - 2, r(), alpha(t.accent(), (int) (0x70 * (1f - pr))));
                }
                int barW = (int) Math.ceil(3 * ease(prog(clickedAt, 200)));
                if (barW > 0) roundR(ctx, lx, ry + 4, barW, ROW - 10, 1, t.accent());
            }
            drawHead(ctx, id, name, lx + 7, ry + 3, 14, pe == null ? alpha(t.accent(), 0xAA) : tier(pe.getArmor(), t), t);
            text(ctx, name, lx + 27, ry + 6, t.text());
            if (pe != null) drawArmorRow(ctx, pe, lx + lw - 86, ry + 6, t);
            textRight(ctx, le.getLatency() + "ms", lx + lw - 8, ry + 6, t.dim());
        }

        // detail panel
        int dx = detailX(), dw = x + W - 14 - dx;
        round(ctx, dx, ly, dw, bottom() - ly, t.panel());
        drawTimerControls(ctx, dx, ly, dw, mx, my, t);

        PlayerListEntry tgt = findTarget();
        if (tgt == null) {
            text(ctx, targetText.isEmpty() ? "Type or pick a nickname" : "Not online", dx + 12, ly + 12, t.dim());
            return;
        }
        UUID tid = tgt.getProfile().getId();
        PlayerEntity tp = mc.world == null ? null : mc.world.getPlayerByUuid(tid);
        renderDetail(ctx, mx, my, t, tid, tgt.getProfile().getName(), tp,
                "online, " + tgt.getLatency() + "ms", dx, ly, dw);
    }

    /** handles clicks on the timer slider and -/+ buttons; returns true if used */
    private boolean timerClick(double mx, double my) {
        int dx = detailX(), dw = x + W - 14 - dx, ly = listY();
        int[] dtr = track(S_DELAY);
        if (in(mx, my, dtr[0] - 4, dtr[1] - 8, dtr[2] + 8, 20)) {
            dragging = S_DELAY;
            setFromMouse(S_DELAY, mx);
            return true;
        }
        if (in(mx, my, dx + 12, ly + BTN_Y, 24, 18)) {
            Config.bombDelay = Math.max(1, Config.bombDelay - 1);
            return true;
        }
        if (in(mx, my, dx + dw - 36, ly + BTN_Y, 24, 18)) {
            Config.bombDelay = Math.min(120, Config.bombDelay + 1);
            return true;
        }
        return false;
    }

    private void renderThemes(DrawContext ctx, int mx, int my, Theme t) {
        int lx = cx(), ly = listY(), w = contentW();
        for (int i = 0; i < Theme.ALL.length; i++) {
            Theme th = Theme.ALL[i];
            int ry = ly + i * 32;
            boolean active = i == Config.theme;
            boolean hover = in(mx, my, lx, ry, w, 28);
            round(ctx, lx, ry, w, 28, hover ? mix(t.panel(), t.accent(), 0.12f) : t.panel());
            if (active) roundR(ctx, lx + 3, ry + 6, 3, 16, 1, t.accent());
            text(ctx, th.name(), lx + 14, ry + 10, t.text());
            int sx = lx + w - 100;
            roundR(ctx, sx, ry + 7, 14, 14, 7, th.bg());
            roundR(ctx, sx + 18, ry + 7, 14, 14, 7, th.panel());
            roundR(ctx, sx + 36, ry + 7, 14, 14, 7, th.accent());
            if (active) text(ctx, "active", sx + 58, ry + 10, t.accent());
        }
    }

    private void toggle(DrawContext ctx, int tx, int ty, boolean on, Theme t) {
        roundR(ctx, tx, ty, 32, 14, 7, on ? t.accent() : alpha(t.dim(), 0x70));
        roundR(ctx, tx + (on ? 20 : 2), ty + 2, 10, 10, 5, 0xFFFFFFFF);
    }

    private void renderSettings(DrawContext ctx, int mx, int my, Theme t) {
        int lx = cx(), ly = listY(), w = contentW();
        String[] labels = {"Show distance in list", "Show health in details", "Sort players by name",
                "Background dim", "Corner roundness", "Bomb sound", "Bomb delay", "Bombed nametags"};
        for (int i = 0; i < labels.length; i++) {
            int ry = ly + i * 32;
            boolean hover = in(mx, my, lx, ry, w, 28);
            round(ctx, lx, ry, w, 28, hover && i < 3 ? mix(t.panel(), t.accent(), 0.10f) : t.panel());
            text(ctx, labels[i], lx + 12, ry + 10, t.text());
        }
        toggle(ctx, lx + w - 44, ly + 7, Config.showDistance, t);
        toggle(ctx, lx + w - 44, ly + 32 + 7, Config.showHealth, t);
        toggle(ctx, lx + w - 44, ly + 64 + 7, Config.sortByName, t);
        toggle(ctx, lx + w - 44, ly + 7 * 32 + 7, Config.nametags, t);
        button(ctx, lx + w - 126, ly + 7 * 32 + 5, 72, 18, "Clear (" + NameTags.count() + ")", mx, my, t);

        renderSlider(ctx, S_DIM, t);
        renderSlider(ctx, S_CORNER, t);
        renderSlider(ctx, S_DELAY, t);

        int sy = ly + 5 * 32, px = lx + w - 196;
        button(ctx, px, sy + 5, 18, 18, "<", mx, my, t);
        String nm = Bomb.NAMES[Config.bombSound];
        text(ctx, nm, px + 22 + (90 - textRenderer.getWidth(nm)) / 2, sy + 10, t.accent());
        button(ctx, px + 116, sy + 5, 18, 18, ">", mx, my, t);
        button(ctx, lx + w - 56, sy + 5, 46, 18, "Test", mx, my, t);
        int[] dl = track(S_DELAY);
        textRight(ctx, Config.bombDelay + "s", dl[0] - 10, ly + 6 * 32 + 10, t.dim());

        int[] d = track(S_DIM), c = track(S_CORNER);
        textRight(ctx, Config.dim + "%", d[0] - 10, ly + 3 * 32 + 10, t.dim());
        textRight(ctx, Config.corner + "px", c[0] - 10, ly + 4 * 32 + 10, t.dim());
    }

    private void button(DrawContext ctx, int bx, int by, int bw, int bh, String label, int mx, int my, Theme t) {
        boolean hover = in(mx, my, bx, by, bw, bh);
        roundR(ctx, bx, by, bw, bh, r(), hover ? mix(t.bg(), t.accent(), 0.35f) : t.bg());
        text(ctx, label, bx + (bw - textRenderer.getWidth(label)) / 2, by + (bh - 8) / 2, t.text());
    }

    private void renderAbout(DrawContext ctx, Theme t) {
        int lx = cx(), ly = listY(), w = contentW();
        round(ctx, lx, ly, w, bottom() - ly, t.panel());
        String[] lines = {
                "cucumber v1.0.0",
                "",
                "Press O to open or close this menu (rebind in Controls).",
                "Players: shows everyone within the radius you set.",
                "Target: type any nickname of someone online and BOMB them.",
                "Click a player: head, armor, enchants and durability.",
                "BOMB plays a sound after a delay (pick it in Settings).",
                "After it goes off, that player gets a custom nametag.",
                "Themes: pick a color style, it is saved automatically.",
                "Settings: toggles, background dim and corner roundness.",
                "",
                "Note: the game only knows about players your client has",
                "loaded, so servers with low view distance show fewer."
        };
        for (int i = 0; i < lines.length; i++) {
            text(ctx, lines[i], lx + 14, ly + 14 + i * 12, i == 0 ? t.accent() : t.dim());
        }
    }

    private PlayerEntity getSelectedPlayer() {
        if (selected == null) return null;
        for (PlayerEntity p : players) if (p.getUuid().equals(selected)) return p;
        return null;
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        mx /= s;
        my /= s;
        if (button != 0) return super.mouseClicked(mx, my, button);

        for (int i = 0; i < TABS.length; i++) {
            if (in(mx, my, x + 8, y + 54 + i * 26, SIDE - 16, 22)) {
                tab = i;
                dragging = -1;
                fieldFocused = false;
                return true;
            }
        }

        if (tab == 0) {
            int[] tr = track(S_RADIUS);
            if (in(mx, my, tr[0] - 4, tr[1] - 8, tr[2] + 8, 20)) {
                dragging = S_RADIUS;
                setFromMouse(S_RADIUS, mx);
                return true;
            }
            int tdx = detailX(), tdw = x + W - 14 - tdx;
            int[] dtr = track(S_DELAY);
            if (in(mx, my, dtr[0] - 4, dtr[1] - 8, dtr[2] + 8, 20)) {
                dragging = S_DELAY;
                setFromMouse(S_DELAY, mx);
                return true;
            }
            if (in(mx, my, tdx + 12, listY() + 144, 24, 18)) {
                Config.bombDelay = Math.max(1, Config.bombDelay - 1);
                return true;
            }
            if (in(mx, my, tdx + tdw - 36, listY() + 144, 24, 18)) {
                Config.bombDelay = Math.min(120, Config.bombDelay + 1);
                return true;
            }
            int end = Math.min(players.size(), scroll + visibleRows());
            for (int i = scroll; i < end; i++) {
                int ry = listY() + (i - scroll) * ROW;
                if (in(mx, my, cx(), ry, listW(), ROW - 2)) {
                    selected = players.get(i).getUuid();
                    clickedId = selected;
                    slotSel = -1;
                    clickedAt = System.currentTimeMillis();
                    return true;
                }
            }
            if (getSelectedPlayer() != null) {
                int dx = detailX(), dw = x + W - 14 - dx;
                if (slotClick(mx, my)) return true;
                if (in(mx, my, dx + 12, listY() + BOMB_Y, dw - 24, 26)) {
                    Bomb.start(getSelectedPlayer().getName().getString());
                    bombClickAt = System.currentTimeMillis();
                    return true;
                }
            }
        } else if (tab == 1) {
            int ly = listY(), lx = cx();
            fieldFocused = in(mx, my, lx, ly, listW(), 24);
            if (fieldFocused) return true;
            List<PlayerListEntry> on = filteredOnline();
            int top = ly + 30, end = Math.min(on.size(), scroll2 + targetRows());
            for (int i = scroll2; i < end; i++) {
                if (in(mx, my, lx, top + (i - scroll2) * ROW, listW(), ROW - 2)) {
                    targetText = on.get(i).getProfile().getName();
                    slotSel = -1;
                    clickedAt = System.currentTimeMillis();
                    return true;
                }
            }
            if (timerClick(mx, my)) return true;
            if (findTarget() != null) {
                int dx = detailX(), dw = x + W - 14 - dx;
                if (slotClick(mx, my)) return true;
                if (in(mx, my, dx + 12, ly + BOMB_Y, dw - 24, 26)) {
                    Bomb.start(findTarget().getProfile().getName());
                    bombClickAt = System.currentTimeMillis();
                    return true;
                }
            }
        } else if (tab == 2) {
            for (int i = 0; i < Theme.ALL.length; i++) {
                if (in(mx, my, cx(), listY() + i * 32, contentW(), 28)) {
                    Config.theme = i;
                    return true;
                }
            }
        } else if (tab == 3) {
            for (int i = 0; i < 3; i++) {
                if (in(mx, my, cx(), listY() + i * 32, contentW(), 28)) {
                    if (i == 0) Config.showDistance = !Config.showDistance;
                    if (i == 1) Config.showHealth = !Config.showHealth;
                    if (i == 2) Config.sortByName = !Config.sortByName;
                    return true;
                }
            }
            int ny = listY() + 7 * 32;
            if (in(mx, my, cx() + contentW() - 126, ny + 5, 72, 18)) {
                NameTags.clear();
                return true;
            }
            if (in(mx, my, cx() + contentW() - 48, ny + 3, 40, 22)) {
                Config.nametags = !Config.nametags;
                return true;
            }
            int sy = listY() + 5 * 32, px = cx() + contentW() - 196;
            int n = Bomb.NAMES.length;
            if (in(mx, my, px, sy + 5, 18, 18)) {
                Config.bombSound = (Config.bombSound + n - 1) % n;
                Bomb.play(Config.bombSound);
                return true;
            }
            if (in(mx, my, px + 116, sy + 5, 18, 18)) {
                Config.bombSound = (Config.bombSound + 1) % n;
                Bomb.play(Config.bombSound);
                return true;
            }
            if (in(mx, my, cx() + contentW() - 56, sy + 5, 46, 18)) {
                Bomb.play(Config.bombSound);
                return true;
            }
            for (int id : new int[]{S_DIM, S_CORNER, S_DELAY}) {
                int[] tr = track(id);
                if (in(mx, my, tr[0] - 4, tr[1] - 8, tr[2] + 8, 20)) {
                    dragging = id;
                    setFromMouse(id, mx);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging >= 0) {
            setFromMouse(dragging, mx / s);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = -1;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        if (tab == 0) {
            int max = Math.max(0, players.size() - visibleRows());
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(vAmount)));
            return true;
        }
        if (tab == 1) {
            int max = Math.max(0, filteredOnline().size() - targetRows());
            scroll2 = Math.max(0, Math.min(max, scroll2 - (int) Math.signum(vAmount)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (tab == 1 && fieldFocused && (Character.isLetterOrDigit(chr) || chr == '_') && targetText.length() < 16) {
            targetText += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab == 1 && fieldFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!targetText.isEmpty()) targetText = targetText.substring(0, targetText.length() - 1);
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                fieldFocused = false;
            }
            return true; // typing never closes the menu
        }
        if (keyCode == GLFW.GLFW_KEY_O && System.currentTimeMillis() - openedAt > 300) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
