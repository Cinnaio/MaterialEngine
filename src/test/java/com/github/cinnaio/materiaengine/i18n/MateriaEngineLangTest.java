package com.github.cinnaio.materiaengine.i18n;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MateriaEngineLangTest {
    @TempDir Path directory;

    @Test
    void olderLanguageFilesKeepOverridesAndInheritNewHarvestMessages() throws Exception {
        Files.createDirectories(directory.resolve("lang"));
        Files.writeString(directory.resolve("lang/zh.yml"), "command:\n  reload-success: custom\n");
        Files.writeString(directory.resolve("lang/us.yml"), "command:\n  reload-success: custom\n");
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getResource(anyString())).thenAnswer(call -> getClass().getResourceAsStream("/" + call.getArgument(0)));
        MateriaEngineLang lang = new MateriaEngineLang(plugin);
        Player player = mock(Player.class);
        when(player.locale()).thenReturn(Locale.SIMPLIFIED_CHINESE);
        assertEquals("custom", lang.text(player, "command.reload-success"));
        assertTrue(lang.text(player, "harvest.feedback.success").contains("{blocks}"));
        assertFalse(lang.text(player, "harvest.stats.item").startsWith("harvest."));
        when(player.locale()).thenReturn(Locale.US);
        assertTrue(lang.text(player, "harvest.feedback.success").contains("Harvested"));
        lang.reload();
        assertEquals("custom", lang.text(player, "command.reload-success"));
        assertFalse(Files.readString(directory.resolve("lang/us.yml")).contains("harvest"));
    }
}
