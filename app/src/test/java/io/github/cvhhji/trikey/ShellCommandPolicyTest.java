package io.github.cvhhji.trikey;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ShellCommandPolicyTest {
    @Test
    public void acceptsShortAndMultilineCommands() {
        assertTrue(ShellCommandPolicy.isValidCommand("id"));
        assertTrue(ShellCommandPolicy.isValidCommand("settings put system demo 1\nsettings get system demo"));
    }

    @Test
    public void rejectsEmptyOversizedAndNullByteCommands() {
        assertFalse(ShellCommandPolicy.isValidCommand("  \n  "));
        assertFalse(ShellCommandPolicy.isValidCommand("x".repeat(ShellCommandPolicy.MAX_COMMAND_LENGTH + 1)));
        assertFalse(ShellCommandPolicy.isValidCommand("id\u0000whoami"));
        assertFalse(ShellCommandPolicy.isValidCommand(null));
    }

    @Test
    public void invokesCommandsThroughSu() {
        assertArrayEquals(new String[]{"/system/bin/su", "-c", "id"},
                ShellCommandPolicy.rootCommand("id"));
    }
}
