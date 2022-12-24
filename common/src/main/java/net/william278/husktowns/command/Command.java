package net.william278.husktowns.command;

import de.themoep.minedown.adventure.MineDown;
import net.william278.husktowns.HuskTowns;
import net.william278.husktowns.config.Locales;
import net.william278.husktowns.user.CommandUser;
import net.william278.husktowns.user.ConsoleUser;
import net.william278.paginedown.PaginatedList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class Command extends Node implements TabProvider {

    public List<ChildCommand> children;
    private Executable defaultExecutor;

    protected Command(@NotNull String name, @NotNull List<String> aliases, @NotNull HuskTowns plugin) {
        super(name, aliases, plugin);
    }

    public void setDefaultExecutor(@NotNull Executable defaultExecutor) {
        this.defaultExecutor = defaultExecutor;
    }

    @NotNull
    protected Executable getDefaultExecutor() {
        if (defaultExecutor == null) {
            throw new IllegalStateException("Default executor not set");
        }
        return defaultExecutor;
    }

    public void setChildren(@NotNull List<ChildCommand> children) {
        this.children = children;
    }

    @Override
    public final void execute(@NotNull CommandUser executor, @NotNull String[] args) {
        if (!executor.hasPermission(getPermission())) {
            plugin.getLocales().getLocale("error_no_permission")
                    .ifPresent(executor::sendMessage);
            return;
        }
        if (args.length >= 1 && !children.isEmpty()) {
            for (Node child : children) {
                if (child.matchesInput(args[0])) {
                    if (!executor.hasPermission(child.getPermission())) {
                        plugin.getLocales().getLocale("error_no_permission")
                                .ifPresent(executor::sendMessage);
                        return;
                    }
                    if (executor instanceof ConsoleUser && !isConsoleExecutable()) {
                        plugin.getLocales().getLocale("error_command_in_game_only")
                                .ifPresent(executor::sendMessage);
                        return;
                    }
                    child.execute(executor, removeFirstArg(args));
                    return;
                }
            }
            plugin.getLocales().getLocale("error_invalid_syntax", getUsage())
                    .ifPresent(executor::sendMessage);
            return;
        }
        if (executor instanceof ConsoleUser && !isConsoleExecutable()) {
            plugin.getLocales().getLocale("error_command_in_game_only")
                    .ifPresent(executor::sendMessage);
            return;
        }
        this.defaultExecutor.execute(executor, args);
    }

    @Override
    @Nullable
    public final List<String> suggest(@NotNull CommandUser user, @NotNull String[] args) {
        if (args.length <= 1) {
            return TabProvider.getMatchingNames(args[0], user, children);
        } else {
            for (final Node child : children) {
                if (child.matchesInput(args[0]) && child instanceof TabProvider provider) {
                    return provider.suggest(user, removeFirstArg(args));
                }
            }
        }
        return null;
    }

    @NotNull
    private String[] removeFirstArg(String[] args) {
        String[] newArgs = new String[args.length - 1];
        System.arraycopy(args, 1, newArgs, 0, args.length - 1);
        return newArgs;
    }

    @NotNull
    public final String getUsage() {
        final String arguments = children.stream().map(Node::getName).collect(Collectors.joining("|"));
        return "/" + getName() + " [" + plugin.getLocales().truncateText(arguments, 40) + "]";
    }

    @NotNull
    public final String getDescription() {
        return plugin.getLocales().getRawLocale("command_" + getName() + "_description")
                .or(() -> plugin.getLocales().getRawLocale("not_applicable"))
                .orElseThrow(() -> new IllegalStateException("Could not find command description for /" + getName()));
    }

    @NotNull
    protected MineDown getChildCommandList(@NotNull CommandUser user, final int page) {
        final Locales locales = plugin.getLocales();
        return PaginatedList.of(children.stream()
                                .filter(child -> child.canPerform(user))
                                .map(command -> locales.getRawLocale("command_list_item",
                                                Locales.escapeText(command.getUsage()),
                                                Locales.escapeText(command.getDescription().length() > 50
                                                        ? command.getDescription().substring(0, 49).trim() + "…"
                                                        : command.getDescription()),
                                                Locales.escapeText(locales.wrapText(command.getDescription(), 40)))
                                        .orElse(command.getUsage()))
                                .toList(),
                        locales.getBaseList(plugin.getSettings().listItemsPerPage)
                                .setHeaderFormat(locales.getRawLocale("child_command_list_title",
                                        "/" + getName()).orElse(""))
                                .setItemSeparator("\n").setCommand("/husktowns:" + getName() + " help")
                                .build())
                .getNearestValidPage(page);
    }

    @NotNull
    public Command.HelpCommand getHelpCommand() {
        return new HelpCommand(this, plugin);
    }

    public static class HelpCommand extends ChildCommand implements TabProvider {

        protected HelpCommand(@NotNull Command parent, @NotNull HuskTowns plugin) {
            super("help", List.of(), parent, "(page)", plugin);
        }

        @Override
        public void execute(@NotNull CommandUser executor, @NotNull String[] args) {
            int page = parseIntArg(args, 0).orElse(1);
            executor.sendMessage(parent.getChildCommandList(executor, page));
        }

        @Override
        @Nullable
        public List<String> suggest(@NotNull CommandUser user, @NotNull String[] args) {
            return Stream.iterate(1, i -> i + 1)
                    .limit(9)
                    .map(String::valueOf)
                    .collect(Collectors.toList());
        }
    }
}
