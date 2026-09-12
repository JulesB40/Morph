package me.ichun.mods.morph.ui;

import java.util.function.Consumer;
import me.ichun.mods.morph.client.ClientCollectionState;
import me.ichun.mods.morph.model.EntryId;
import me.ichun.mods.morph.network.CollectionProtocol;
import net.minecraft.client.Minecraft;

/** Shared loader-neutral UI transport; loaders only supply the packet sender. */
public final class CollectionClientUi {
    private CollectionClientUi() {}

    public static MorphScreen.Actions actions(Consumer<CollectionProtocol.Action> send) {
        return new MorphScreen.Actions() {
            private long request(CollectionProtocol.Opcode opcode, String id, boolean favorite) {
                var action = ClientCollectionState.nextAction(opcode, id == null ? null : new EntryId(id), favorite);
                send.accept(action);
                return action.sequence();
            }
            public long select(String id) { return request(CollectionProtocol.Opcode.SELECT, id, false); }
            public long favorite(String id, boolean value) { return request(CollectionProtocol.Opcode.FAVORITE, id, value); }
            public long delete(String id) { return request(CollectionProtocol.Opcode.DELETE, id, false); }
            public long reset() { return request(CollectionProtocol.Opcode.RESET, null, false); }
            public void refresh() { request(CollectionProtocol.Opcode.REQUEST_SNAPSHOT, null, false); }
        };
    }

    public static void open(MorphScreen.Actions actions, boolean radial) {
        var minecraft = Minecraft.getInstance();
        var screen = radial ? new MorphFavoritesScreen(actions) : new MorphScreen(actions);
        minecraft.gui.setScreen(screen);
        if (ClientCollectionState.hasSnapshot()) ((CollectionView) screen).update(ClientCollectionState.snapshot());
        actions.refresh();
    }

    public static void receive(CollectionProtocol.Page page) {
        if (ClientCollectionState.accept(page) && Minecraft.getInstance().gui.screen() instanceof CollectionView view)
            view.update(ClientCollectionState.snapshot());
    }
    public static void receive(CollectionProtocol.Ack ack) {
        ClientCollectionState.acknowledge(ack);
        if (Minecraft.getInstance().gui.screen() instanceof CollectionView view)
            view.acknowledge(ack.sequence(), ack.revision(), ack.code().name());
    }
}

