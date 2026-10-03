package com.cucumber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Players that got bombed get a custom nametag (replaces the vanilla one). */
public final class NameTags {
    private static final Set<String> NAMES = new LinkedHashSet<>();

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final String[] LABELS = {"Helm", "Chest", "Legs", "Boots", "Main", "Off"};

    private record Line(String text, int color) {}

    private NameTags() {}

    public static void add(String name) {
        if (name != null && !name.isEmpty()) NAMES.add(name.toLowerCase(Locale.ROOT));
    }

    public static void clear() {
        NAMES.clear();
    }

    public static int count() {
        return NAMES.size();
    }

    public static boolean has(Entity e) {
        return Config.nametags && e instanceof PlayerEntity
                && NAMES.contains(e.getName().getString().toLowerCase(Locale.ROOT));
    }

    public static boolean hides(Entity e) {
        return has(e) && e != MinecraftClient.getInstance().player;
    }

    // ---------- helpers ----------
    private static double weaponDamage(ItemStack st) {
        double d = 1.0;
        var comp = st.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (comp != null) {
            for (var en : comp.modifiers()) {
                var key = en.attribute().getKey();
                if (key.isPresent() && key.get().getValue().getPath().endsWith("attack_damage")) {
                    d += en.modifier().value();
                }
            }
        }
        return d;
    }

    private static String abbr(String full) {
        String[] w = full.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.length; i++) {
            String x = w[i];
            boolean level = i == w.length - 1 && x.matches("[IVXLC]+|\\d+");
            sb.append(level || x.length() <= 4 ? x : x.substring(0, 4));
            if (i < w.length - 1) sb.append(' ');
        }
        return sb.toString();
    }

    private static String enchants(ItemStack st) {
        StringBuilder sb = new StringBuilder();
        int n = 0, total = st.getEnchantments().getSize();
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : st.getEnchantments().getEnchantmentEntries()) {
            if (n >= 3) break;
            if (n > 0) sb.append(", ");
            sb.append(abbr(Enchantment.getName(e.getKey(), e.getIntValue()).getString()));
            n++;
        }
        if (total > 3) sb.append(" +").append(total - 3);
        return sb.toString();
    }

    private static List<Line> buildLines(PlayerEntity p, Theme t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<Line> lines = new ArrayList<>();

        PlayerListEntry le = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
        String head = p.getName().getString() + (le != null ? "  |  " + le.getLatency() + "ms" : "");
        lines.add(new Line(head, t.accent()));

        double dmg = weaponDamage(p.getEquippedStack(EquipmentSlot.MAINHAND));
        lines.add(new Line(String.format(Locale.ROOT, "HP %.1f/%.0f   DMG %.1f   ARM %d",
                p.getHealth(), p.getMaxHealth(), dmg, p.getArmor()), 0xFFFFFFFF));

        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack st = p.getEquippedStack(SLOTS[i]);
            if (st.isEmpty()) continue;
            String ench = enchants(st);
            String body = ench.isEmpty() ? st.getName().getString() : ench;
            lines.add(new Line(LABELS[i] + ": " + body, ench.isEmpty() ? 0xFFB8C4D6 : 0xFFFFE08A));
        }
        return lines;
    }

    // ---------- world rendering ----------
    public static void render(WorldRenderContext ctx) {
        if (!Config.nametags || NAMES.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vc = ctx.consumers();
        Camera cam = ctx.camera();
        if (ms == null || vc == null || cam == null) return;

        Vec3d cp = cam.getPos();
        float td = ctx.tickCounter().getTickDelta(false);
        TextRenderer tr = mc.textRenderer;
        Theme t = Theme.ALL[Config.theme];

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !has(p)) continue;

            List<Line> lines = buildLines(p, t);
            Vec3d pos = p.getLerpedPos(td);
            double dist = pos.distanceTo(cp);
            float sc = 0.025f * (float) Math.max(1.0, Math.min(dist / 8.0, 6.0));

            ms.push();
            ms.translate(pos.x - cp.x, pos.y - cp.y + p.getHeight() + 0.45, pos.z - cp.z);
            ms.multiply(cam.getRotation());
            ms.scale(sc, -sc, sc);
            Matrix4f m = ms.peek().getPositionMatrix();

            int n = lines.size();
            for (int i = 0; i < n; i++) {
                Line ln = lines.get(i);
                float w = tr.getWidth(ln.text());
                tr.draw(ln.text(), -w / 2f, (i - n) * 10f, ln.color(), false, m, vc,
                        TextRenderer.TextLayerType.SEE_THROUGH, 0x90000000, 0xF000F0);
            }
            ms.pop();
        }
    }
}
