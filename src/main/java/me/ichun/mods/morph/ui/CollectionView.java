package me.ichun.mods.morph.ui;

import me.ichun.mods.morph.model.CollectionSnapshot;

/** Implemented by collection screens receiving complete snapshots and matching action results. */
public interface CollectionView {
    void update(CollectionSnapshot snapshot);
    void acknowledge(long sequence, long revision, String resultCode);
}
