package com.skps2010;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public record CravingPayload(String itemId) implements CustomPacketPayload {
    public static final Type<CravingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CraveAndFeast.MODID, "craving_sync"));

    public static final StreamCodec<FriendlyByteBuf, CravingPayload> STREAM_CODEC =
            CustomPacketPayload.codec(CravingPayload::write, CravingPayload::new);

    private CravingPayload(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(itemId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public Item toItem() {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return Items.AIR;
        return BuiltInRegistries.ITEM.get(id);
    }
}
