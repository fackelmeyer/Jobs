package com.gamingmesh.jobs.commands.list;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.commands.Cmd;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsPlayer;
import com.gamingmesh.jobs.i18n.Language;

import net.Zrips.CMILib.Container.CMIList;
import net.Zrips.CMILib.Container.PageInfo;
import net.Zrips.CMILib.Locale.LC;
import net.Zrips.CMILib.Messages.CMIMessages;
import net.Zrips.CMILib.Scoreboards.CMIScoreboard;
import net.Zrips.CMILib.Version.Schedulers.CMIScheduler;

public class ptop implements Cmd {

    @Override
    public Boolean perform(Jobs plugin, final CommandSender sender, final String[] args) {

        if (args.length < 1 || args.length > 2) {
            return false;
        }

        Player player = sender instanceof Player ? (Player) sender : null;

        int page = 0;
        Job job = null;

        for (String one : args) {
            if (one.equalsIgnoreCase("clear")) {
                if (player == null)
                    return false;

                player.getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
                CMIScoreboard.removeScoreBoard(player);

                return true;
            }

            if (job == null) {
                job = Jobs.getJob(one);
                if (job != null)
                    continue;
            }
            if (page < 1)
                try {
                    page = Integer.parseInt(one);
                } catch (NumberFormatException ignored) {
                }
        }

        if (job == null) {
            Language.sendMessage(sender, "command.ptop.error.nojob");
            return null;
        }

        if (page < 1)
            page = 1;

        final int finalPage = page;
        final Job finalJob = job;
        CMIScheduler.runTaskAsynchronously(plugin, () -> showPrestigeTop(sender, finalJob, finalPage));
        return true;
    }

    private static void showPrestigeTop(CommandSender sender, Job job, int page) {

        // Get all players with this job and sort by prestige
        List<UUID> allPlayers = job.getTopList(0);

        if (allPlayers.isEmpty()) {
            CMIMessages.sendMessage(sender, LC.info_NoInformation);
            return;
        }

        // Filter and sort by prestige (descending), then by effective level (descending)
        List<PrestigeEntry> prestigeList = new ArrayList<>();
        for (UUID uuid : allPlayers) {
            JobsPlayer jPlayer = Jobs.getPlayerManager().getJobsPlayer(uuid);
            if (jPlayer == null) continue;

            JobProgression progression = jPlayer.getJobProgression(job);
            if (progression == null || progression.getPrestige() <= 0) continue;

            prestigeList.add(new PrestigeEntry(uuid, jPlayer, progression));
        }

        if (prestigeList.isEmpty()) {
            Language.sendMessage(sender, "command.ptop.error.noprestige");
            return;
        }

        // Sort by prestige descending, then by effective level descending
        prestigeList.sort(Comparator
            .comparingInt((PrestigeEntry e) -> e.progression.getPrestige()).reversed()
            .thenComparingInt((PrestigeEntry e) -> e.progression.getEffectiveLevel()).reversed());

        int amount = Jobs.getGCManager().JobsTopAmount;
        PageInfo pi = new PageInfo(amount, prestigeList.size(), page);

        List<String> ls = new ArrayList<>();

        for (int i = 0; i < amount; i++) {

            if (prestigeList.size() <= i + pi.getStart())
                break;

            PrestigeEntry entry = prestigeList.get(i + pi.getStart());

            String prestigeStr = entry.progression.getPrestigeFormatted();

            if (Jobs.getGCManager().ShowToplistInScoreboard && sender instanceof Player)
                ls.add(Jobs.getLanguage().getMessage("scoreboard.line",
                    "%number%", pi.getPositionForOutput(i),
                    "%playername%", entry.jPlayer.getName(),
                    "%playerdisplayname%", entry.jPlayer.getDisplayName(),
                    "%level%", entry.progression.getEffectiveLevel(),
                    "%prestige%", prestigeStr,
                    "%exp%", entry.progression.getExperience()));
            else
                ls.add(Jobs.getLanguage().getMessage("command.ptop.output.list",
                    "%number%", pi.getPositionForOutput(i),
                    "%playername%", entry.jPlayer.getName(),
                    "%playerdisplayname%", entry.jPlayer.getDisplayName(),
                    "%level%", entry.progression.getEffectiveLevel(),
                    "%prestige%", entry.progression.getPrestige(),
                    "%prestigeformatted%", prestigeStr,
                    "%exp%", entry.progression.getExperience()));
        }

        if (Jobs.getGCManager().ShowToplistInScoreboard && sender instanceof Player) {
            CMIScoreboard.show((Player) sender, Jobs.getLanguage().getMessage("scoreboard.ptopline", job), ls, Jobs.getGCManager().ToplistInScoreboardInterval);
        } else {
            Language.sendMessage(sender, "command.ptop.output.topline", job, "%amount%", Jobs.getGCManager().JobsTopAmount);
            CMIMessages.sendMessage(sender, CMIList.listToString(ls));
        }

        pi.autoPagination(sender, "jobs ptop " + job.getName());
    }

    /**
     * Helper class to store prestige entry for sorting
     */
    private static class PrestigeEntry {
        final UUID uuid;
        final JobsPlayer jPlayer;
        final JobProgression progression;

        PrestigeEntry(UUID uuid, JobsPlayer jPlayer, JobProgression progression) {
            this.uuid = uuid;
            this.jPlayer = jPlayer;
            this.progression = progression;
        }
    }
}
