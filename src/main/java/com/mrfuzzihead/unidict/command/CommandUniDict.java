package com.mrfuzzihead.unidict.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import com.mrfuzzihead.unidict.UniDict;
import com.mrfuzzihead.unidict.report.UniDictReport;

/**
 * The {@code /unidict} command — the user-facing surface of the BB-1 transparency report
 * (docs/PLAN.md §BB-1). Currently exposes {@code /unidict report}, which prints, per unified
 * resource, the canonical (main) entry, every variant and owner mod, plus a per-integration rewrite
 * summary. Registered at {@code FMLServerStartingEvent} (see {@code CommonProxy.serverStarting}).
 */
public final class CommandUniDict extends CommandBase {

    @Override
    public String getCommandName() {
        return "unidict";
    }

    @Override
    public String getCommandUsage(final ICommandSender sender) {
        return "/unidict report — print the unification transparency report";
    }

    @Override
    public void processCommand(final ICommandSender sender, final String[] args) {
        final String sub = args.length == 0 ? "report" : args[0];
        if ("report".equals(sub)) {
            sender.addChatMessage(new ChatComponentText("[UniDict] " + getCommandUsage(sender)));
            for (final String line : UniDictReport.lines(UniDict.resourceHandler))
                sender.addChatMessage(new ChatComponentText(line));
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
