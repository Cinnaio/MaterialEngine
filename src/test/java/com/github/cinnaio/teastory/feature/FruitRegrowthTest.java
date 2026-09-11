package com.github.cinnaio.teastory.feature;

import com.github.cinnaio.teastory.util.CraftEngineHook;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FruitRegrowthTest {
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final CraftEngineHook hook = mock(CraftEngineHook.class);
    private final Block block = mock(Block.class);
    private final World world = mock(World.class);
    private final Chunk chunk = mock(Chunk.class);
    private MockedStatic<Bukkit> bukkit;
    private FruitRegrowth regrowth;

    @BeforeEach
    void setUp() {
        when(plugin.getName()).thenReturn("MateriaEngine");
        when(plugin.namespace()).thenReturn("materiaengine");
        bukkit = mockStatic(Bukkit.class);
        RegionScheduler scheduler = mock(RegionScheduler.class);
        bukkit.when(Bukkit::getRegionScheduler).thenReturn(scheduler);
        when(scheduler.runDelayed(eq(plugin), eq(world), anyInt(), anyInt(), any(), anyLong()))
                .thenReturn(mock(ScheduledTask.class));
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(block.getWorld()).thenReturn(world);
        when(block.getChunk()).thenReturn(chunk);
        when(block.getY()).thenReturn(80);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getBlock(0, 80, 0)).thenReturn(block);
        PersistentDataContainer chunkData = container();
        when(chunk.getPersistentDataContainer()).thenReturn(chunkData);
        when(hook.isReady()).thenReturn(true);
        when(hook.getBlockId(block)).thenReturn("cgap:peach_leaves");
        when(hook.getBooleanState(block, "persistent")).thenReturn(false);
        when(hook.getBooleanState(block, "fruiting")).thenReturn(false);
        when(hook.getIntState(block, "distance")).thenReturn(3);
        when(hook.setBooleanState(block, "cgap:peach_leaves", "fruiting", true)).thenReturn(true);
        regrowth = new FruitRegrowth(plugin, hook);
    }

    @AfterEach
    void tearDown() { bukkit.close(); }

    private PersistentDataContainer container() {
        Map<NamespacedKey, Object> values = new HashMap<>();
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        PersistentDataAdapterContext context = mock(PersistentDataAdapterContext.class);
        when(data.getAdapterContext()).thenReturn(context);
        when(context.newPersistentDataContainer()).thenAnswer(ignored -> container());
        doAnswer(call -> { values.put(call.getArgument(0), call.getArgument(2)); return null; })
                .when(data).set(any(), any(), any());
        when(data.get(any(), any())).thenAnswer(call -> values.get(call.getArgument(0)));
        when(data.getOrDefault(any(), any(), any())).thenAnswer(call ->
                values.getOrDefault(call.getArgument(0), call.getArgument(2)));
        when(data.getKeys()).thenAnswer(ignored -> Set.copyOf(values.keySet()));
        when(data.isEmpty()).thenAnswer(ignored -> values.isEmpty());
        doAnswer(call -> { values.remove(call.getArgument(0)); return null; }).when(data).remove(any());
        return data;
    }

    @Test
    void regrowthSurvivesNewServiceInstanceAndWaitsForDeadline() {
        regrowth.record(block, "cgap:peach_leaves", 1200);
        FruitRegrowth restarted = new FruitRegrowth(plugin, hook);
        assertTrue(restarted.process(chunk, System.currentTimeMillis()));
        verify(hook, never()).setBooleanState(any(), any(), any(), anyBoolean());
        assertFalse(restarted.process(chunk, System.currentTimeMillis() + 1200001));
        verify(hook).setBooleanState(block, "cgap:peach_leaves", "fruiting", true);
        assertTrue(chunk.getPersistentDataContainer().isEmpty());
    }

    @Test
    void removedLeavesAreNotRecreated() {
        regrowth.record(block, "cgap:peach_leaves", 1200);
        when(hook.getBlockId(block)).thenReturn(null);
        assertFalse(regrowth.process(chunk, Long.MAX_VALUE));
        verify(hook, never()).setBooleanState(any(), any(), any(), anyBoolean());
    }

    @Test
    void disconnectedAndPlacedLeavesCannotRegrow() {
        when(hook.getBooleanState(block, "persistent")).thenReturn(true);
        regrowth.record(block, "cgap:peach_leaves", 1200);
        assertTrue(chunk.getPersistentDataContainer().isEmpty());
        when(hook.getBooleanState(block, "persistent")).thenReturn(false);
        regrowth.record(block, "cgap:peach_leaves", 1200);
        when(hook.getIntState(block, "distance")).thenReturn(7);
        assertFalse(regrowth.process(chunk, Long.MAX_VALUE));
        verify(hook, never()).setBooleanState(any(), any(), any(), anyBoolean());
    }

    @Test
    void engineReloadAndFailedStateUpdatesKeepPendingEntryForRetry() {
        regrowth.record(block, "cgap:peach_leaves", 1200);
        when(hook.isReady()).thenReturn(false);
        assertTrue(regrowth.process(chunk, Long.MAX_VALUE));
        when(hook.isReady()).thenReturn(true);
        when(hook.setBooleanState(block, "cgap:peach_leaves", "fruiting", true)).thenReturn(false);
        assertTrue(regrowth.process(chunk, Long.MAX_VALUE));
        assertFalse(chunk.getPersistentDataContainer().isEmpty());
    }
}
