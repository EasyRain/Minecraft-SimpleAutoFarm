package com.simpleautofarm.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Crystal handling, moved off the crop farm: {@code isPlantBlock} used to accept "budding" blocks
 * (amethyst mother rock, AE2 certus quartz, GeOre crystals), which are not plants. The ore farm now
 * accepts them and produces the drops of the cluster they would grow.
 */
public final class CrystalHelper {

    private CrystalHelper() {
    }

    /** True when the stack is a budding crystal block (amethyst / AE2 / GeOre...). */
    public static boolean isBudding(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && isBuddingBlock(blockItem.getBlock());
    }

    /** A "budding" block grows crystal clusters on its faces (amethyst, AE2, GeOre...). */
    public static boolean isBuddingBlock(Block block) {
        if (block instanceof BuddingAmethystBlock) {
            return true;
        }
        Identifier key = BuiltInRegistries.BLOCK.getKey(block);
        return key != null && key.getPath().contains("budding");
    }

    /**
     * Drops of the cluster that grows from this budding block
     * (budding_amethyst -> amethyst_cluster -> amethyst shards; AE2 / GeOre likewise).
     */
    public static List<ItemStack> clusterDrops(Level level, BlockPos pos, BlockEntity owner, Block buddingBlock) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(buddingBlock);
        if (key == null) {
            return List.of();
        }
        // Extract the crystal material by dropping the "budding" segment and quality words:
        // flawless_budding_quartz -> quartz, budding_amethyst -> amethyst,
        // entro_budding_fully -> entro, lattra_budding_mostly -> lattra,
        // flawless_budding_overload_crystal -> overload_crystal.
        String material = String.join("_", extractMaterialSegments(key.getPath()));
        String namespace = key.getNamespace();
        // Cluster naming varies: X_cluster (amethyst/quartz/entro/coal/overload_crystal)
        // or X_crystal_cluster (lattra).
        Block cluster = findBlock(namespace, material + "_cluster");
        if (cluster == null) {
            cluster = findBlock(namespace, material + "_crystal_cluster");
        }
        if (cluster == null || !(level instanceof ServerLevel serverLevel)) {
            return List.of();
        }

        // 1) code-based drops (vanilla amethyst, GeOre clusters)
        List<ItemStack> drops = Block.getDrops(cluster.defaultBlockState(), serverLevel, pos, owner);
        if (!drops.isEmpty()) {
            return drops;
        }

        // 2) some mods (AE2 and addons) return empty from code drops when no player entity is
        //    involved, so fall back to the block's data-driven loot table.
        Identifier clusterId = BuiltInRegistries.BLOCK.getKey(cluster);
        ResourceKey<LootTable> lootKey = ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(clusterId.getNamespace(), "blocks/" + clusterId.getPath()));
        LootTable table = serverLevel.getServer().reloadableRegistries().getLootTable(lootKey);
        if (table != LootTable.EMPTY) {
            LootParams params = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withParameter(LootContextParams.BLOCK_STATE, cluster.defaultBlockState())
                    .create(LootContextParamSets.BLOCK);
            return table.getRandomItems(params);
        }
        return List.of();
    }

    @Nullable
    private static Block findBlock(String namespace, String path) {
        Identifier id = Identifier.fromNamespaceAndPath(namespace, path);
        return BuiltInRegistries.BLOCK.get(id).map(holder -> holder.value()).orElse(null);
    }

    private static List<String> extractMaterialSegments(String path) {
        List<String> remaining = new ArrayList<>();
        for (String segment : path.split("_")) {
            if (!segment.equals("budding") && !isQualityWord(segment)) {
                remaining.add(segment);
            }
        }
        return remaining;
    }

    private static boolean isQualityWord(String segment) {
        return switch (segment) {
            case "flawless", "flawed", "chipped", "cracked", "damaged",
                 "fully", "mostly", "half", "hardly" -> true;
            default -> false;
        };
    }
}
