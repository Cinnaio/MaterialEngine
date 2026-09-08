package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.config.HarvestToolsConfig;
import com.github.cinnaio.materiaengine.config.HarvestToolsConfig.Crop;
import com.github.cinnaio.materiaengine.config.HarvestToolsConfig.Mode;
import com.github.cinnaio.materiaengine.config.HarvestToolsConfig.Tool;
import com.github.cinnaio.materiaengine.integration.BeaconEngineBridge;
import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import com.github.cinnaio.materiaengine.util.MachineItems;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class HarvestToolsFeature implements Listener {
    private final JavaPlugin plugin;
    private final CraftEngineHook hook;
    private final FruitRegrowth regrowth;
    private final RandomGenerator random;
    private final BeaconEngineBridge beacon;
    private volatile HarvestToolsConfig config;

    public HarvestToolsFeature(JavaPlugin plugin, CraftEngineHook hook, BeaconEngineBridge beacon) {
        this(plugin, hook, new FruitRegrowth(plugin, hook), HarvestToolsConfig.load(plugin.getConfig()), ThreadLocalRandom.current(), beacon);
        regrowth.start();
    }

    HarvestToolsFeature(JavaPlugin plugin, CraftEngineHook hook, FruitRegrowth regrowth,
                        HarvestToolsConfig config, RandomGenerator random, BeaconEngineBridge beacon) {
        this.plugin = plugin;
        this.hook = hook;
        this.regrowth = regrowth;
        this.config = config;
        this.random = random;
        this.beacon = beacon;
    }

    public void reload() {
        plugin.reloadConfig();
        try {
            config = HarvestToolsConfig.load(plugin.getConfig());
        } catch (IllegalArgumentException error) {
            plugin.getLogger().warning("Harvest configuration rejected; keeping previous settings: " + error.getMessage());
        }
    }

    public void shutdown() {
        regrowth.shutdown();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.useItemInHand() == Event.Result.DENY) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;
        if (action == Action.RIGHT_CLICK_BLOCK && event.useInteractedBlock() == Event.Result.DENY) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.ADVENTURE) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        String itemId = hook.getItemId(hand);
        HarvestToolsConfig settings = config;
        boolean sickle = settings.sickleItem().equals(itemId);
        Tool tool = settings.tools().get(itemId == null ? "" : itemId);
        if (!sickle && tool == null) return;
        event.setCancelled(true);
        if (player.hasCooldown(hand) || !hook.isReady()) return;
        if (tool != null && tool.mode() == Mode.FRUIT) {
            if (!canTrace(player, tool.reach())) return;
            var hit = player.rayTraceBlocks(tool.reach(), FluidCollisionMode.NEVER);
            if (hit != null && hit.getHitBlock() != null && available(hit.getHitBlock())) {
                if (harvestFruit(player, hand, hit.getHitBlock(), settings)) finish(player, hand);
            }
            return;
        }
        Block center = event.getClickedBlock();
        if (center == null || !available(center)) return;
        String centerId = blockId(center);
        if (!mature(center, settings.crops().get(centerId))) return;
        if (!sickle && !tool.targets().containsKey(centerId)) return;
        boolean harvested = harvestCrop(player, hand, center, tool, settings);
        if (!harvested) return;
        damage(player);
        if (sickle) {
            for (int dx = -settings.radius(); dx <= settings.radius(); dx++) {
                for (int dz = -settings.radius(); dz <= settings.radius(); dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (!hook.isCustomItem(player.getInventory().getItemInMainHand(), itemId)) {
                        if (harvested) finish(player, hand);
                        return;
                    }
                    Block block = center.getRelative(dx, 0, dz);
                    if (available(block) && harvestCrop(player, hand, block, null, settings)) {
                        harvested = true;
                        damage(player);
                    }
                }
            }
        }
        if (harvested) finish(player, hand);
    }

    boolean harvestCrop(Player player, ItemStack hand, Block block, Tool tool, HarvestToolsConfig settings) {
        String id = blockId(block);
        Crop crop = settings.crops().get(id);
        if (!mature(block, crop) || (tool != null && !tool.targets().containsKey(id))) return false;
        int seedSlot = findSeed(player, crop.seed());
        if (!hook.canHarvest(player, block, seedSlot >= 0)) return false;
        boolean custom = !id.startsWith("minecraft:");
        List<ItemStack> drops;
        if (custom) {
            drops = hook.customHarvestDrops(player, block);
            if (drops == null) return false;
        } else {
            BlockBreakEvent breaking = new BlockBreakEvent(block, player);
            Bukkit.getPluginManager().callEvent(breaking);
            if (breaking.isCancelled()) return false;
            drops = breaking.isDropItems() ? new ArrayList<>(block.getDrops(hand, player)) : new ArrayList<>();
        }
        applyBonus(drops, id, tool);
        PlayerHarvestBlockEvent harvesting = new PlayerHarvestBlockEvent(player, block, EquipmentSlot.HAND, drops);
        Bukkit.getPluginManager().callEvent(harvesting);
        if (harvesting.isCancelled() || !id.equals(blockId(block)) || !mature(block, crop)) return false;
        // Recheck inventory after callbacks. Newly awarded seeds are never used for this replant.
        seedSlot = findSeed(player, crop.seed());
        if (seedSlot >= 0 && !hook.canHarvest(player, block, true)) return false;
        boolean changed;
        if (seedSlot >= 0) {
            if (custom) changed = hook.setIntState(block, id, "age", 0);
            else {
                Ageable reset = (Ageable) block.getBlockData();
                reset.setAge(0);
                block.setBlockData(reset, false);
                changed = true;
            }
            if (changed) {
                ItemStack seed = player.getInventory().getItem(seedSlot);
                seed.setAmount(seed.getAmount() - 1);
                player.getInventory().setItem(seedSlot, seed.getAmount() == 0 ? null : seed);
            }
        } else if (custom) changed = hook.removeHarvestedBlock(block, player);
        else {
            block.setType(Material.AIR, true);
            changed = true;
        }
        if (changed) deliver(player, block, harvesting.getItemsHarvested(), settings.basketItem());
        return changed;
    }

    boolean harvestFruit(Player player, ItemStack hand, Block block, HarvestToolsConfig settings) {
        String id = hook.getBlockId(block);
        String fruit = settings.fruits().get(id == null ? "" : id);
        if (fruit == null || !Boolean.TRUE.equals(hook.getBooleanState(block, "fruiting"))
                || !hook.canHarvest(player, block, false)) return false;
        ItemStack drop = hook.createItem(fruit);
        if (!MachineItems.hasItem(drop)) return false;
        PlayerHarvestBlockEvent event = new PlayerHarvestBlockEvent(player, block, EquipmentSlot.HAND,
                new ArrayList<>(List.of(drop)));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || !id.equals(hook.getBlockId(block))
                || !Boolean.TRUE.equals(hook.getBooleanState(block, "fruiting"))) return false;
        if (!hook.setBooleanState(block, id, "fruiting", false)) return false;
        regrowth.record(block, id, settings.regrowSeconds());
        deliver(player, block, event.getItemsHarvested(), settings.basketItem());
        damage(player);
        return true;
    }

    void applyBonus(List<ItemStack> drops, String crop, Tool tool) {
        if (tool == null) return;
        String product = tool.targets().get(crop);
        boolean productPresent = false;
        for (int i = 0; i < drops.size(); i++) {
            ItemStack original = drops.get(i);
            String id = MachineItems.itemIdOf(hook, original);
            if (id == null) continue;
            productPresent |= id.equals(product);
            String replacement = tool.replacement(id, random);
            if (!replacement.equals(id)) {
                ItemStack upgraded = hook.createItem(replacement);
                if (MachineItems.hasItem(upgraded)) {
                    upgraded.setAmount(original.getAmount());
                    drops.set(i, upgraded);
                }
            }
        }
        if (tool.mode() == Mode.BONUS && productPresent && random.nextDouble() < tool.chance()) {
            ItemStack extra = hook.createItem(product);
            if (MachineItems.hasItem(extra)) {
                extra.setAmount(1);
                drops.add(extra);
            }
        }
    }

    void deliver(Player player, Block block, List<ItemStack> drops, String basket) {
        boolean collect = !basket.isBlank() && hook.isCustomItem(player.getInventory().getItemInOffHand(), basket);
        for (ItemStack drop : drops) {
            if (!MachineItems.hasItem(drop)) continue;
            if (collect) {
                int amount = drop.getAmount();
                String id = MachineItems.itemIdOf(hook, drop);
                var leftovers = player.getInventory().addItem(drop).values();
                int accepted = amount - leftovers.stream().mapToInt(ItemStack::getAmount).sum();
                leftovers.forEach(leftover ->
                        block.getWorld().dropItemNaturally(block.getLocation().add(.5, .5, .5), leftover));
                if (accepted > 0) beacon.recordItemObtained(player, id, accepted);
            } else block.getWorld().dropItemNaturally(block.getLocation().add(.5, .5, .5), drop);
        }
    }

    private int findSeed(Player player, String id) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (id.equals(MachineItems.itemIdOf(hook, contents[i]))) return i;
        }
        return -1;
    }

    private String blockId(Block block) {
        String id = hook.getBlockId(block);
        return id != null ? id : block.getType().getKey().toString();
    }

    private boolean mature(Block block, Crop spec) {
        if (spec == null) return false;
        Integer age = hook.getIntState(block, "age");
        if (age != null) return age >= spec.matureAge();
        return block.getBlockData() instanceof Ageable data && data.getAge() >= spec.matureAge();
    }

    private boolean available(Block block) {
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                && Bukkit.isOwnedByCurrentRegion(block.getLocation());
    }

    private boolean canTrace(Player player, double reach) {
        var start = player.getEyeLocation();
        var end = start.clone().add(start.getDirection().multiply(reach));
        for (int x = Math.min(start.getBlockX(), end.getBlockX()) >> 4;
             x <= Math.max(start.getBlockX(), end.getBlockX()) >> 4; x++) {
            for (int z = Math.min(start.getBlockZ(), end.getBlockZ()) >> 4;
                 z <= Math.max(start.getBlockZ(), end.getBlockZ()) >> 4; z++) {
                if (!start.getWorld().isChunkLoaded(x, z) || !Bukkit.isOwnedByCurrentRegion(start.getWorld(), x, z)) return false;
            }
        }
        return true;
    }

    private void damage(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE) player.damageItemStack(EquipmentSlot.HAND, 1);
    }

    private void finish(Player player, ItemStack hand) {
        player.swingMainHand();
        player.setCooldown(hand, 4);
    }
}
