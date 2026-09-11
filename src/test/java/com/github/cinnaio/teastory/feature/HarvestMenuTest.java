package com.github.cinnaio.teastory.feature;

import com.github.cinnaio.teastory.config.HarvestToolsConfig;
import com.github.cinnaio.teastory.i18n.TeaStoryLang;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HarvestMenuTest {
    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final Inventory inventory = mock(Inventory.class);
    private final HarvestStats stats = mock(HarvestStats.class);
    private final TeaStoryLang lang = mock(TeaStoryLang.class);
    private final HarvestMenuItems items = mock(HarvestMenuItems.class);
    private final HarvestMenu menu = new HarvestMenu(mock(JavaPlugin.class), stats, lang, items, () -> mock(HarvestToolsConfig.class));

    private InventoryClickEvent click(int slot, ClickType type) {
        var event = mock(InventoryClickEvent.class);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(slot);
        when(event.getClick()).thenReturn(type);
        return event;
    }

    @Test
    void pagesAndPeriodsUseOwnedReadOnlyInventory() {
        when(player.getUniqueId()).thenReturn(id);
        when(player.hasPermission("materiaengine.harvest")).thenReturn(true);
        when(stats.summary(eq(id), any())).thenReturn(new HarvestStats.Summary(40, 80, 50, 30, 5, 3));
        when(stats.items(id, Integer.MAX_VALUE, HarvestPeriod.ALL)).thenReturn(IntStream.range(0, 40)
                .mapToObj(i -> new HarvestStats.ItemTotal("cgap:product_" + i, new HarvestStats.Output(2, 0, 0, 0))).toList());
        when(lang.text(eq(player), anyString())).thenAnswer(call -> call.getArgument(1));
        when(items.name(anyString())).thenReturn(Component.text("Product"));
        when(items.create(anyString(), any(), anyList(), anyBoolean())).thenReturn(mock(ItemStack.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(54), any(Component.class))).thenAnswer(call -> {
                when(inventory.getHolder()).thenReturn(call.getArgument(0));
                return inventory;
            });
            menu.open(player, id, "Farmer");
            verify(player).openInventory(inventory);
            verify(items, atLeastOnce()).create(eq("minecraft:gray_stained_glass_pane"), any(), anyList(), eq(false));
            verify(items, atLeastOnce()).create(eq("minecraft:chest"), any(), anyList(), eq(false));
            verify(items, atLeastOnce()).create(eq("minecraft:shears"), any(), anyList(), eq(false));
            menu.onClick(click(11, ClickType.LEFT));
            clearInvocations(items);
            menu.onClick(click(53, ClickType.LEFT));
            verify(items).create(eq("cgap:product_21"), any(), anyList(), eq(false));
            verify(items, never()).create(eq("cgap:product_0"), any(), anyList(), anyBoolean());
            menu.onClick(click(47, ClickType.LEFT));
            verify(stats).items(id, Integer.MAX_VALUE, HarvestPeriod.TODAY);
            for (ClickType type : List.of(ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.DROP, ClickType.SWAP_OFFHAND)) {
                var event = click(9, type);
                menu.onClick(event);
                verify(event).setCancelled(true);
            }
            var drag = mock(InventoryDragEvent.class);
            when(drag.getInventory()).thenReturn(inventory);
            menu.onDrag(drag);
            verify(drag).setCancelled(true);
            menu.shutdown();
            verify(inventory, atLeastOnce()).clear();
        }
    }

    @Test
    void anotherPlayersRecordsRequireSeparatePermission() {
        when(player.getUniqueId()).thenReturn(id);
        when(player.hasPermission("materiaengine.harvest")).thenReturn(true);
        when(lang.text(player, "command.no-permission")).thenReturn("denied");
        menu.open(player, UUID.randomUUID(), "Other");
        verifyNoInteractions(stats, inventory);
        verify(player).sendMessage("denied");
    }
}
