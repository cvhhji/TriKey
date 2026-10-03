package io.github.cvhhji.trikey;

public final class ShellCommandPolicy {
    public static final int MAX_COMMAND_LENGTH = 4096;
    public static final String ROOT_EXECUTABLE = "/system/bin/su";

    private ShellCommandPolicy() {}

    public static boolean isValidCommand(String command) {
        return command != null
                && !command.trim().isEmpty()
                && command.length() <= MAX_COMMAND_LENGTH
                && command.indexOf('\0') < 0;
    }

    public static String[] rootCommand(String command) {
        return new String[]{ROOT_EXECUTABLE, "-c", command};
    }
}
