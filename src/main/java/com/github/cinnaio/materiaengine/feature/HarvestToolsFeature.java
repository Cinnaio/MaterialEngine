package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.config.HarvestToolsConfig;
import com.github.cinnaio.materiaengine.config.HarvestToolsConfig.Crop;
import com.github.cinnaio.materiaengine.config.HarvestToolsConfig.Mode;
import com.github.cinnaio.materiaengine.config.HarvestToolsConfig.Tool;
import com.github.cinnaio.materiaengine.config.HarvestToolsConfig.Effect;
import com.github.cinnaio.materiaengine.integration.BeaconEngineBridge;
import com.github.cinnaio.materiaengine.i18n.MateriaEngineLang;
import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import com.github.cinnaio.materiaengine.util.MachineItems;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public final class HarvestToolsFeature implements Listener {
    private final JavaPlugin plugin;
    private final CraftEngineHook hook;
    private final FruitRegrowth regrowth;
    private final RandomGenerator random;
    private final BeaconEngineBridge beacon;
    private final MateriaEngineLang lang;
    private final HarvestStats stats;
    private SeedPouch seedPouch;
    private final Map<UUID, Long> lastFeedback = new ConcurrentHashMap<>();
    private volatile HarvestToolsConfig config;

    public HarvestToolsFeature(JavaPlugin plugin, CraftEngineHook hook, BeaconEngineBridge beacon) {
        this(plugin, hook, new FruitRegrowth(plugin, hook), HarvestToolsConfig.load(plugin.getConfig()),
                ThreadLocalRandom.current(), beacon, null, null);
        regrowth.start();
    }

    public HarvestToolsFeature(JavaPlugin plugin, CraftEngineHook hook, BeaconEngineBridge beacon,
                               MateriaEngineLang lang, HarvestStats stats) {
        this(plugin, hook, new FruitRegrowth(plugin, hook), HarvestToolsConfig.load(plugin.getConfig()),
                ThreadLocalRandom.current(), beacon, lang, stats);
        regrowth.start();
    }

    HarvestToolsFeature(JavaPlugin plugin, CraftEngineHook hook, FruitRegrowth regrowth,
                        HarvestToolsConfig config, RandomGenerator random, BeaconEngineBridge beacon) {
        this(plugin, hook, regrowth, config, random, beacon, null, null);
    }

    HarvestToolsFeature(JavaPlugin plugin, CraftEngineHook hook, FruitRegrowth regrowth,
                        HarvestToolsConfig config, RandomGenerator random, BeaconEngineBridge beacon,
                        MateriaEngineLang lang, HarvestStats stats) {
        this.plugin = plugin;
        this.hook = hook;
        this.regrowth = regrowth;
        this.config = config;
        this.random = random;
        this.beacon = beacon;
        this.lang = lang;
        this.stats = stats;
        if (this.stats != null) this.stats.configure(config.stats());
    }

    public boolean reload() {
        plugin.reloadConfig();
        try {
            HarvestToolsConfig candidate = HarvestToolsConfig.load(plugin.getConfig());
            if (stats != null) stats.configure(candidate.stats());
            config = candidate;
            return true;
        } catch (IllegalArgumentException error) {
            plugin.getLogger().warning("Harvest configuration rejected; keeping previous settings: " + error.getMessage());
            return false;
        }
    }

    public HarvestToolsConfig settings() { return config; }

    public void setSeedPouch(SeedPouch seedPouch) { this.seedPouch = seedPouch; }

    public void shutdown() {
        regrowth.shutdown();
        lastFeedback.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastFeedback.remove(event.getPlayer().getUniqueId());
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
        // Suppress vanilla hoe/shovel use while allowing the clicked container to open.
        event.setUseItemInHand(Event.Result.DENY);
        if (tool != null && tool.mode() == Mode.FRUIT) {
            if (!canTrace(player, tool.reach())) return;
            var hit = player.rayTraceBlocks(tool.reach(), FluidCollisionMode.NEVER);
            Block target = hit == null ? null : hit.getHitBlock();
            if (target == null || !available(target)) return;
            String targetId = hook.getBlockId(target);
            if (targetId == null || !settings.fruits().containsKey(targetId)) return;
            event.setCancelled(true);
            if (player.hasCooldown(hand)) {
                feedback(player, settings, settings.feedback().cooldown(), "harvest.feedback.cooldown", null);
                return;
            }
            if (!hook.isReady()) {
                feedback(player, settings, settings.feedback().failure(), "harvest.feedback.unavailable", null);
                return;
            }
            if (!Boolean.TRUE.equals(hook.getBooleanState(target, "fruiting"))) {
                feedback(player, settings, settings.feedback().failure(), "harvest.feedback.no-fruit", null);
                return;
            }
            HarvestResult result = harvestFruitResult(player, hand, target, settings);
            if (result.harvested()) {
                damage(player, tool.durabilityCost());
                finish(player, hand, tool.cooldownTicks());
                feedback(player, settings, settings.feedback().fruit(), "harvest.feedback.fruit", result.summary(), target.getLocation());
            } else {
                feedback(player, settings, settings.feedback().failure(), "harvest.feedback.failure", null);
            }
            return;
        }
        Block center = event.getClickedBlock();
        if (center == null || !available(center)) return;
        String centerId = blockId(center);
        Crop centerCrop = settings.crops().get(centerId);
        boolean recognized = centerCrop != null && (sickle || tool.targets().containsKey(centerId));
        if (!recognized) return;
        event.setCancelled(true);
        if (player.hasCooldown(hand)) {
            feedback(player, settings, settings.feedback().cooldown(), "harvest.feedback.cooldown", null);
            return;
        }
        if (!hook.isReady()) {
            feedback(player, settings, settings.feedback().failure(), "harvest.feedback.unavailable", null);
            return;
        }
        if (!mature(center, centerCrop)) {
            feedback(player, settings, settings.feedback().failure(), "harvest.feedback.immature", null);
            return;
        }
        HarvestResult result = harvestCropResult(player, hand, center, tool, settings);
        if (!result.harvested()) {
            feedback(player, settings, settings.feedback().failure(), "harvest.feedback.failure", null);
            return;
        }
        int cooldown = sickle ? settings.sickleCooldownTicks() : tool.cooldownTicks();
        damage(player, sickle ? settings.sickleDurabilityCost() : tool.durabilityCost());
        HarvestSummary summary = result.summary();
        if (sickle) {
            for (int dx = -settings.radius(); dx <= settings.radius(); dx++) {
                for (int dz = -settings.radius(); dz <= settings.radius(); dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (!hook.isCustomItem(player.getInventory().getItemInMainHand(), itemId)) {
                        finish(player, hand, cooldown);
                        feedback(player, settings, settings.feedback().success(), "harvest.feedback.success", summary, center.getLocation());
                        return;
                    }
                    Block block = center.getRelative(dx, 0, dz);
                    if (available(block)) {
                        HarvestResult neighbor = harvestCropResult(player, hand, block, null, settings);
                        if (neighbor.harvested()) {
                            summary = summary.add(neighbor.summary());
                            damage(player, settings.sickleDurabilityCost());
                        }
                    }
                }
            }
        }
        finish(player, hand, cooldown);
        feedback(player, settings, summary.qualityItems() > 0
                ? settings.feedback().quality() : summary.bonusItems() > 0
                ? settings.feedback().bonus() : settings.feedback().success(),
                summary.qualityItems() > 0 ? "harvest.feedback.quality"
                        : summary.bonusItems() > 0 ? "harvest.feedback.bonus" : "harvest.feedback.success", summary, center.getLocation());
    }

    boolean harvestCrop(Player player, ItemStack hand, Block block, Tool tool, HarvestToolsConfig settings) {
        return harvestCropResult(player, hand, block, tool, settings).harvested();
    }

    private HarvestResult harvestCropResult(Player player, ItemStack hand, Block block, Tool tool,
                                            HarvestToolsConfig settings) {
        String id = blockId(block);
        Crop crop = settings.crops().get(id);
        if (!mature(block, crop) || (tool != null && !tool.targets().containsKey(id))) return HarvestResult.empty();
        SeedSource seed = findSeed(player, crop.seed());
        if (!hook.canHarvest(player, block, seed != null)) return HarvestResult.empty();
        boolean custom = !id.startsWith("minecraft:");
        List<ItemStack> drops;
        if (custom) {
            drops = hook.customHarvestDrops(player, block);
            if (drops == null) return HarvestResult.empty();
        } else {
            BlockBreakEvent breaking = new BlockBreakEvent(block, player);
            Bukkit.getPluginManager().callEvent(breaking);
            if (breaking.isCancelled()) return HarvestResult.empty();
            drops = breaking.isDropItems() ? new ArrayList<>(block.getDrops(hand, player)) : new ArrayList<>();
        }
        BonusApplication application = applyBonus(drops, id, tool);
        PlayerHarvestBlockEvent harvesting = new PlayerHarvestBlockEvent(player, block, EquipmentSlot.HAND, drops);
        Bukkit.getPluginManager().callEvent(harvesting);
        if (harvesting.isCancelled() || !id.equals(blockId(block)) || !mature(block, crop)) return HarvestResult.empty();
        // Recheck inventory after callbacks. Newly awarded seeds are never used for this replant.
        seed = findSeed(player, crop.seed());
        if (seed != null && !hook.canHarvest(player, block, true)) return HarvestResult.empty();
        boolean changed;
        if (seed != null) {
            if (custom) changed = hook.setIntState(block, id, "age", 0);
            else {
                Ageable reset = (Ageable) block.getBlockData();
                reset.setAge(0);
                block.setBlockData(reset, false);
                changed = true;
            }
            if (changed && !consumeSeed(player, seed)) {
                plugin.getLogger().warning("Harvest seed source changed during replant; rewards withheld.");
                return HarvestResult.empty();
            }
        } else if (custom) changed = hook.removeHarvestedBlock(block, player);
        else {
            block.setType(Material.AIR, true);
            changed = true;
        }
        if (!changed) return HarvestResult.empty();
        List<ItemStack> harvested = harvesting.getItemsHarvested();
        String toolId = hook.getItemId(hand);
        if (toolId == null || toolId.isBlank()) toolId = settings.sickleItem();
        Delivery delivery = deliver(player, block, harvested, settings.basketItem());
        Map<String, HarvestStats.Output> produced = application.retained(delivery.produced());
        if (stats != null) stats.record(player, toolId, id, produced);
        return HarvestResult.success(produced, seed != null ? 1 : 0, seed == null ? 1 : 0, delivery.overflow());
    }

    boolean harvestFruit(Player player, ItemStack hand, Block block, HarvestToolsConfig settings) {
        return harvestFruitResult(player, hand, block, settings).harvested();
    }

    private HarvestResult harvestFruitResult(Player player, ItemStack hand, Block block, HarvestToolsConfig settings) {
        String id = hook.getBlockId(block);
        String fruit = settings.fruits().get(id == null ? "" : id);
        if (fruit == null || !Boolean.TRUE.equals(hook.getBooleanState(block, "fruiting"))
                || !hook.canHarvest(player, block, false)) return HarvestResult.empty();
        ItemStack drop = hook.createItem(fruit);
        if (!MachineItems.hasItem(drop)) return HarvestResult.empty();
        PlayerHarvestBlockEvent event = new PlayerHarvestBlockEvent(player, block, EquipmentSlot.HAND,
                new ArrayList<>(List.of(drop)));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || !id.equals(hook.getBlockId(block))
                || !Boolean.TRUE.equals(hook.getBooleanState(block, "fruiting"))) return HarvestResult.empty();
        if (!hook.setBooleanState(block, id, "fruiting", false)) return HarvestResult.empty();
        regrowth.record(block, id, settings.regrowSeconds(id));
        List<ItemStack> harvested = event.getItemsHarvested();
        String toolId = hook.getItemId(hand);
        if (toolId == null || toolId.isBlank()) toolId = "unknown";
        Delivery delivery = deliver(player, block, harvested, settings.basketItem());
        Map<String, HarvestStats.Output> produced = delivery.produced();
        if (stats != null) stats.record(player, toolId, id, produced);
        return HarvestResult.success(produced, 0, 0, delivery.overflow());
    }

    BonusApplication applyBonus(List<ItemStack> drops, String crop, Tool tool) {
        if (tool == null) return BonusApplication.empty();
        String product = tool.targets().get(crop);
        boolean productPresent = false;
        Map<String, Long> baseline = new HashMap<>();
        Map<String, Long> quality = new HashMap<>();
        Map<String, Long> bonus = new HashMap<>();
        for (ItemStack drop : drops) {
            String id = MachineItems.itemIdOf(hook, drop);
            if (id != null) baseline.merge(id, (long) drop.getAmount(), Long::sum);
        }
        for (int i = 0; i < drops.size(); i++) {
            ItemStack original = drops.get(i);
            String id = MachineItems.itemIdOf(hook, original);
            if (id == null) continue;
            productPresent |= id.equals(product);
            String replacement = tool.replacement(id, crop, random);
            if (!replacement.equals(id)) {
                ItemStack upgraded = hook.createItem(replacement);
                if (MachineItems.hasItem(upgraded)) {
                    upgraded.setAmount(original.getAmount());
                    drops.set(i, upgraded);
                    quality.merge(replacement, (long) Math.max(0, original.getAmount()), Long::sum);
                }
            }
        }
        if (tool.mode() == Mode.BONUS && productPresent && tool.bonusAmount() > 0 && random.nextDouble() < tool.chanceFor(crop)) {
            ItemStack extra = hook.createItem(product);
            if (MachineItems.hasItem(extra)) {
                extra.setAmount(tool.bonusAmount());
                drops.add(extra);
                bonus.put(product, (long) tool.bonusAmount());
            }
        }
        return new BonusApplication(baseline, quality, bonus);
    }

    Delivery deliver(Player player, Block block, List<ItemStack> drops, String basket) {
        Map<String, HarvestStats.Output> produced = new HashMap<>();
        long overflow = 0;
        boolean collect = !basket.isBlank() && hook.isCustomItem(player.getInventory().getItemInOffHand(), basket);
        for (ItemStack drop : drops) {
            if (!MachineItems.hasItem(drop)) continue;
            if (collect) {
                int amount = drop.getAmount();
                String id = MachineItems.itemIdOf(hook, drop);
                var leftovers = player.getInventory().addItem(drop).values();
                int accepted = amount - leftovers.stream().mapToInt(ItemStack::getAmount).sum();
                overflow += amount - accepted;
                leftovers.forEach(leftover -> dropOutput(block, leftover, produced));
                if (accepted > 0 && id != null) {
                    produced.merge(id, new HarvestStats.Output(accepted, 0, 0, 0), HarvestStats.Output::add);
                    beacon.recordItemObtained(player, id, accepted);
                }
            } else dropOutput(block, drop, produced);
        }
        return new Delivery(produced, overflow);
    }

    private void dropOutput(Block block, ItemStack drop, Map<String, HarvestStats.Output> produced) {
        var entity = block.getWorld().dropItemNaturally(block.getLocation().add(.5, .5, .5), drop);
        if (entity == null || !entity.isValid()) return;
        ItemStack spawned = entity.getItemStack();
        String id = MachineItems.itemIdOf(hook, spawned);
        if (id != null) produced.merge(id, new HarvestStats.Output(0, spawned.getAmount(), 0, 0), HarvestStats.Output::add);
    }

    private SeedSource findSeed(Player player, String id) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (id.equals(MachineItems.itemIdOf(hook, contents[i]))) return new SeedSource(id, i, null);
        }
        SeedPouch.Seed stored = seedPouch == null ? null : seedPouch.findSeed(player, id);
        return stored == null ? null : new SeedSource(id, -1, stored);
    }

    private boolean consumeSeed(Player player, SeedSource source) {
        if (source.pouch() != null) return seedPouch.consume(player, source.pouch());
        ItemStack seed = player.getInventory().getItem(source.slot());
        if (!source.id().equals(MachineItems.itemIdOf(hook, seed))) return false;
        seed.setAmount(seed.getAmount() - 1);
        player.getInventory().setItem(source.slot(), seed.getAmount() == 0 ? null : seed);
        return true;
    }

    private record SeedSource(String id, int slot, SeedPouch.Seed pouch) { }

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

    private void damage(Player player, int amount) {
        if (amount > 0 && player.getGameMode() != GameMode.CREATIVE) {
            player.damageItemStack(EquipmentSlot.HAND, amount);
        }
    }

    private void finish(Player player, ItemStack hand, int cooldownTicks) {
        player.swingMainHand();
        if (cooldownTicks > 0) player.setCooldown(hand, cooldownTicks);
    }

    private void feedback(Player player, HarvestToolsConfig settings, Effect effect, String key, HarvestSummary summary) {
        feedback(player, settings, effect, key, summary, player.getLocation());
    }

    private void feedback(Player player, HarvestToolsConfig settings, Effect effect, String key,
                          HarvestSummary summary, Location target) {
        HarvestToolsConfig.Feedback feedback = settings.feedback();
        if (!feedback.enabled()) return;
        long now = System.nanoTime();
        Long last = lastFeedback.get(player.getUniqueId());
        if (summary == null && last != null && now - last < feedback.failureIntervalTicks() * 50_000_000L) return;
        lastFeedback.put(player.getUniqueId(), now);
        playEffect(player, target, effect, feedback.sounds(), feedback.particles());
        if (feedback.actionbar() && lang != null) {
            String message = lang.text(player, key);
            if (summary != null) {
                if (summary.replanted() + summary.missingSeeds() > 0) message += lang.text(player, "harvest.feedback.replant");
                if (summary.overflow() > 0) message += lang.text(player, "harvest.feedback.overflow");
                message = message.replace("{blocks}", Long.toString(summary.blocks()))
                        .replace("{items}", Long.toString(summary.items()))
                        .replace("{quality}", Long.toString(summary.qualityItems()))
                        .replace("{bonus}", Long.toString(summary.bonusItems()))
                        .replace("{collected}", Long.toString(summary.collectedItems()))
                        .replace("{dropped}", Long.toString(summary.droppedItems()))
                        .replace("{replanted}", Long.toString(summary.replanted()))
                        .replace("{missing}", Long.toString(summary.missingSeeds()))
                        .replace("{overflow}", Long.toString(summary.overflow()));
            }
            player.sendActionBar(message);
        }
    }

    private void playEffect(Player player, Location target, Effect effect, boolean sounds, boolean particles) {
        if (effect == null || target == null || target.getWorld() == null) return;
        Location location = target.clone().add(.5, .5, .5);
        if (sounds && !effect.sound().isBlank()) {
            player.playSound(location, effect.sound(), SoundCategory.PLAYERS, effect.volume(), effect.pitch());
        }
        if (particles && effect.count() > 0 && !effect.particle().isBlank()) {
            player.spawnParticle(Particle.valueOf(effect.particle()), location,
                    effect.count(), effect.offset(), effect.offset(), effect.offset(), 0);
        }
    }

    private record BonusApplication(Map<String, Long> baseline, Map<String, Long> quality, Map<String, Long> bonus) {
        private static BonusApplication empty() { return new BonusApplication(Map.of(), Map.of(), Map.of()); }

        private Map<String, HarvestStats.Output> retained(Map<String, HarvestStats.Output> produced) {
            Map<String, HarvestStats.Output> result = new HashMap<>();
            produced.forEach((id, value) -> {
                long extra = Math.max(0, value.items() - baseline.getOrDefault(id, 0L));
                result.put(id, new HarvestStats.Output(value.collected(), value.dropped(),
                        Math.min(extra, quality.getOrDefault(id, 0L)), Math.min(extra, bonus.getOrDefault(id, 0L))));
            });
            return result;
        }
    }

    private record Delivery(Map<String, HarvestStats.Output> produced, long overflow) { }

    private record HarvestResult(boolean harvested, HarvestSummary summary) {
        private static HarvestResult empty() { return new HarvestResult(false, new HarvestSummary(0, 0, 0, 0, 0, 0, 0, 0, 0)); }

        private static HarvestResult success(Map<String, HarvestStats.Output> produced, long replanted, long missing, long overflow) {
            HarvestSummary summary = new HarvestSummary(1, 0, 0, 0, 0, 0, replanted, missing, overflow);
            for (HarvestStats.Output value : produced.values()) {
                summary = summary.add(new HarvestSummary(0, value.items(), value.quality(), value.bonus(), value.collected(), value.dropped(), 0, 0, 0));
            }
            return new HarvestResult(true, summary);
        }
    }

    private record HarvestSummary(long blocks, long items, long qualityItems, long bonusItems, long collectedItems, long droppedItems,
                                  long replanted, long missingSeeds, long overflow) {
        private HarvestSummary add(HarvestSummary other) {
            return new HarvestSummary(blocks + other.blocks, items + other.items,
                    qualityItems + other.qualityItems, bonusItems + other.bonusItems,
                    collectedItems + other.collectedItems, droppedItems + other.droppedItems,
                    replanted + other.replanted, missingSeeds + other.missingSeeds, overflow + other.overflow);
        }
    }
}
