package com.github.cinnaio.materiaengine.command;

import com.github.cinnaio.materiaengine.feature.HarvestStats;
import com.github.cinnaio.materiaengine.feature.HarvestToolsFeature;
import com.github.cinnaio.materiaengine.feature.SimpleProcessingMachineGui;
import com.github.cinnaio.materiaengine.feature.TeaTableGui;
import com.github.cinnaio.materiaengine.i18n.MateriaEngineLang;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReloadCommandTest {
    private final TeaTableGui teaTable = mock(TeaTableGui.class);
    private final SimpleProcessingMachineGui machine = mock(SimpleProcessingMachineGui.class);
    private final HarvestToolsFeature harvest = mock(HarvestToolsFeature.class);
    private final HarvestStats stats = mock(HarvestStats.class);
    private final MateriaEngineLang lang = mock(MateriaEngineLang.class);
    private final CommandSourceStack source = mock(CommandSourceStack.class);
    private final Player player = mock(Player.class);
    private final UUID playerId = UUID.randomUUID();
    private final ReloadCommand command = new ReloadCommand(teaTable, List.of(machine), harvest, lang, stats);

    @BeforeEach
    void setup() {
        when(source.getSender()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Farmer");
        when(stats.enabled()).thenReturn(true);
        when(stats.persistenceReady()).thenReturn(true);
        when(stats.summary(nullable(UUID.class))).thenReturn(new HarvestStats.Summary(2, 5, 3, 2, 1, 1));
        var language = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/lang/us.yml"), StandardCharsets.UTF_8));
        when(lang.text(any(CommandSender.class), anyString())).thenAnswer(call -> language.getString(call.getArgument(1)));
    }

    @Test
    void ownStatisticsRenderTotalsAndProductDetails() {
        when(stats.breakdown(playerId, 5)).thenReturn(List.of(new HarvestStats.Breakdown(
                "cgap:tea_shears", "cgap:tea_tree_crop", new HarvestStats.Summary(2, 5, 3, 2, 1, 1))));
        when(stats.items(playerId, 5)).thenReturn(List.of(new HarvestStats.ItemTotal(
                "cgap:fresh_tea_leaf_bud", new HarvestStats.Output(3, 2, 1, 1))));

        command.execute(source, new String[]{"harvest", "stats"});

        verify(stats).summary(playerId);
        verify(player).sendMessage(contains("Farmer"));
        verify(player).sendMessage(contains("2 harvests, 5 items (stored 3 / dropped 2), 1 upgraded, 1 bonus"));
        verify(player).sendMessage(contains("cgap:tea_shears"));
        verify(player).sendMessage(contains("cgap:fresh_tea_leaf_bud"));
        verify(player, never()).sendMessage(contains("{"));
        assertEquals("materiaengine.admin", command.permission());
    }

    @Test
    void consoleAndExplicitAllQueryGlobalStatistics() {
        CommandSender console = mock(CommandSender.class);
        when(source.getSender()).thenReturn(console);
        command.execute(source, new String[]{"harvest", "stats"});
        when(source.getSender()).thenReturn(player);
        command.execute(source, new String[]{"harvest", "stats", "all"});
        verify(stats, times(2)).summary(null);
        verify(console).sendMessage(contains("all"));
        verify(player).sendMessage(contains("all"));
    }

    @Test
    void onlineNamesAndOfflineUuidsResolveToTheRequestedPlayer() {
        UUID targetId = UUID.randomUUID();
        Player target = mock(Player.class);
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Orchard");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Orchard")).thenReturn(target);
            command.execute(source, new String[]{"harvest", "stats", "Orchard"});
            command.execute(source, new String[]{"harvest", "stats", targetId.toString()});
        }
        verify(stats, times(2)).summary(targetId);
        verify(player).sendMessage(contains("Orchard"));
        verify(player).sendMessage(contains(targetId.toString()));
    }

    @Test
    void unknownPlayerDoesNotFallBackToGlobalStatistics() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            command.execute(source, new String[]{"harvest", "stats", "unknown-player"});
        }
        verify(stats, never()).summary(any());
        verify(player).sendMessage(contains("Online player not found"));
    }

    @Test
    void disabledOrUnavailablePersistenceIsShownAlongsideExistingStatistics() {
        when(stats.enabled()).thenReturn(false);
        when(stats.persistenceReady()).thenReturn(false);
        command.execute(source, new String[]{"harvest", "stats"});
        verify(player).sendMessage(contains("tracking is paused"));
        verify(player).sendMessage(contains("session data only"));
        verify(player).sendMessage(contains("5 items"));
    }

    @Test
    void reloadReportsHarvestRejectionWithoutLosingMachineSaveAndReload() {
        command.execute(source, new String[]{"reload"});
        verify(teaTable).save();
        verify(machine).save();
        verify(lang).reload();
        verify(teaTable).reload();
        verify(machine).reload();
        verify(harvest).reload();
        verify(player).sendMessage(contains("invalid harvest configuration was rejected"));
        when(harvest.reload()).thenReturn(true);
        command.execute(source, new String[]{"reload"});
        verify(player).sendMessage(contains("Config reloaded"));
    }
}
