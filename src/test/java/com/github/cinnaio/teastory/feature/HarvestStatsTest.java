package com.github.cinnaio.teastory.feature;

import com.github.cinnaio.teastory.config.HarvestToolsConfig.Stats;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HarvestStatsTest {
    @TempDir Path directory;
    private JavaPlugin plugin;
    private Player player;
    private UUID playerId;

    @BeforeEach
    void setup() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        player = mock(Player.class);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
    }

    private HarvestStats open() {
        return new HarvestStats(plugin, new Stats(true, false, 30));
    }

    private void mint(HarvestStats stats) {
        stats.record(player, "cgap:herb_shears", "cgap:mint_crop",
                Map.of("cgap:fresh_mint", new HarvestStats.Output(2, 2, 0, 1)));
    }

    @Test
    void persistsPlayerToolCropAndQualityBreakdownsWithoutDuplicatingFlushes() {
        HarvestStats stats = open();
        assertTrue(stats.persistenceReady());
        mint(stats);
        mint(stats);
        stats.record(player, "cgap:tea_shears", "cgap:tea_tree_crop",
                Map.of("cgap:fresh_tea_leaf_bud", new HarvestStats.Output(2, 0, 2, 0)));
        var expected = new HarvestStats.Summary(3, 10, 6, 4, 2, 2);
        assertEquals(expected, stats.summary(playerId));
        assertEquals(2, stats.breakdown(playerId, 5).size());
        assertEquals("cgap:mint_crop", stats.breakdown(playerId, 5).getFirst().crop());
        assertEquals(2, stats.breakdown(playerId, 5).getFirst().totals().harvests());
        assertTrue(stats.flush());
        assertTrue(stats.flush());
        stats.shutdown();
        HarvestStats reloaded = open();
        assertEquals(expected, reloaded.summary(playerId));
        assertEquals(stats.items(playerId, 10), reloaded.items(playerId, 10));
        assertEquals(HarvestStats.Summary.empty(), reloaded.summary(UUID.randomUUID()));
        reloaded.shutdown();
    }

    @Test
    void disablingTrackingAndCreativeModePreserveExistingRecords() {
        HarvestStats stats = open();
        mint(stats);
        stats.configure(new Stats(false, false, 30));
        mint(stats);
        stats.configure(new Stats(true, false, 30));
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        mint(stats);
        assertEquals(1, stats.summary(playerId).harvests());
        stats.configure(new Stats(true, true, 30));
        mint(stats);
        stats.shutdown();
        HarvestStats reloaded = open();
        assertEquals(2, reloaded.summary(playerId).harvests());
        reloaded.shutdown();
    }

    @Test
    void failedBatchRollsBackBothTablesAndCanBeRetried() throws Exception {
        HarvestStats stats = open();
        String url = "jdbc:sqlite:" + directory.resolve("harvest_stats.db");
        try (var connection = DriverManager.getConnection(url); var sql = connection.createStatement()) {
            sql.execute("CREATE TRIGGER fail_output BEFORE INSERT ON harvest_outputs BEGIN SELECT RAISE(FAIL, 'test'); END");
            mint(stats);
            assertFalse(stats.flush());
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM harvest_stats")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            sql.execute("DROP TRIGGER fail_output");
        }
        mint(stats);
        assertTrue(stats.flush());
        stats.shutdown();
        HarvestStats reloaded = open();
        assertEquals(2, reloaded.summary(playerId).harvests());
        assertEquals(8, reloaded.summary(playerId).items());
        reloaded.shutdown();
    }

    @Test
    void concurrentHarvestsAndFlushesRetainEveryIncrement() throws Exception {
        HarvestStats stats = open();
        try (var workers = Executors.newFixedThreadPool(5)) {
            var tasks = new ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) tasks.add(workers.submit(() -> {
                for (int j = 0; j < 100; j++) mint(stats);
            }));
            tasks.add(workers.submit(() -> {
                for (int j = 0; j < 20; j++) assertTrue(stats.flush());
            }));
            for (var task : tasks) task.get();
        }
        stats.shutdown();
        HarvestStats reloaded = open();
        assertEquals(new HarvestStats.Summary(400, 1600, 800, 800, 0, 400), reloaded.summary(playerId));
        reloaded.shutdown();
    }

    @Test
    void failedLoadDoesNotOverwriteExistingDatabase() throws Exception {
        Path file = directory.resolve("harvest_stats.db");
        Files.writeString(file, "invalid database");
        byte[] original = Files.readAllBytes(file);
        HarvestStats stats = open();
        assertFalse(stats.persistenceReady());
        mint(stats);
        stats.shutdown();
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    private HarvestStats at(String instant) {
        return new HarvestStats(plugin, new Stats(true, false, 30), Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void dailyAndMondayWeekBoundariesUseConfiguredTimezoneAcrossRestarts() {
        HarvestStats sunday = at("2026-09-06T15:59:59Z");
        mint(sunday);
        sunday.shutdown();
        HarvestStats monday = at("2026-09-06T16:00:00Z");
        assertEquals(0, monday.summary(playerId, HarvestPeriod.TODAY).harvests());
        assertEquals(0, monday.summary(playerId, HarvestPeriod.WEEK).harvests());
        mint(monday);
        monday.shutdown();
        HarvestStats tuesday = at("2026-09-08T02:00:00Z");
        mint(tuesday);
        assertEquals(3, tuesday.summary(playerId).harvests());
        assertEquals(1, tuesday.summary(playerId, HarvestPeriod.TODAY).harvests());
        assertEquals(2, tuesday.summary(playerId, HarvestPeriod.WEEK).harvests());
        assertEquals(8, tuesday.items(playerId, 5, HarvestPeriod.WEEK).getFirst().output().items());
        assertEquals(1, tuesday.breakdown(playerId, 5, HarvestPeriod.TODAY).getFirst().totals().harvests());
        tuesday.shutdown();
    }

    @Test
    void existingTotalsAreNotBackfilledIntoDailyTables() throws Exception {
        HarvestStats existing = open();
        mint(existing);
        existing.shutdown();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("harvest_stats.db"));
             var statement = connection.createStatement()) {
            statement.execute("DROP TABLE harvest_daily_stats");
            statement.execute("DROP TABLE harvest_daily_outputs");
        }
        HarvestStats migrated = open();
        assertEquals(1, migrated.summary(playerId).harvests());
        assertEquals(0, migrated.summary(playerId, HarvestPeriod.TODAY).harvests());
        mint(migrated);
        assertEquals(2, migrated.summary(playerId).harvests());
        assertEquals(1, migrated.summary(playerId, HarvestPeriod.TODAY).harvests());
        migrated.shutdown();
    }

    @Test
    void dailyWriteFailureRollsBackLifetimeAndCanRetry() throws Exception {
        HarvestStats stats = open();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("harvest_stats.db"));
             var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER fail_daily BEFORE INSERT ON harvest_daily_outputs BEGIN SELECT RAISE(FAIL, 'test'); END");
            mint(stats);
            assertFalse(stats.flush());
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM harvest_stats")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            statement.execute("DROP TRIGGER fail_daily");
        }
        assertTrue(stats.flush());
        stats.shutdown();
        HarvestStats reloaded = open();
        assertEquals(1, reloaded.summary(playerId).harvests());
        assertEquals(1, reloaded.summary(playerId, HarvestPeriod.TODAY).harvests());
        reloaded.shutdown();
    }

    @Test
    void csvIncludesScopedRowsAndQuotesDelimitersWithoutSpreadsheetFormulas() throws Exception {
        HarvestStats stats = at("2026-09-08T02:00:00Z");
        stats.record(player, "tool,\"name", "cgap:mint_crop", Map.of("=danger", new HarvestStats.Output(1, 0, 0, 0)));
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        stats.record(other, "other-tool", "cgap:mint_crop", Map.of("cgap:fresh_mint", new HarvestStats.Output(2, 0, 0, 0)));
        Path file = stats.exportCsv(playerId, HarvestPeriod.TODAY);
        String csv = Files.readString(file);
        assertTrue(csv.startsWith("\uFEFFdate,player_uuid"));
        assertTrue(csv.contains("\"2026-09-08\""));
        assertTrue(csv.contains("\"tool,\"\"name\""));
        assertTrue(csv.contains("\"'=danger\""));
        assertFalse(csv.contains("other-tool"));
        assertEquals(3, csv.lines().count());
        assertTrue(file.startsWith(directory.resolve("exports")));
        stats.shutdown();
    }
}
