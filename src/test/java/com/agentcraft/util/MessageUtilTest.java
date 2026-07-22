package com.agentcraft.util;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MessageUtilTest {

    @Test
    void sendPrefixesMessage() {
        Player player = mock(Player.class);
        MessageUtil.send(player, "hello");
        verify(player).sendMessage(contains("AgentCraft"));
        verify(player).sendMessage(contains("hello"));
    }

    @Test
    void colorHelpersWrapMessages() {
        assertEquals(ChatColor.WHITE + "info", MessageUtil.info("info"));
        assertEquals(ChatColor.GREEN + "ok", MessageUtil.success("ok"));
        assertEquals(ChatColor.RED + "bad", MessageUtil.error("bad"));
        assertEquals(ChatColor.YELLOW + "warn", MessageUtil.warning("warn"));
        assertEquals(ChatColor.AQUA + "hi" + ChatColor.WHITE, MessageUtil.highlight("hi"));
    }

    @Test
    void agentChatIncludesNameAndMessage() {
        String formatted = MessageUtil.agentChat("Bob", "hello there");
        assertTrue(formatted.contains("Bob"));
        assertTrue(formatted.contains("hello there"));
        assertTrue(formatted.contains(ChatColor.GREEN.toString()));
    }
}
