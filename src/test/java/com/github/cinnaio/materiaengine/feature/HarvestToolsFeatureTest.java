package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.config.HarvestToolsConfig;
import com.github.cinnaio.materiaengine.integration.BeaconEngineBridge;
import com.github.cinnaio.materiaengine.i18n.MateriaEngineLang;
import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.SoundCategory;
import org.bukkit.Particle;
import org.bukkit.entity.Item;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.Map;
import java.util.logging.Logger;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HarvestToolsFeatureTest {
    private final CraftEngineHook hook = mock(CraftEngineHook.class);
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final PluginManager events = mock(PluginManager.class);
    private final Block block = mock(Block.class);
    private final World world = mock(World.class);
    private final FruitRegrowth regrowth = mock(FruitRegrowth.class);
    private final BeaconEngineBridge beacon = mock(BeaconEngineBridge.class);
    private final HarvestStats stats = mock(HarvestStats.class);
    private final MateriaEngineLang lang = mock(MateriaEngineLang.class);
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Material itemMaterial = mock(Material.class);
    private final ItemStack[] storage = new ItemStack[36];
    private MockedStatic<Bukkit> bukkit;
    private HarvestToolsConfig config;
    private HarvestToolsFeature feature;
    private ItemStack hand;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(events);
        bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(any(Location.class))).thenReturn(true);
        bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(eq(world), anyInt(), anyInt())).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenAnswer(ignored -> new Location(world, 0, 64, 0));
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(player.getEyeLocation()).thenAnswer(ignored -> new Location(world, 0, 65, 0));
        when(inventory.getStorageContents()).thenReturn(storage);
        when(inventory.getItem(anyInt())).thenAnswer(call -> storage[call.getArgument(0, Integer.class)]);
        doAnswer(call -> { storage[call.getArgument(0, Integer.class)] = call.getArgument(1); return null; })
                .when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenAnswer(ignored -> new Location(world, 0, 64, 0));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.dropItemNaturally(any(Location.class), any(ItemStack.class))).thenAnswer(call -> {
            Item entity = mock(Item.class);
            when(entity.isValid()).thenReturn(true);
            when(entity.getItemStack()).thenReturn(call.getArgument(1));
            return entity;
        });
        when(hook.isReady()).thenReturn(true);
        when(hook.getBlockId(block)).thenReturn("cgap:mint_crop");
        when(hook.getIntState(block, "age")).thenReturn(3);
        when(hook.canHarvest(eq(player), any(Block.class), anyBoolean())).thenReturn(true);
        when(hook.removeHarvestedBlock(block, player)).thenReturn(true);
        when(hook.setIntState(block, "cgap:mint_crop", "age", 0)).thenReturn(true);
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        yaml.set("harvest-tools.tools.herb-shears.chance", 1);
        yaml.set("harvest-tools.tools.tea-shears.chance", 1);
        config = HarvestToolsConfig.load(yaml);
        when(plugin.getConfig()).thenReturn(yaml);
        var language = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/lang/us.yml"), StandardCharsets.UTF_8));
        when(lang.text(eq(player), anyString())).thenAnswer(call -> language.getString(call.getArgument(1)));
        feature = new HarvestToolsFeature(plugin, hook, regrowth, config, new Random(12), beacon, lang, stats);
        hand = stack("cgap:herb_shears", 1);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(hook.customHarvestDrops(player, block)).thenAnswer(ignored -> new ArrayList<>(
                List.of(stack("cgap:fresh_mint", 3), stack("cgap:mint_seeds", 1))));
    }

    @AfterEach
    void tearDown() { bukkit.close(); }

    private ItemStack stack(String id, int amount) {
        ItemStack item = mock(ItemStack.class);
        AtomicInteger count = new AtomicInteger(amount);
        when(item.getType()).thenReturn(itemMaterial);
        when(item.getAmount()).thenAnswer(ignored -> count.get());
        doAnswer(call -> { count.set(call.getArgument(0)); return null; }).when(item).setAmount(anyInt());
        when(hook.getItemId(item)).thenReturn(id);
        when(hook.isCustomItem(item, id)).thenReturn(true);
        return item;
    }

    private PlayerInteractEvent interact(EquipmentSlot slot) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, hand, block, BlockFace.UP, slot);
    }

    private void configure(Consumer<org.bukkit.configuration.ConfigurationSection> changes) {
        var yaml = plugin.getConfig();
        changes.accept(yaml);
        config = HarvestToolsConfig.load(yaml);
        feature = new HarvestToolsFeature(plugin, hook, regrowth, config, new Random(12), beacon, lang, stats);
    }

    @Test
    void cancelledInteractionAndOffHandCannotHarvest() {
        PlayerInteractEvent denied = interact(EquipmentSlot.HAND);
        denied.setUseInteractedBlock(Event.Result.DENY);
        feature.onInteract(denied);
        feature.onInteract(interact(EquipmentSlot.OFF_HAND));
        verify(hook, never()).customHarvestDrops(any(), any());
        verify(player, never()).damageItemStack(any(EquipmentSlot.class), anyInt());
    }

    @Test
    void immatureAndProtectedCropsDoNotConsumeSeedsOrDropItems() {
        storage[0] = stack("cgap:mint_seeds", 3);
        when(hook.getIntState(block, "age")).thenReturn(2);
        assertFalse(feature.harvestCrop(player, hand, block, config.tools().get("cgap:herb_shears"), config));
        when(hook.getIntState(block, "age")).thenReturn(3);
        when(hook.canHarvest(player, block, true)).thenReturn(false);
        assertFalse(feature.harvestCrop(player, hand, block, config.tools().get("cgap:herb_shears"), config));
        assertEquals(3, storage[0].getAmount());
        verify(hook, never()).customHarvestDrops(any(), any());
    }

    @Test
    void cancelledLootOrHarvestEventLeavesPlantAndInventoryUnchanged() {
        doReturn(null).when(hook).customHarvestDrops(player, block);
        assertFalse(feature.harvestCrop(player, hand, block, null, config));
        doReturn(new ArrayList<>()).when(hook).customHarvestDrops(player, block);
        doAnswer(call -> { if (call.getArgument(0) instanceof PlayerHarvestBlockEvent e) e.setCancelled(true); return null; })
                .when(events).callEvent(any(Event.class));
        assertFalse(feature.harvestCrop(player, hand, block, null, config));
        verify(hook, never()).removeHarvestedBlock(any(), any());
        verify(hook, never()).setIntState(any(), any(), any(), anyInt());
        verify(stats, never()).record(any(), anyString(), anyString(), anyMap());
    }

    @Test
    void failedBlockMutationDoesNotConsumeSeedOrAwardLoot() {
        storage[0] = stack("cgap:mint_seeds", 2);
        when(hook.setIntState(block, "cgap:mint_crop", "age", 0)).thenReturn(false);
        assertFalse(feature.harvestCrop(player, hand, block, null, config));
        assertEquals(2, storage[0].getAmount());
        verify(world, never()).dropItemNaturally(any(), any());
    }

    @Test
    void replantConsumesExactlyOneExistingSeedAndHarvestDamagesOnce() {
        storage[0] = stack("cgap:mint_seeds", 2);
        when(hook.createItem("cgap:fresh_mint")).thenAnswer(ignored -> stack("cgap:fresh_mint", 1));
        feature.onInteract(interact(EquipmentSlot.HAND));
        assertEquals(1, storage[0].getAmount());
        verify(hook).setIntState(block, "cgap:mint_crop", "age", 0);
        verify(player).damageItemStack(EquipmentSlot.HAND, 1);
        verify(world, times(3)).dropItemNaturally(any(), any());
        verify(player).sendActionBar(contains("Replanted 1"));
        verify(player).sendActionBar(contains("Missing seeds 0"));
    }

    @Test
    void noSeedRemovesCropAndDoesNotSpendNewlyDroppedSeeds() {
        assertTrue(feature.harvestCrop(player, hand, block, null, config));
        verify(hook).removeHarvestedBlock(block, player);
        verify(hook, never()).setIntState(any(), any(), any(), anyInt());
        verify(inventory, never()).setItem(anyInt(), any());
    }

    @Test
    void qualityKeepsOriginalQuantityAndLeavesSeedsUntouched() {
        ItemStack leaf = stack("cgap:fresh_tea_leaf_old_leaf", 2);
        ItemStack seed = stack("cgap:tea_seeds", 3);
        ItemStack premium = stack("cgap:fresh_tea_leaf_bud_leaf1", 1);
        when(hook.createItem(anyString())).thenReturn(premium);
        List<ItemStack> drops = new ArrayList<>(List.of(leaf, seed));
        feature.applyBonus(drops, "cgap:tea_tree_crop", config.tools().get("cgap:tea_shears"));
        assertSame(premium, drops.get(0));
        assertEquals(2, premium.getAmount());
        assertSame(seed, drops.get(1));
        assertEquals(3, seed.getAmount());
    }

    @Test
    void bonusAddsOneProductAndCannotCreateRewardsFromSuppressedLoot() {
        ItemStack extra = stack("cgap:fresh_mint", 1);
        when(hook.createItem("cgap:fresh_mint")).thenReturn(extra);
        List<ItemStack> drops = new ArrayList<>(List.of(stack("cgap:fresh_mint", 4), stack("cgap:mint_seeds", 2)));
        feature.applyBonus(drops, "cgap:mint_crop", config.tools().get("cgap:herb_shears"));
        assertEquals(3, drops.size());
        assertSame(extra, drops.get(2));
        assertEquals(1, extra.getAmount());
        List<ItemStack> suppressed = new ArrayList<>();
        feature.applyBonus(suppressed, "cgap:mint_crop", config.tools().get("cgap:herb_shears"));
        assertTrue(suppressed.isEmpty());
    }

    @Test
    void basketDropsOnlyInventoryOverflow() {
        ItemStack basket = stack("cgap:harvest_basket", 1);
        when(inventory.getItemInOffHand()).thenReturn(basket);
        ItemStack drop = stack("cgap:fresh_mint", 4);
        ItemStack overflow = stack("cgap:fresh_mint", 2);
        when(inventory.addItem(drop)).thenReturn(new HashMap<>(java.util.Map.of(0, overflow)));
        feature.deliver(player, block, List.of(drop), config.basketItem());
        verify(world).dropItemNaturally(any(), same(overflow));
        verify(world, never()).dropItemNaturally(any(), same(drop));
        verify(beacon).recordItemObtained(player, "cgap:fresh_mint", 2);
    }

    @Test
    void harvestedFruitCannotBeClaimedTwiceAndKeepsLeafBlock() {
        when(hook.getBlockId(block)).thenReturn("cgap:peach_leaves");
        when(hook.getBooleanState(block, "fruiting")).thenReturn(true);
        when(hook.createItem("cgap:peach")).thenAnswer(ignored -> stack("cgap:peach", 1));
        when(hook.setBooleanState(block, "cgap:peach_leaves", "fruiting", false)).thenAnswer(ignored -> {
            when(hook.getBooleanState(block, "fruiting")).thenReturn(false);
            return true;
        });
        assertTrue(feature.harvestFruit(player, hand, block, config));
        assertFalse(feature.harvestFruit(player, hand, block, config));
        verify(world).dropItemNaturally(any(), any());
        verify(regrowth).record(block, "cgap:peach_leaves", 1200);
        verify(hook, never()).removeHarvestedBlock(any(), any());
    }

    @Test
    void brokenSickleStopsBeforeHarvestingNeighboringPlants() {
        hand = stack("cgap:sickle", 1);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        doAnswer(ignored -> { when(inventory.getItemInMainHand()).thenReturn(null); return null; })
                .when(player).damageItemStack(EquipmentSlot.HAND, 1);
        feature.onInteract(interact(EquipmentSlot.HAND));
        verify(hook, times(1)).customHarvestDrops(any(), any());
        verify(block, never()).getRelative(anyInt(), anyInt(), anyInt());
    }

    @Test
    void cooldownStillPreventsVanillaHoeOrShovelAction() {
        when(player.hasCooldown(hand)).thenReturn(true);
        var event = interact(EquipmentSlot.HAND);
        feature.onInteract(event);
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
        verify(hook, never()).customHarvestDrops(any(), any());
    }

    @Test
    void unrelatedBlockInteractionIsNotCancelledByHarvestTool() {
        when(hook.getBlockId(block)).thenReturn("cgap:tea_table");
        var event = interact(EquipmentSlot.HAND);
        feature.onInteract(event);
        assertNotEquals(Event.Result.DENY, event.useInteractedBlock());
        assertEquals(Event.Result.DENY, event.useItemInHand());
        verify(hook, never()).customHarvestDrops(any(), any());
        verify(player, never()).damageItemStack(any(EquipmentSlot.class), anyInt());
    }

    @Test
    void configuredFeedbackAndActionCostsApplyOnceToSuccessfulHarvest() {
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        yaml.set("harvest-tools.tools.herb-shears.chance", 0);
        yaml.set("harvest-tools.tools.herb-shears.cooldown-ticks", 12);
        yaml.set("harvest-tools.tools.herb-shears.durability-cost", 2);
        yaml.set("harvest-tools.feedback.particles", false);
        config = HarvestToolsConfig.load(yaml);
        feature = new HarvestToolsFeature(plugin, hook, regrowth, config, new Random(12), beacon, lang, stats);
        Location location = new Location(world, 0, 64, 0);
        when(player.getLocation()).thenReturn(location);
        feature.onInteract(interact(EquipmentSlot.HAND));
        verify(player).damageItemStack(EquipmentSlot.HAND, 2);
        verify(player).setCooldown(hand, 12);
        verify(player).playSound(new Location(world, .5, 64.5, .5), "minecraft:block.grass.break", SoundCategory.PLAYERS, 0.65f, 1.1f);
    }

    @Test
    void ninePlantsProduceOneFeedbackAndNineStatisticsEntries() {
        hand = stack("cgap:sickle", 1);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            Block neighbor = mock(Block.class);
            when(block.getRelative(dx, 0, dz)).thenReturn(neighbor);
            when(neighbor.getWorld()).thenReturn(world);
            Location location = new Location(world, dx, 64, dz);
            when(neighbor.getLocation()).thenReturn(location);
            when(hook.getBlockId(neighbor)).thenReturn("cgap:mint_crop");
            when(hook.getIntState(neighbor, "age")).thenReturn(3);
            when(hook.removeHarvestedBlock(neighbor, player)).thenReturn(true);
            ItemStack mint = stack("cgap:fresh_mint", 1);
            when(hook.customHarvestDrops(player, neighbor)).thenReturn(new ArrayList<>(List.of(mint)));
        }
        feature.onInteract(interact(EquipmentSlot.HAND));
        verify(player, times(9)).damageItemStack(EquipmentSlot.HAND, 1);
        verify(player).setCooldown(hand, 4);
        verify(player).sendActionBar(contains("Harvested 9"));
        verify(player).sendActionBar(contains("Missing seeds 9"));
        verify(player, times(1)).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());
        verify(player).spawnParticle(eq(Particle.HAPPY_VILLAGER), any(Location.class), eq(4), eq(.25), eq(.25), eq(.25), eq(0.0));
        verify(stats, times(9)).record(eq(player), eq("cgap:sickle"), eq("cgap:mint_crop"), anyMap());
    }

    @Test
    void basketStatisticsDistinguishAcceptedOverflowAndRetainedBonus() {
        ItemStack basket = stack("cgap:harvest_basket", 1);
        when(inventory.getItemInOffHand()).thenReturn(basket);
        when(hook.createItem("cgap:fresh_mint")).thenAnswer(ignored -> stack("cgap:fresh_mint", 1));
        ItemStack overflow = stack("cgap:fresh_mint", 2);
        when(inventory.addItem(any(ItemStack.class))).thenAnswer(call -> {
            ItemStack item = call.getArgument(0);
            return new HashMap<>(item.getAmount() == 3 ? Map.of(0, overflow) : Map.of());
        });
        feature.onInteract(interact(EquipmentSlot.HAND));
        verify(stats).record(player, "cgap:herb_shears", "cgap:mint_crop", Map.of(
                "cgap:fresh_mint", new HarvestStats.Output(2, 2, 0, 1),
                "cgap:mint_seeds", new HarvestStats.Output(1, 0, 0, 0)));
        verify(player).sendActionBar(contains("Bonus 1"));
        verify(player).sendActionBar(contains("Stored 3 / Dropped 2"));
        verify(player).sendActionBar(contains("Inventory overflow 2"));
        verify(beacon, times(2)).recordItemObtained(player, "cgap:fresh_mint", 1);
    }

    @Test
    void eventSuppressedBonusAndCancelledItemSpawnsDoNotInflateStatistics() {
        when(hook.createItem("cgap:fresh_mint")).thenAnswer(ignored -> stack("cgap:fresh_mint", 1));
        doAnswer(call -> {
            if (call.getArgument(0) instanceof PlayerHarvestBlockEvent event) event.getItemsHarvested().removeLast();
            return null;
        }).when(events).callEvent(any(Event.class));
        feature.onInteract(interact(EquipmentSlot.HAND));
        verify(stats).record(player, "cgap:herb_shears", "cgap:mint_crop", Map.of(
                "cgap:fresh_mint", new HarvestStats.Output(0, 3, 0, 0),
                "cgap:mint_seeds", new HarvestStats.Output(0, 1, 0, 0)));
        clearInvocations(stats, player);
        Item cancelled = mock(Item.class);
        when(world.dropItemNaturally(any(), any())).thenReturn(cancelled);
        feature.onInteract(interact(EquipmentSlot.HAND));
        verify(stats).record(player, "cgap:herb_shears", "cgap:mint_crop", Map.of());
        verify(player).sendActionBar(contains("Stored 0 / Dropped 0"));
    }

    @Test
    void qualityStatisticsUseFinalItemIdsAndRetainedUpgradeAmounts() {
        configure(yaml -> yaml.createSection("harvest-tools.tools.tea-shears.quality-targets",
                Map.of("cgap:fresh_tea_leaf_bud", 1)));
        hand = stack("cgap:tea_shears", 1);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(hook.getBlockId(block)).thenReturn("cgap:tea_tree_crop");
        when(hook.getIntState(block, "age")).thenReturn(6);
        ItemStack leaf = stack("cgap:fresh_tea_leaf_old_leaf", 2);
        ItemStack premium = stack("cgap:fresh_tea_leaf_bud", 2);
        doReturn(new ArrayList<>(List.of(leaf, premium))).when(hook).customHarvestDrops(player, block);
        when(hook.createItem("cgap:fresh_tea_leaf_bud")).thenAnswer(ignored -> stack("cgap:fresh_tea_leaf_bud", 1));
        doAnswer(call -> {
            if (call.getArgument(0) instanceof PlayerHarvestBlockEvent event) event.getItemsHarvested().getFirst().setAmount(1);
            return null;
        }).when(events).callEvent(any(Event.class));
        feature.onInteract(interact(EquipmentSlot.HAND));
        verify(stats).record(player, "cgap:tea_shears", "cgap:tea_tree_crop", Map.of(
                "cgap:fresh_tea_leaf_bud", new HarvestStats.Output(0, 3, 1, 0)));
        verify(player).sendActionBar(contains("Upgraded 1"));
    }

    @Test
    void failureFeedbackIsThrottledAndCanBeDisabled() {
        configure(yaml -> yaml.set("harvest-tools.feedback.failure-interval-ticks", 200));
        when(hook.getIntState(block, "age")).thenReturn(2);
        feature.onInteract(interact(EquipmentSlot.HAND));
        feature.onInteract(interact(EquipmentSlot.HAND));
        verify(player, times(1)).sendActionBar(contains("not mature"));
        verify(stats, never()).record(any(), any(), any(), any());
        configure(yaml -> yaml.set("harvest-tools.feedback.enabled", false));
        clearInvocations(player);
        feature.onInteract(interact(EquipmentSlot.HAND));
        verify(player, never()).sendActionBar(anyString());
        verify(player, never()).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());
    }

    @Test
    void validReloadAppliesSettingsAndInvalidParticlePreservesLastConfiguration() {
        plugin.getConfig().set("harvest-tools.tools.herb-shears.cooldown-ticks", 17);
        plugin.getConfig().set("harvest-tools.feedback.actionbar", false);
        assertTrue(feature.reload());
        feature.onInteract(interact(EquipmentSlot.HAND));
        verify(player).setCooldown(hand, 17);
        verify(player, never()).sendActionBar(anyString());
        plugin.getConfig().set("harvest-tools.feedback.success.particle", "INVALID");
        plugin.getConfig().set("harvest-tools.tools.herb-shears.cooldown-ticks", 90);
        assertFalse(feature.reload());
        clearInvocations(player);
        feature.onInteract(interact(EquipmentSlot.HAND));
        verify(player).setCooldown(hand, 17);
    }

    @Test
    void fruitRayDoesNotAccessUnloadedOrForeignRegions() {
        hand = stack("cgap:fruit_picker", 1);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        feature.onInteract(interact(EquipmentSlot.HAND));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        bukkit.when(() -> Bukkit.isOwnedByCurrentRegion(eq(world), anyInt(), anyInt())).thenReturn(false);
        feature.onInteract(interact(EquipmentSlot.HAND));
        verify(player, never()).rayTraceBlocks(anyDouble(), any(FluidCollisionMode.class));
    }

    @Test
    void fruitPickerUsesFiveBlockRayForAirInteraction() {
        hand = stack("cgap:fruit_picker", 1);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        var air = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, hand, null, BlockFace.SELF, EquipmentSlot.HAND);
        // Bukkit predicts no vanilla block action for an air click, but custom item use is still allowed.
        air.setUseInteractedBlock(Event.Result.DENY);
        feature.onInteract(air);
        verify(player).rayTraceBlocks(5.0, FluidCollisionMode.NEVER);
        verify(hook, never()).setBooleanState(any(), any(), any(), anyBoolean());
    }

    @Test
    void eachNeighborGetsItsOwnProtectionCheck() {
        hand = stack("cgap:sickle", 1);
        when(inventory.getItemInMainHand()).thenReturn(hand);
        Block neighbor = mock(Block.class);
        when(block.getRelative(anyInt(), anyInt(), anyInt())).thenReturn(neighbor);
        when(neighbor.getWorld()).thenReturn(world);
        when(neighbor.getLocation()).thenAnswer(ignored -> new Location(world, 1, 64, 0));
        when(hook.getBlockId(neighbor)).thenReturn("cgap:mint_crop");
        when(hook.getIntState(neighbor, "age")).thenReturn(3);
        when(hook.canHarvest(player, neighbor, false)).thenReturn(false);
        feature.onInteract(interact(EquipmentSlot.HAND));
        verify(hook, times(8)).canHarvest(player, neighbor, false);
        verify(hook, never()).customHarvestDrops(player, neighbor);
    }

    @Test
    void vanillaCropHonorsBreakCancellationThenReplantsWithOneSeed() {
        when(hook.getBlockId(block)).thenReturn(null);
        when(hook.getIntState(block, "age")).thenReturn(null);
        Material wheat = mock(Material.class);
        when(wheat.getKey()).thenReturn(org.bukkit.NamespacedKey.minecraft("wheat"));
        when(block.getType()).thenReturn(wheat);
        Ageable age = mock(Ageable.class);
        when(age.getAge()).thenReturn(7);
        when(block.getBlockData()).thenReturn(age);
        storage[0] = stack("minecraft:wheat_seeds", 2);
        doAnswer(call -> { if (call.getArgument(0) instanceof BlockBreakEvent e) e.setCancelled(true); return null; })
                .when(events).callEvent(any(Event.class));
        assertFalse(feature.harvestCrop(player, hand, block, null, config));
        assertEquals(2, storage[0].getAmount());
        doNothing().when(events).callEvent(any(Event.class));
        ItemStack drop = stack("minecraft:wheat", 1);
        when(block.getDrops(hand, player)).thenReturn(List.of(drop));
        assertTrue(feature.harvestCrop(player, hand, block, null, config));
        assertEquals(1, storage[0].getAmount());
        verify(age).setAge(0);
        verify(block).setBlockData(age, false);
        verify(world).dropItemNaturally(any(), same(drop));
    }
}
