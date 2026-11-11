package com.mrfuzzihead.unidict.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.pure.report.ReportPager;
import com.mrfuzzihead.unidict.report.UniDictReport;

/**
 * The {@code /unidict} command — the user-facing surface of the BB-1 transparency report
 * (docs/PLAN.md §BB-1). Exposes:
 * <ul>
 * <li>{@code /unidict report} — full report (all resources + rewrites + summary).</li>
 * <li>{@code /unidict report <resource> [page]} — filtered to a single resource, e.g.
 * {@code /unidict report ingotCopper 2}.</li>
 * </ul>
 * Registered at {@code FMLServerStartingEvent} (see {@code CommonProxy.serverStarting}).
 */
public final class CommandUniDict extends CommandBase {

    private static final int PAGE_SIZE = 20;

    @Override
    public String getCommandName() {
        return "unidict";
    }

    @Override
    public String getCommandUsage(final ICommandSender sender) {
        return "/unidict report [resource] [page] — print the unification transparency report, optionally filtered and paged";
    }

    @Override
    public void processCommand(final ICommandSender sender, final String[] args) {
        final String sub = args.length == 0 ? "report" : args[0];
        if ("report".equals(sub)) {
            // Optional arg 2 filters the loud dump to one resource, e.g. "/unidict report ingotCopper".
            // Arg 3 pages it, so a full report no longer sends one chat packet per line.
            final String filter = args.length > 1 ? args[1] : null;
            final int requestedPage = args.length > 2 ? parsePage(args[2]) : 1;
            final List<String> lines = new ArrayList<>();
            for (final String line : UniDictReport.lines(UniDict.resourceHandler)) {
                if (filter == null || (line.startsWith("report resource=") && line.toLowerCase()
                    .contains(filter.toLowerCase()))) lines.add(line);
            }
            final int pages = ReportPager.pageCount(lines.size(), PAGE_SIZE);
            if (pages == 0) {
                sender.addChatMessage(new ChatComponentText("[UniDict] no report lines match " + filter));
                return;
            }
            final int page = Math.min(Math.max(requestedPage, 1), pages);
            sender.addChatMessage(
                new ChatComponentText(
                    "[UniDict] report page " + page
                        + "/"
                        + pages
                        + (filter == null ? "" : " (filter=" + filter + ")")
                        + " — "
                        + getCommandUsage(sender)));
            for (final String line : ReportPager.page(lines, page, PAGE_SIZE)) {
                sender.addChatMessage(new ChatComponentText(line));
            }
        } else {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
        }
    }

    private static int parsePage(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException ignored) {
            return 1;
        }
    }

    /** The report is informational; readable by anyone with command access. */
    @Override
    public boolean canCommandSenderUseCommand(final ICommandSender sender) {
        return true;
    }
}
