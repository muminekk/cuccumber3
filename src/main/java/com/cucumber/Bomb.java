package com.cucumber;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.concurrent.ThreadLocalRandom;

public final class Bomb {
    private static final String[][] SOUNDS = {
            {"Explosion", "entity.generic.explode"},
            {"TNT Fuse", "entity.tnt.primed"},
            {"Creeper Hiss", "entity.creeper.primed"},
            {"Thunder", "entity.lightning_bolt.thunder"},
            {"Lightning", "entity.lightning_bolt.impact"},
            {"Dragon Growl", "entity.ender_dragon.growl"},
            {"Dragon Death", "entity.ender_dragon.death"},
            {"Wither Spawn", "entity.wither.spawn"},
            {"Wither Death", "entity.wither.death"},
            {"Ghast Scream", "entity.ghast.scream"},
            {"Sonic Boom", "entity.warden.sonic_boom"},
            {"Warden Roar", "entity.warden.roar"},
            {"Ravager", "entity.ravager.roar"},
            {"Elder Curse", "entity.elder_guardian.curse"},
            {"Enderman", "entity.enderman.scream"},
            {"Phantom", "entity.phantom.swoop"},
            {"Anvil", "block.anvil.land"},
            {"Bell", "block.bell.use"},
            {"Glass Break", "block.glass.break"},
            {"Beacon", "block.beacon.activate"},
            {"Raid Horn", "event.raid.horn"},
            {"Trident", "item.trident.thunder"},
            {"Firework", "entity.firework_rocket.blast"},
            {"Totem", "item.totem.use"},
            {"Level Up", "entity.player.levelup"},
            {"Pling", "block.note_block.pling"},
            {"Villager No", "entity.villager.no"},
            {"Cat Meow", "entity.cat.ambient"},
            {"Bat", "entity.bat.takeoff"}
    };

    /** all names, plus "Random" as the last entry */
    public static final String[] NAMES;

    static {
        NAMES = new String[SOUNDS.length + 1];
        for (int i = 0; i < SOUNDS.length; i++) NAMES[i] = SOUNDS[i][0];
        NAMES[SOUNDS.length] = "Random";
    }

    private static int ticksLeft = -1;
    private static int totalTicks = 1;
    public static long firedAt = 0;
    public static long armedAt = 0;
    public static String target = "";

    private Bomb() {}

    public static boolean armed() {
        return ticksLeft >= 0;
    }

    public static int secondsLeft() {
        return (ticksLeft + 19) / 20;
    }

    private static void message(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(Text.literal(msg), false);
    }

    public static void start(String name) {
        if (!armed()) {
            target = name;
            armedAt = System.currentTimeMillis();
            message("Bomb armed on " + name + " (" + Config.bombDelay + "s)");
            totalTicks = Math.max(1, Config.bombDelay * 20);
            ticksLeft = totalTicks;
        }
    }

    /** 1.0 = just started, 0.0 = about to go off */
    public static float fraction() {
        return Math.max(0f, Math.min(1f, ticksLeft / (float) totalTicks));
    }

    public static void tick(MinecraftClient mc) {
        if (ticksLeft < 0) return;
        if (mc.world == null) {
            ticksLeft = -1;
            return;
        }
        if (ticksLeft == 0) {
            play(Config.bombSound);
            firedAt = System.currentTimeMillis();
            NameTags.add(target);
            message("Bomb: " + target + " went boom");
            ticksLeft = -1;
        } else {
            ticksLeft--;
        }
    }

    public static void play(int index) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int i = index >= SOUNDS.length
                ? ThreadLocalRandom.current().nextInt(SOUNDS.length)
                : Math.max(0, index);
        SoundEvent ev = SoundEvent.of(Identifier.ofVanilla(SOUNDS[i][1]));
        mc.getSoundManager().play(PositionedSoundInstance.master(ev, 1.0f, 1.0f));
    }
}
