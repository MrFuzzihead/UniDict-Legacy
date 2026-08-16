package com.mrfuzzihead.unidict.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.report.UniDictReport;

/**
 * The {@code /unidict} command — the user-facing surface of the BB-1 transparency report
 * (docs/PLAN.md §BB-1). Exposes:
 * <ul>
 * <li>{@code /unidict report} — full report (all resources + rewrites + summary).</li>
 * <li>{@code /unidict report <resource>} — filtered to a single resource, e.g.
 * {@code /unidict report ingotCopper}.</li>
 * </ul>
 * Registered at {@code FMLServerStartingEvent} (see {@code CommonProxy.serverStarting}).
 */
public final class CommandUniDict extends CommandBase {

    @Override
    public String getCommandName() {
        return "unidict";
    }

    @Override
    public String getCommandUsage(final ICommandSender sender) {
        return "/unidict report [resource] — print the unification transparency report, optionally filtered to one resource";
    }

    @Override
    public void processCommand(final ICommandSender sender, final String[] args) {
        final String sub = args.length == 0 ? "report" : args[0];
        if ("report".equals(sub)) {
            // Optional trailing arg filters the loud 100-line dump to one resource, e.g.
            // "/unidict report ingotCopper" — the user-facing fix for BB-1 readability.
            final String filter = args.length > 1 ? args[1] : null;
            sender.addChatMessage(new ChatComponentText("[UniDict] " + getCommandUsage(sender)));
            for (final String line : UniDictReport.lines(UniDict.resourceHandler)) {
                if (filter == null || (line.startsWith("report resource=") && line.toLowerCase()
                    .contains(filter.toLowerCase()))) sender.addChatMessage(new ChatComponentText(line));
            }
        } else {
            sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
        }
    }

    /** The report is informational; readable by anyone with command access. */
    @Override
    public boolean canCommandSenderUseCommand(final ICommandSender sender) {
        return true;
    }
}
