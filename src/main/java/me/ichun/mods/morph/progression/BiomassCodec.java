package me.ichun.mods.morph.progression;

import java.io.*;
import java.util.EnumMap;
import static me.ichun.mods.morph.progression.BiomassDefinitions.Upgrade;

/** Bounded canonical persistence payload; transport framing and player identity belong to the save/service owner. */
public final class BiomassCodec {
    private static final int VERSION = 1;
    public static final int MAX_BYTES = 128;
    private BiomassCodec() {}

    public static byte[] encode(BiomassLedger ledger, BiomassDefinitions definitions) {
        definitions.validate(ledger);
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeLong(ledger.revision());
            out.writeByte(ledger.unlocked() ? 1 : 0);
            out.writeInt(ledger.balance());
            out.writeByte(ledger.upgrades().size());
            for (Upgrade upgrade : Upgrade.values()) {
                int level = ledger.level(upgrade);
                if (level != 0) { out.writeByte(wireId(upgrade)); out.writeByte(level); }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }

    public static BiomassLedger decode(byte[] bytes, BiomassDefinitions definitions) {
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Oversized biomass payload");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readUnsignedByte() != VERSION) throw new IllegalArgumentException("Unsupported biomass schema");
            long revision = in.readLong();
            int unlocked = in.readUnsignedByte();
            if (unlocked > 1) throw new IllegalArgumentException("Invalid biomass unlock flag");
            int balance = in.readInt();
            int count = in.readUnsignedByte();
            if (count > Upgrade.values().length) throw new IllegalArgumentException("Too many biomass upgrades");
            var levels = new EnumMap<Upgrade, Integer>(Upgrade.class);
            int previous = 0;
            for (int i = 0; i < count; i++) {
                int id = in.readUnsignedByte();
                if (id <= previous) throw new IllegalArgumentException("Duplicate or unsorted biomass upgrade");
                previous = id;
                levels.put(fromWireId(id), in.readUnsignedByte());
            }
            if (in.available() != 0) throw new IllegalArgumentException("Trailing biomass payload");
            var ledger = new BiomassLedger(revision, unlocked == 1, balance, levels);
            definitions.validate(ledger);
            return ledger;
        } catch (IOException truncated) { throw new IllegalArgumentException("Truncated biomass payload", truncated); }
    }

    private static int wireId(Upgrade upgrade) {
        return switch (upgrade) { case CAPACITY -> 1; case EFFICIENCY -> 2; case ABSORPTION -> 3;
            case REACH -> 4; case CRITICAL_CAPACITY -> 5; };
    }
    private static Upgrade fromWireId(int id) {
        return switch (id) { case 1 -> Upgrade.CAPACITY; case 2 -> Upgrade.EFFICIENCY; case 3 -> Upgrade.ABSORPTION;
            case 4 -> Upgrade.REACH; case 5 -> Upgrade.CRITICAL_CAPACITY;
            default -> throw new IllegalArgumentException("Unknown biomass upgrade"); };
    }
}
