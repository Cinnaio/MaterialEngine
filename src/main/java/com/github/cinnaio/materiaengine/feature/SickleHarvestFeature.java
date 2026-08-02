package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import com.github.cinnaio.materiaengine.util.MachineItems;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.CustomBlock;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.properties.Property;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 镰刀范围收割：右键成熟作物 → 收割半径内全部成熟作物（teastory 自定义 + 原版），
 * 背包有对应种子则原位续种成 age 0，消耗耐久按收割数量成倍增加（受耐久附魔减免）。
 * 同时拦截镰刀（iron_hoe）右键耕地的效果。
 */
public final class SickleHarvestFeature implements Listener {

    private static final String DEFAULT_SICKLE_ID = "cgap:sickle";
    private static final int DEFAULT_RADIUS = 1;
    private static final Map<String, CropSpec> DEFAULT_CUSTOM_CROPS = Map.of(
            "cgap:tea_tree_crop", new CropSpec("cgap:tea_seeds", 6),
            "cgap:xian_rice_plant_crop", new CropSpec("cgap:item_xian_rice_seedling", 7)
    );
    private static final Map<Material, CropSpec> DEFAULT_VANILLA_CROPS = Map.of(
            Material.WHEAT, new CropSpec("minecraft:wheat_seeds", 7),
            Material.CARROTS, new CropSpec("minecraft:carrot", 7),
            Material.POTATOES, new CropSpec("minecraft:potato", 7),
            Material.BEETROOTS, new CropSpec("minecraft:beetroot_seeds", 3),
            Material.NETHER_WART, new CropSpec("minecraft:nether_wart", 3)
    );
    private static final Set<Material> TILLABLE = Set.of(
            Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.DIRT_PATH, Material.GRASS_BLOCK
    );

    private final JavaPlugin plugin;
    private final CraftEngineHook craftEngineHook;

    private String sickleId;
    private int radius;
    private Map<String, CropSpec> customCrops;
    private Map<Material, CropSpec> vanillaCrops;

    public SickleHarvestFeature(JavaPlugin plugin, CraftEngineHook craftEngineHook) {
        this.plugin = plugin;
        this.craftEngineHook = craftEngineHook;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("sickle");
        this.sickleId = config == null ? DEFAULT_SICKLE_ID : config.getString("item", DEFAULT_SICKLE_ID);
        this.radius = config == null ? DEFAULT_RADIUS : Math.max(0, config.getInt("radius", DEFAULT_RADIUS));
        Map<String, CropSpec> customs = new LinkedHashMap<>(DEFAULT_CUSTOM_CROPS);
        Map<Material, CropSpec> vanillas = new LinkedHashMap<>(DEFAULT_VANILLA_CROPS);
        ConfigurationSection crops = config == null ? null : config.getConfigurationSection("crops");
        if (crops != null) {
            for (String key : crops.getKeys(false)) {
                ConfigurationSection specSection = crops.getConfigurationSection(key);
                if (specSection == null) {
                    continue;
                }
                String seed = specSection.getString("seed", "");
                int matureAge = specSection.getInt("mature-age", 7);
                Material material = Material.matchMaterial(key);
                if (material != null) {
                    if (seed.isBlank()) {
                        vanillas.remove(material);
                    } else {
                        vanillas.put(material, new CropSpec(seed, matureAge));
                    }
                } else {
                    if (seed.isBlank()) {
                        customs.remove(key);
                    } else {
                        customs.put(key, new CropSpec(seed, matureAge));
                    }
                }
            }
        }
        this.customCrops = Map.copyOf(customs);
        this.vanillaCrops = Map.copyOf(vanillas);
    }

    /**
     * 右键成熟作物 → 范围收割 + 续种；右键可耕地 → 拦截 iron_hoe 锄地。
     * 收割/续种走 CraftEngineBlocks.remove/place 与 breakNaturally/setBlockData 直接 API，
     * 不触发破坏/放置事件，因此不影响 NatureEngine 的生长追踪。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (hand == null || !craftEngineHook.isCustomItem(hand, sickleId)) {
            return;
        }
        Block block = event.getClickedBlock();
        if (isMatureCrop(block)) {
            event.setCancelled(true);
            harvestArea(event.getPlayer(), hand, block);
            return;
        }
        if (TILLABLE.contains(block.getType())) {
            event.setCancelled(true);
        }
    }

    private boolean isMatureCrop(Block block) {
        // teastory 自定义作物
        String cropId = craftEngineHook.getBlockId(block);
        if (cropId != null) {
            CropSpec spec = customCrops.get(cropId);
            if (spec == null) {
                return false;
            }
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            if (state == null || state.isEmpty()) {
                return false;
            }
            CustomBlock custom = CraftEngineBlocks.byId(Key.ce(cropId));
            return custom != null && ageOf(custom, state) >= spec.matureAge();
        }
        // 原版作物
        CropSpec spec = vanillaCrops.get(block.getType());
        return spec != null && block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= spec.matureAge();
    }

    private void harvestArea(Player player, ItemStack hand, Block center) {
        int harvested = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (harvestBlock(player, hand, center.getRelative(dx, 0, dz))) {
                    harvested++;
                }
            }
        }
        if (harvested > 0 && player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().setItemInMainHand(player.damageItemStack(hand, harvested));
        }
    }

    private boolean harvestBlock(Player player, ItemStack hand, Block block) {
        // 自定义作物：remove（成熟战利品，时运生效）→ 有种子则原位续种 age0
        String cropId = craftEngineHook.getBlockId(block);
        if (cropId != null) {
            CropSpec spec = customCrops.get(cropId);
            if (spec == null) {
                return false;
            }
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            if (state == null || state.isEmpty()) {
                return false;
            }
            CustomBlock custom = CraftEngineBlocks.byId(Key.ce(cropId));
            if (custom == null || ageOf(custom, state) < spec.matureAge()) {
                return false;
            }
            CraftEngineBlocks.remove(block, player, true, true, true);
            if (consumeSeed(player, spec.seedId())) {
                CraftEngineBlocks.place(block.getLocation(), Key.ce(cropId), true);
            }
            return true;
        }
        // 原版作物：breakNaturally（时运生效，不触发事件）→ 有种子则续种 age0
        CropSpec spec = vanillaCrops.get(block.getType());
        if (spec == null) {
            return false;
        }
        if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() < spec.matureAge()) {
            return false;
        }
        Material cropType = block.getType(); // breakNaturally 后方块变空气，须先记下作物类型
        block.breakNaturally(hand);
        if (consumeSeed(player, spec.seedId())) {
            Ageable replant = (Ageable) Bukkit.createBlockData(cropType);
            replant.setAge(0);
            block.setBlockData(replant, false);
        }
        return true;
    }

    private boolean consumeSeed(Player player, String seedId) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || !seedId.equals(MachineItems.itemIdOf(craftEngineHook, item))) {
                continue;
            }
            item.setAmount(item.getAmount() - 1);
            if (item.getAmount() <= 0) {
                player.getInventory().setItem(i, null);
            }
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static int ageOf(CustomBlock custom, ImmutableBlockState state) {
        Property<Integer> property = (Property<Integer>) custom.getProperty("age");
        if (property == null) {
            return 0;
        }
        Integer value = state.get(property);
        return value == null ? 0 : value;
    }

    private record CropSpec(String seedId, int matureAge) {
    }
}
