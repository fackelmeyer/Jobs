/**
 * Jobs Plugin for Bukkit
 * Copyright (C) 2011 Zak Ford <zak.j.ford@gmail.com>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.gamingmesh.jobs.container;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.economy.PaymentData;
import com.gamingmesh.jobs.i18n.Language;

import net.Zrips.CMILib.Container.CMINumber;
import net.Zrips.CMILib.Time.CMITimeManager;

public class JobProgression {
	private Job job;
	private JobsPlayer jPlayer;
	private double experience;
	private double lastExperience = 0;
	private double lastMoney = 0;
	private int level;
	private int prestige = 0;
	private transient int maxExperience = -1;
	private long leftOn = 0;

	public JobProgression(Job job, JobsPlayer jPlayer, int level, double experience) {
		this(job, jPlayer, level, experience, 0);
	}

	public JobProgression(Job job, JobsPlayer jPlayer, int level, double experience, int prestige) {
		this.job = job;
		this.jPlayer = jPlayer;
		this.experience = experience;
		this.level = level;
		this.prestige = prestige;

		JobsTop.updateTops(job, jPlayer, this);
	}

	/**
	 * Can the job level up?
	 * 
	 * @return true if the job can level up
	 * @return false if the job cannot
	 */
	public boolean canLevelUp() {
		return experience >= maxExperience;
	}

	/**
	 * Can the job level down?
	 * 
	 * @return true if the job can level up
	 * @return false if the job cannot
	 */
	public boolean canLevelDown() {
		return experience < 0;
	}

	/**
	 * Return the job
	 * 
	 * @return the job
	 */
	public Job getJob() {
		return job;
	}

	/**
	 * Set the job
	 * 
	 * @param job - the new job to be set
	 */
	public void setJob(Job job) {
//		synchronized (jPlayer.saveLock) {
		jPlayer.setSaved(false);
		this.job = job;
		reloadMaxExperienceAndCheckLevelUp();
//		}
	}

	/**
	 * Get the experience in this job
	 * 
	 * @return the experiece in this job
	 */
	public double getExperience() {
		return experience;
	}

	/**
	 * Adds experience for this job
	 * 
	 * @param experience - the experience in this job
	 * @return - job level up
	 */
	public boolean addExperience(double experience) {
		jPlayer.setSaved(false);
		this.experience += experience;
		lastExperience = getLastExperience() + experience;
		return checkLevelUp();
	}

	/**
	 * Sets experience for this job
	 * 
	 * @param experience - the experience in this job
	 * @return - job level up
	 */
	public boolean setExperience(double experience) {
		jPlayer.setSaved(false);
		this.experience = experience;
		return checkLevelUp();
	}

	/**
	 * Takes experience from this job
	 * 
	 * @param experience - the experience in this job
	 * @return - job level up
	 */
	public boolean takeExperience(double experience) {
		jPlayer.setSaved(false);
		this.experience -= experience;
		lastExperience = getLastExperience() + experience;
		return checkLevelUp();
	}

	/**
	 * Get the maximum experience for this level
	 * 
	 * @return the experience needed to level up
	 */
	public int getMaxExperience() {
		return maxExperience;
	}

	/**
	 * Get the current level of this job
	 * 
	 * @return the level of this job
	 */
	public int getLevel() {
		return level;
	}

	/**
	 * Get the current level of this job in formatted way
	 * 
	 * @return the level of this job
	 */
	public String getLevelFormatted() {
		return getLevelFormatted(level);
	}

	public static String getLevelFormatted(int level) {
		if (Jobs.getGCManager().RomanNumbers)
			return CMINumber.toRoman(level);
		return String.valueOf(level);
	}

	/**
	 * Sets the level of this job progression
	 * 
	 * @param level the new level for this job
	 * @return true if this progression can level up
	 */
	public boolean setLevel(int level) {
		jPlayer.setSaved(false);
		this.level = level;
		return reloadMaxExperienceAndCheckLevelUp();
	}

	/**
	 * Reloads max experience
	 */
	public void reloadMaxExperience() {
		Map<String, Double> param = new HashMap<>();
		param.put("joblevel", (double) level);
		param.put("numjobs", (double) jPlayer.getJobProgression().size());
		maxExperience = (int) job.getMaxExp(param);
	}

	public int getMaxExperience(int level) {
		Map<String, Double> param = new HashMap<>();
		param.put("joblevel", (double) level);
		param.put("numjobs", (double) jPlayer.getJobProgression().size());
		return (int) job.getMaxExp(param);
	}

	/**
	 * Performs a level up
	 *
	 * @returns if level up was performed
	 */
	private boolean checkLevelUp() {
		if (level == 1 && experience < 0)
			experience = 0;

		if (experience < 0)
			return checkLevelDown();

		boolean ret = false;
		while (canLevelUp()) {
			// Don't level up at max level
			if (job.getMaxLevel() > 0 && level >= jPlayer.getMaxJobLevelAllowed(job))
				break;

			level++;
			experience -= maxExperience;

			ret = true;
			reloadMaxExperience();
			jPlayer.reloadLimits();
		}

		// Auto-prestige when at max level and XP is full
		int maxLevel = jPlayer.getMaxJobLevelAllowed(job);
		if (maxLevel > 0 && level >= maxLevel && experience >= maxExperience) {
			if (canPrestige()) {
				int oldPrestige = prestige;
				double overflowExp = experience - maxExperience;
				prestige();
				// Apply overflow experience to new level
				if (overflowExp > 0) {
					experience = overflowExp;
					checkLevelUp(); // Recursively check for level ups with overflow
				}
				ret = true;

				// Notify player and give rewards
				Player player = jPlayer.getPlayer();
				if (player != null) {
					double incomeBonus = prestige * Jobs.getGCManager().PrestigeBonusPerLevel * 100;
					double pointsBonus = prestige * Jobs.getGCManager().PrestigePointsBonusPerLevel * 100;

					// Give money reward
					double moneyReward = prestige * Jobs.getGCManager().PrestigeMoneyReward;
					if (moneyReward > 0 && Jobs.getEconomy() != null) {
						Jobs.getEconomy().getEconomy().depositPlayer(player, moneyReward);
					}

					// Play sound
					if (Jobs.getGCManager().PrestigeSound) {
						player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
					}

					// Spawn firework
					if (Jobs.getGCManager().PrestigeFirework) {
						spawnPrestigeFirework(player);
					}

					// Send message
					Language.sendMessage(player, "message.prestige.auto",
						job,
						"%oldprestige%", oldPrestige,
						"%newprestige%", prestige,
						"%incomebonus%", String.format("%.0f", incomeBonus),
						"%pointsbonus%", String.format("%.0f", pointsBonus),
						"%moneyreward%", String.format("%.0f", moneyReward));
				}
			} else {
				// At max prestige, cap experience
				if (experience > maxExperience)
					experience = maxExperience;
			}
		} else if (experience > maxExperience && level >= maxLevel) {
			// At max level but not enough for prestige, cap experience
			experience = maxExperience;
		}

		JobsTop.updateTops(job, jPlayer, this);

		return ret;
	}

	/**
	 * Performs a level down
	 * 
	 * @returns if level down was performed
	 */
	private boolean checkLevelDown() {
		boolean ret = false;
		while (canLevelDown()) {
			if (
			// Don't level down at 1
			level <= 1 || !Jobs.getGCManager().AllowDelevel) {
				experience = 0;

				break;
			}

			level--;
			experience += getMaxExperience(level);

			ret = true;
			reloadMaxExperience();

			jPlayer.reloadLimits();
		}

		JobsTop.updateTops(job, jPlayer, this);

		return ret;
	}

	/**
	 * Reloads max experience and checks for level up Do this whenever job or level
	 * changes
	 * 
	 * @return if leveled up
	 */
	private boolean reloadMaxExperienceAndCheckLevelUp() {
		reloadMaxExperience();
		return checkLevelUp();
	}

	public Long getLeftOn() {
		return leftOn;
	}

	public JobProgression setLeftOn(Long leftOn) {
		this.leftOn = leftOn;
		return this;
	}

	public boolean canRejoin() {
		if (leftOn == 0 || leftOn + job.getRejoinCd() < System.currentTimeMillis())
			return true;

		org.bukkit.entity.Player player = jPlayer != null ? jPlayer.getPlayer() : null;
		return player != null && player.hasPermission("jobs.rejoinbypass");
	}

	public String getRejoinTimeMessage() {
		return leftOn == 0 ? "" : CMITimeManager.to24hourShort(leftOn + job.getRejoinCd() - System.currentTimeMillis());
	}

	public double getLastExperience() {
		return lastExperience;
	}

	public void setLastExperience(double lastExperience) {
		this.lastExperience = lastExperience;
	}

	public double getLastMoney() {
		return lastMoney;
	}

	public void setLastMoney(double lastMoney) {
		this.lastMoney = lastMoney;
	}

	/**
	 * Get the current prestige level
	 *
	 * @return the prestige level
	 */
	public int getPrestige() {
		return prestige;
	}

	/**
	 * Set the prestige level
	 *
	 * @param prestige the new prestige level
	 */
	public void setPrestige(int prestige) {
		jPlayer.setSaved(false);
		this.prestige = prestige;
		JobsTop.updateTops(job, jPlayer, this);
	}

	/**
	 * Increment prestige by 1 and reset level to 1
	 */
	public void prestige() {
		jPlayer.setSaved(false);
		this.prestige++;
		this.level = 1;
		this.experience = 0;
		reloadMaxExperience();
		JobsTop.updateTops(job, jPlayer, this);
	}

	/**
	 * Check if the player can prestige (at max level and not at max prestige)
	 *
	 * @return true if can prestige
	 */
	public boolean canPrestige() {
		int maxLevel = jPlayer.getMaxJobLevelAllowed(job);
		if (maxLevel <= 0 || level < maxLevel)
			return false;

		int maxPrestige = Jobs.getGCManager().MaxPrestigeLevel;
		if (maxPrestige > 0 && prestige >= maxPrestige)
			return false;

		return true;
	}

	/**
	 * Check if player is at max prestige level
	 *
	 * @return true if at max prestige
	 */
	public boolean isMaxPrestige() {
		int maxPrestige = Jobs.getGCManager().MaxPrestigeLevel;
		return maxPrestige > 0 && prestige >= maxPrestige;
	}

	/**
	 * Get the effective level including prestige for rankings
	 * Effective level = level + (prestige * maxLevel)
	 *
	 * @return the effective level
	 */
	public int getEffectiveLevel() {
		int maxLevel = job.getMaxLevel();
		if (maxLevel <= 0)
			maxLevel = 100; // Default fallback
		return level + (prestige * maxLevel);
	}

	/**
	 * Get formatted prestige string with color based on level
	 * Colors: 1=green, 2=yellow, 3=gold, 4=red, 5=dark_purple
	 *
	 * @return formatted prestige string or empty if no prestige
	 */
	public String getPrestigeFormatted() {
		if (prestige <= 0)
			return "";

		String color;
		switch (prestige) {
			case 1:
				color = "&a"; // Green
				break;
			case 2:
				color = "&e"; // Yellow
				break;
			case 3:
				color = "&6"; // Gold
				break;
			case 4:
				color = "&c"; // Red
				break;
			case 5:
			default:
				color = "&5"; // Dark Purple (max)
				break;
		}

		return " " + color + "[Prestige " + prestige + "]";
	}

	/**
	 * Spawns a firework at the player's location to celebrate prestige
	 *
	 * @param player the player to spawn firework for
	 */
	private void spawnPrestigeFirework(Player player) {
		Firework firework = (Firework) player.getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK_ROCKET);
		FireworkMeta meta = firework.getFireworkMeta();

		// Color based on prestige level
		Color color;
		switch (prestige) {
			case 1:
				color = Color.LIME;
				break;
			case 2:
				color = Color.YELLOW;
				break;
			case 3:
				color = Color.ORANGE;
				break;
			case 4:
				color = Color.RED;
				break;
			case 5:
			default:
				color = Color.PURPLE;
				break;
		}

		FireworkEffect effect = FireworkEffect.builder()
			.withColor(color)
			.withFade(Color.WHITE)
			.with(FireworkEffect.Type.BALL_LARGE)
			.trail(true)
			.flicker(true)
			.build();

		meta.addEffect(effect);
		meta.setPower(1);
		firework.setFireworkMeta(meta);
	}

}
