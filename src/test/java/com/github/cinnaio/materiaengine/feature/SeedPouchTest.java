package com.github.cinnaio.materiaengine.feature;

import com.github.cinnaio.materiaengine.config.HarvestToolsConfig;
import com.github.cinnaio.materiaengine.i18n.MateriaEngineLang;
import com.github.cinnaio.materiaengine.util.CraftEngineHook;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeedPouchTest {
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final CraftEngineHook hook = mock(CraftEngineHook.class);
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final MateriaEngineLang lang = mock(MateriaEngineLang.class);
    private final ItemStack[] slots = new ItemStack[41];
    private final Map<ItemStack, State> states = new IdentityHashMap<>();
    private final Map<Integer, ItemStack[]> encoded = new HashMap<>();
    private final AtomicInteger serial = new AtomicInteger();
    private final Material material = mock(Material.class);
    private MockedStatic<ItemStack> codec;
    private YamlConfiguration yaml;
    private SeedPouch pouch;

    @BeforeEach
    void setup() {
        when(plugin.getName()).thenReturn("MateriaEngine");
        when(plugin.namespace()).thenReturn("materiaengine");
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(lang.text(any(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItem(anyInt())).thenAnswer(call -> slots[call.getArgument(0, Integer.class)]);
        when(inventory.getStorageContents()).thenAnswer(call -> Arrays.copyOf(slots, 36));
        doAnswer(call -> { slots[call.getArgument(0, Integer.class)] = call.getArgument(1); return null; }).when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        when(hook.getItemId(any())).thenAnswer(call -> states.containsKey(call.getArgument(0)) ? states.get(call.getArgument(0)).id : null);
        when(hook.isCustomItem(any(), anyString())).thenAnswer(call -> states.containsKey(call.getArgument(0))
                && states.get(call.getArgument(0)).id.equals(call.getArgument(1)));
        yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        pouch = new SeedPouch(plugin, hook, lang, mock(HarvestMenuItems.class), () -> HarvestToolsConfig.load(yaml));
        codec = mockStatic(ItemStack.class);
        codec.when(() -> ItemStack.serializeItemsAsBytes(any(ItemStack[].class))).thenAnswer(call -> {
            int id = serial.incrementAndGet();
            encoded.put(id, copy(call.getArgument(0)));
            return ByteBuffer.allocate(4).putInt(id).array();
        });
        codec.when(() -> ItemStack.deserializeItemsFromBytes(any(byte[].class))).thenAnswer(call -> copy(encoded.get(ByteBuffer.wrap(call.getArgument(0)).getInt())));
        slots[0] = stack("cgap:seed_pouch", 1, "", Map.of());
    }

    @AfterEach void close() { if (codec != null) codec.close(); }

    private ItemStack[] copy(ItemStack[] items) {
        return Arrays.stream(items).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
    }

    private ItemStack stack(String id, int amount, String variant, Map<NamespacedKey, Object> data) {
        ItemStack item = mock(ItemStack.class);
        State state = new State(id, amount, variant, new HashMap<>(data));
        states.put(item, state);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenAnswer(call -> state.amount);
        when(item.getMaxStackSize()).thenReturn(id.equals("cgap:seed_pouch") ? 1 : 64);
        when(item.getItemMeta()).thenReturn(meta);
        when(item.setItemMeta(any())).thenReturn(true);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(NamespacedKey.class), any())).thenAnswer(call -> state.data.get(call.getArgument(0)));
        doAnswer(call -> { state.data.put(call.getArgument(0), call.getArgument(2)); return null; }).when(pdc).set(any(), any(), any());
        doAnswer(call -> { state.amount = call.getArgument(0); return null; }).when(item).setAmount(anyInt());
        doAnswer(call -> stack(state.id, state.amount, state.variant, state.data)).when(item).clone();
        when(item.isSimilar(any())).thenAnswer(call -> {
            State other = states.get(call.getArgument(0));
            return other != null && state.id.equals(other.id) && state.variant.equals(other.variant) && state.data.equals(other.data);
        });
        return item;
    }

    @Test
    void depositsKeepMetadataAndConsumptionRejectsTheSameStaleSnapshot() {
        slots[1] = stack("cgap:mint_seeds", 20, "heirloom", Map.of());
        assertEquals(20, pouch.deposit(player, pouch.read(player, 0), 1, Integer.MAX_VALUE));
        assertNull(slots[1]);
        var stored = pouch.read(player, 0);
        assertEquals("heirloom", states.get(stored.contents()[0]).variant);
        SeedPouch.Seed source = pouch.findSeed(player, "cgap:mint_seeds");
        assertNotNull(source);
        assertTrue(pouch.consume(player, source));
        assertFalse(pouch.consume(player, source));
        SeedPouch reopened = new SeedPouch(plugin, hook, lang, mock(HarvestMenuItems.class), () -> HarvestToolsConfig.load(yaml));
        assertEquals(19, reopened.read(player, 0).contents()[0].getAmount());
    }

    @Test
    void fullPouchesRejectOverflowNonSeedsAndNestedPouches() {
        for (int i = 0; i < 9; i++) {
            slots[1] = stack("cgap:mint_seeds", 64, "variant-" + i, Map.of());
            assertEquals(64, pouch.deposit(player, pouch.read(player, 0), 1, 64));
        }
        slots[1] = stack("cgap:mint_seeds", 2, "extra", Map.of());
        assertEquals(0, pouch.deposit(player, pouch.read(player, 0), 1, 2));
        assertEquals(2, slots[1].getAmount());
        slots[1] = stack("minecraft:stone", 64, "", Map.of());
        assertEquals(0, pouch.deposit(player, pouch.read(player, 0), 1, 64));
        slots[1] = stack("cgap:seed_pouch", 1, "", Map.of());
        assertEquals(0, pouch.deposit(player, pouch.read(player, 0), 1, 1));
    }

    @Test
    void movingTheBagPreventsOldMenuWritesAndReducedCapacityPreservesContents() {
        yaml.set("harvest-tools.seed-pouch.slots", 27);
        slots[1] = stack("cgap:mint_seeds", 8, "", Map.of());
        pouch.deposit(player, pouch.read(player, 0), 1, 4);
        var old = pouch.read(player, 0);
        slots[5] = slots[0];
        slots[0] = null;
        assertEquals(0, pouch.deposit(player, old, 1, 4));
        assertEquals(4, slots[1].getAmount());
        yaml.set("harvest-tools.seed-pouch.slots", 9);
        assertEquals(27, pouch.read(player, 5).contents().length);
        assertEquals(4, pouch.read(player, 5).contents()[0].getAmount());
    }

    @Test
    void partialWithdrawalLeavesUnacceptedSeedsInsideTheBag() {
        slots[1] = stack("cgap:mint_seeds", 10, "", Map.of());
        pouch.deposit(player, pouch.read(player, 0), 1, 10);
        for (int i = 1; i < 36; i++) slots[i] = stack("minecraft:stone", 64, "", Map.of());
        slots[1] = stack("cgap:mint_seeds", 61, "", Map.of());
        assertEquals(3, pouch.withdraw(player, pouch.read(player, 0), 0, 10));
        assertEquals(7, pouch.read(player, 0).contents()[0].getAmount());
        assertEquals(64, slots[1].getAmount());
    }

    @Test
    void serializationFailureNeitherAwardsNorConsumesSeeds() {
        slots[1] = stack("cgap:mint_seeds", 10, "", Map.of());
        pouch.deposit(player, pouch.read(player, 0), 1, 5);
        ItemStack before = slots[0];
        codec.when(() -> ItemStack.serializeItemsAsBytes(any(ItemStack[].class))).thenThrow(new IllegalStateException("codec failure"));
        assertEquals(0, pouch.withdraw(player, pouch.read(player, 0), 0, 5));
        assertEquals(0, pouch.deposit(player, pouch.read(player, 0), 1, 5));
        assertEquals(5, slots[1].getAmount());
        assertSame(before, slots[0]);
        assertEquals(5, pouch.read(player, 0).contents()[0].getAmount());
    }

    @Test
    void menuTransfersUpdatePhysicalContentsAndBlockAlternativeTransferPaths() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("materiaengine.harvest")).thenReturn(true);
        Inventory top = mock(Inventory.class);
        when(top.getSize()).thenReturn(18);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(18), any(Component.class))).thenAnswer(call -> {
                when(top.getHolder()).thenReturn(call.getArgument(0));
                return top;
            });
            pouch.openFirst(player);
            verify(player).openInventory(top);
            slots[1] = stack("cgap:mint_seeds", 10, "", Map.of());
            var click = mock(InventoryClickEvent.class);
            when(click.getInventory()).thenReturn(top);
            when(click.getWhoClicked()).thenReturn(player);
            when(click.getClickedInventory()).thenReturn(inventory);
            when(click.getSlot()).thenReturn(1);
            when(click.getRawSlot()).thenReturn(19);
            when(click.getClick()).thenReturn(ClickType.SHIFT_LEFT);
            pouch.onClick(click);
            assertNull(slots[1]);
            assertEquals(10, pouch.read(player, 0).contents()[0].getAmount());
            when(click.getRawSlot()).thenReturn(0);
            when(click.getClick()).thenReturn(ClickType.RIGHT);
            pouch.onClick(click);
            assertEquals(1, slots[1].getAmount());
            assertEquals(9, pouch.read(player, 0).contents()[0].getAmount());
            for (ClickType type : List.of(ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.DROP, ClickType.SWAP_OFFHAND, ClickType.MIDDLE)) {
                when(click.getClick()).thenReturn(type);
                pouch.onClick(click);
            }
            assertEquals(9, pouch.read(player, 0).contents()[0].getAmount());
            verify(click, times(7)).setCancelled(true);
            var drag = mock(InventoryDragEvent.class);
            when(drag.getInventory()).thenReturn(top);
            pouch.onDrag(drag);
            verify(drag).setCancelled(true);
            var close = mock(InventoryCloseEvent.class);
            when(close.getInventory()).thenReturn(top);
            pouch.onClose(close);
            assertEquals(9, pouch.read(player, 0).contents()[0].getAmount());
            verify(top, atLeastOnce()).clear();
        }
    }

    @Test
    void corruptedAndStackedPouchesAreNeverOverwritten() {
        byte[] invalid = {1};
        var key = new NamespacedKey(plugin, "seed_pouch_contents");
        states.get(slots[0]).data.put(key, invalid);
        assertNull(pouch.read(player, 0));
        assertNull(pouch.findSeed(player, "cgap:mint_seeds"));
        assertSame(invalid, slots[0].getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE_ARRAY));
        states.get(slots[0]).data.clear();
        slots[0].setAmount(2);
        assertNull(pouch.read(player, 0));
    }

    private static final class State {
        final String id;
        int amount;
        final String variant;
        final Map<NamespacedKey, Object> data;
        State(String id, int amount, String variant, Map<NamespacedKey, Object> data) {
            this.id = id; this.amount = amount; this.variant = variant; this.data = data;
        }
    }
}
