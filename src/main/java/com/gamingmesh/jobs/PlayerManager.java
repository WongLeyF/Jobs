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

package com.gamingmesh.jobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Sound;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

import com.gamingmesh.jobs.CMILib.Version;
import com.gamingmesh.jobs.CMILib.ActionBarManager;
import com.gamingmesh.jobs.CMILib.CMIReflections;
import com.gamingmesh.jobs.api.JobsJoinEvent;
import com.gamingmesh.jobs.api.JobsLeaveEvent;
import com.gamingmesh.jobs.api.JobsLevelUpEvent;
import com.gamingmesh.jobs.container.ArchivedJobs;
import com.gamingmesh.jobs.container.Boost;
import com.gamingmesh.jobs.container.BoostMultiplier;
import com.gamingmesh.jobs.container.CurrencyType;
import com.gamingmesh.jobs.container.ItemBonusCache;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.JobCommands;
import com.gamingmesh.jobs.container.JobItems;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsPlayer;
import com.gamingmesh.jobs.container.Log;
import com.gamingmesh.jobs.container.PlayerInfo;
import com.gamingmesh.jobs.container.PlayerPoints;
import com.gamingmesh.jobs.dao.JobsDAO;
import com.gamingmesh.jobs.dao.JobsDAOData;
import com.gamingmesh.jobs.economy.PaymentData;
import com.gamingmesh.jobs.hooks.HookManager;
import com.gamingmesh.jobs.stuff.PerformCommands;
import com.gamingmesh.jobs.stuff.Util;

public class PlayerManager {

    private final ConcurrentMap<String, JobsPlayer> playersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, JobsPlayer> playersUUIDCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, JobsPlayer> players = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, JobsPlayer> playersUUID = new ConcurrentHashMap<>();

    private final String mobSpawnerMetadata = "jobsMobSpawner";

    private final Map<UUID, PlayerInfo> playerUUIDMap = new HashMap<>();
    private final Map<Integer, PlayerInfo> playerIdMap = new HashMap<>();
    private final Map<String, PlayerInfo> playerNameMap = new HashMap<>();

    private final Jobs plugin;

    public PlayerManager(Jobs plugin) {
	this.plugin = plugin;
    }

    /**
     * @deprecated Use {@link JobsPlayer#getPointsData} instead
     * @return {@link com.gamingmesh.jobs.economy.PointsData}
     */
    @Deprecated
    public com.gamingmesh.jobs.economy.PointsData getPointsData() {
	return Jobs.getPointsData();
    }

    /**
     * @return the cached mob spawner meta name
     */
    public String getMobSpawnerMetadata() {
	return mobSpawnerMetadata;
    }

    @Deprecated
    public int getMapSize() {
	return playerUUIDMap.size();
    }

    public void clearMaps() {
	playerUUIDMap.clear();
	playerIdMap.clear();
	playerNameMap.clear();
    }

    public void clearCache() {
	playersCache.clear();
	playersUUIDCache.clear();
	players.clear();
	playersUUID.clear();
    }

    public void addPlayerToMap(PlayerInfo info) {
	playerUUIDMap.put(info.getUuid(), info);
	playerIdMap.put(info.getID(), info);
	playerNameMap.put(info.getName().toLowerCase(), info);
    }

    public void addPlayerToCache(JobsPlayer jPlayer) {
	String jName = jPlayer.getName().toLowerCase();

	if (!playersCache.containsKey(jName))
	    playersCache.put(jName, jPlayer);

	if (jPlayer.getUniqueId() != null && !playersUUIDCache.containsKey(jPlayer.getUniqueId()))
	    playersUUIDCache.put(jPlayer.getUniqueId(), jPlayer);
    }

    public void addPlayer(JobsPlayer jPlayer) {
	String jName = jPlayer.getName().toLowerCase();

	if (!players.containsKey(jName))
	    players.put(jName, jPlayer);

	if (jPlayer.getUniqueId() != null && !playersUUID.containsKey(jPlayer.getUniqueId()))
	    playersUUID.put(jPlayer.getUniqueId(), jPlayer);
    }

    /**
     * Removes the given player from the memory.
     * 
     * @param player {@link Player}
     * @return {@link JobsPlayer}
     */
    public JobsPlayer removePlayer(Player player) {
	if (player == null)
	    return null;

	players.remove(player.getName().toLowerCase());

	return playersUUID.remove(player.getUniqueId());
    }

    public ConcurrentMap<UUID, JobsPlayer> getPlayersCache() {
	return playersUUIDCache;
    }

    public Map<UUID, PlayerInfo> getPlayersInfoUUIDMap() {
	return playerUUIDMap;
    }

    /**
     * Returns the player identifier by its name. This will returns
     * -1 if the player is not cached.
     * 
     * @param name the player name
     * @return the identifier
     */
    public int getPlayerId(String name) {
	PlayerInfo info = getPlayerInfo(name);
	return info == null ? -1 : info.getID();
    }

    /**
     * Returns the player identifier by its uuid. This will returns
     * -1 if the player is not cached.
     * 
     * @param uuid player {@link UUID}
     * @return the identifier
     */
    public int getPlayerId(UUID uuid) {
	PlayerInfo info = playerUUIDMap.get(uuid);
	return info == null ? -1 : info.getID();
    }

    /**
     * Returns the {@link PlayerInfo} for the given name. This will returns
     * null if the player is not cached.
     * 
     * @param name the player name
     * @return {@link PlayerInfo}
     */
    public PlayerInfo getPlayerInfo(String name) {
	return playerNameMap.get(name.toLowerCase());
    }

    /**
     * Returns the {@link PlayerInfo} for the given identifier. This will returns
     * null if the player is not cached.
     * 
     * @param id the player id
     * @return {@link PlayerInfo}
     */
    public PlayerInfo getPlayerInfo(int id) {
	return playerIdMap.get(id);
    }

    /**
     * Returns the {@link PlayerInfo} for the given uuid. This will returns
     * null if the player is not cached.
     * 
     * @param uuid player {@link UUID}
     * @return {@link PlayerInfo}
     */
    public PlayerInfo getPlayerInfo(UUID uuid) {
	return playerUUIDMap.get(uuid);
    }

    /**
     * Handles join of new player synchronously. This can be called
     * within an asynchronous operation in order to load the player
     * from database if it is not cached into memory.
     * 
     * @param player {@link Player}
     */
    public void playerJoin(Player player) {
	JobsPlayer jPlayer = playersUUIDCache.get(player.getUniqueId());

	if (jPlayer == null || Jobs.getGCManager().MultiServerCompatability()) {
	    if (jPlayer != null)
		jPlayer = Jobs.getJobsDAO().loadFromDao(jPlayer);
	    else
		jPlayer = Jobs.getJobsDAO().loadFromDao(player);

	    if (Jobs.getGCManager().MultiServerCompatability()) {
		jPlayer.setArchivedJobs(Jobs.getJobsDAO().getArchivedJobs(jPlayer));
		jPlayer.setPaymentLimit(Jobs.getJobsDAO().getPlayersLimits(jPlayer));
		jPlayer.setPoints(Jobs.getJobsDAO().getPlayerPoints(jPlayer));
	    }

	    // Lets load quest progression
	    PlayerInfo info = Jobs.getJobsDAO().loadPlayerData(player.getUniqueId());
	    if (info != null) {
		jPlayer.setDoneQuests(info.getQuestsDone());
		jPlayer.setQuestProgressionFromString(info.getQuestProgression());
	    }

	    Jobs.getJobsDAO().loadLog(jPlayer);
	}

	addPlayer(jPlayer);
	autoJoinJobs(player);
	jPlayer.onConnect();
	jPlayer.reloadHonorific();
	Jobs.getPermissionHandler().recalculatePermissions(jPlayer);
    }

    /**
     * Handles player quit
     * 
     * @param player {@link Player}
     */
    public void playerQuit(Player player) {
	JobsPlayer jPlayer = getJobsPlayer(player);
	if (jPlayer == null)
	    return;

	jPlayer.onDisconnect();
	if (Jobs.getGCManager().saveOnDisconnect()) {
	    jPlayer.setSaved(false);
	    jPlayer.save();
	}
    }

    /**
     * Removes all jobs player miscellaneous settings like boss bar.
     */
    public void removePlayerAdditions() {
	for (JobsPlayer jPlayer : players.values()) {
	    jPlayer.clearBossMaps();
	    jPlayer.getUpdateBossBarFor().clear();
	}
    }

    /**
     * Save all the information of all of the players
     */
    public void saveAll() {
	/*
	 * Saving is a three step process to minimize synchronization locks when called asynchronously.
	 * 
	 * 1) Safely copy list for saving.
	 * 2) Perform save on all players on copied list.
	 * 3) Garbage collect the real list to remove any offline players with saved data
	 */
	for (JobsPlayer jPlayer : new ArrayList<>(players.values()))
	    jPlayer.save();

	Iterator<JobsPlayer> iter = players.values().iterator();
	while (iter.hasNext()) {
	    JobsPlayer jPlayer = iter.next();
	    if (!jPlayer.isOnline() && jPlayer.isSaved())
		iter.remove();
	}

	Jobs.getBpManager().saveCache();
    }

    /**
     * Converts the cache of all the players into a new one.
     * 
     * @param resetID true to not insert into database and reset the players id
     */
    public void convertChacheOfPlayers(boolean resetID) {
	int y = 0, i = 0, total = playersUUIDCache.size();

	for (JobsPlayer jPlayer : playersUUIDCache.values()) {
	    if (resetID)
		jPlayer.setUserId(-1);

	    JobsDAO dao = Jobs.getJobsDAO();
	    dao.updateSeen(jPlayer);

	    if (!resetID && jPlayer.getUserId() == -1)
		continue;

	    for (JobProgression oneJ : jPlayer.getJobProgression())
		dao.insertJob(jPlayer, oneJ);

	    dao.saveLog(jPlayer);
	    dao.savePoints(jPlayer);
	    dao.recordPlayersLimits(jPlayer);

	    i++;

	    if (y++ >= 1000) {
		Jobs.consoleMsg("&e[Jobs] Saved " + i + "/" + total + " players data");
		y = 0;
	    }
	}
    }

    /**
     * Gets the player job info for specific player if exist.
     * <p>
     * This can return null sometimes if the given player
     * is not cached into memory.
     * 
     * @param player {@link Player}
     * @return {@link JobsPlayer} the player job info of the player
     */
    public JobsPlayer getJobsPlayer(Player player) {
	return getJobsPlayer(player.getUniqueId());
    }

    /**
     * Gets the player job info for specific player uuid if exist.
     * <p>
     * This can return null sometimes if the given player
     * is not cached into memory.
     * 
     * @param player the player uuid
     * @return {@link JobsPlayer} the player job info of the player
     */
    public JobsPlayer getJobsPlayer(UUID uuid) {
	JobsPlayer jPlayer = playersUUID.get(uuid);
	return jPlayer != null ? jPlayer : playersUUIDCache.get(uuid);
    }

    /**
     * Get the player job info for specific player name if exist.
     * <p>
     * This can return null sometimes if the given player
     * is not cached into memory.
     * 
     * @param player name - the player name who's job you're getting
     * @return {@link JobsPlayer} the player job info of the player
     */
    public JobsPlayer getJobsPlayer(String playerName) {
	playerName = playerName.toLowerCase();

	JobsPlayer jPlayer = players.get(playerName);
	return jPlayer != null ? jPlayer : playersCache.get(playerName);
    }

    /**
     * Gets the player job offline data for specific {@link PlayerInfo}
     * 
     * @param info {@link PlayerInfo}
     * @param jobs the list of jobs data from database
     * @param points {@link PlayerPoints}
     * @param logs the map of logs
     * @param archivedJobs {@link ArchivedJobs}
     * @param limits {@link PaymentData}
     * @return {@link JobsPlayer}
     */
    public JobsPlayer getJobsPlayerOffline(PlayerInfo info, List<JobsDAOData> jobs, PlayerPoints points,
	Map<String, Log> logs, ArchivedJobs archivedJobs, PaymentData limits) {
	if (info == null)
	    return null;

	JobsPlayer jPlayer = new JobsPlayer(info.getName());
	jPlayer.setPlayerUUID(info.getUuid());
	jPlayer.setUserId(info.getID());
	jPlayer.setDoneQuests(info.getQuestsDone());
	jPlayer.setQuestProgressionFromString(info.getQuestProgression());

	if (jobs != null) {
	    for (JobsDAOData jobdata : jobs) {
		Job job = Jobs.getJob(jobdata.getJobName());
		if (job != null) {
		    jPlayer.progression.add(new JobProgression(job, jPlayer, jobdata.getLevel(), jobdata.getExperience()));
		    jPlayer.reloadMaxExperience();
		    jPlayer.reloadLimits();
		}
	    }
	}

	if (points != null)
	    jPlayer.setPoints(points);

	if (logs != null)
	    jPlayer.setLog(logs);

	if (limits != null)
	    jPlayer.setPaymentLimit(limits);

	if (archivedJobs != null) {
	    ArchivedJobs aj = new ArchivedJobs();

	    for (JobProgression one : archivedJobs.getArchivedJobs()) {
		JobProgression jp = new JobProgression(one.getJob(), jPlayer, one.getLevel(), one.getExperience());
		jp.reloadMaxExperience();

		if (one.getLeftOn() != null && one.getLeftOn() != 0L)
		    jp.setLeftOn(one.getLeftOn());

		aj.addArchivedJob(jp);
	    }

	    jPlayer.setArchivedJobs(aj);
	}

	return jPlayer;
    }

    /**
     * Causes player to join to the given job.
     * 
     * @param jPlayer {@link JobsPlayer}
     * @param job {@link Job}
     */
    public void joinJob(JobsPlayer jPlayer, Job job) {
	if (jPlayer.isInJob(job))
	    return;
	// let the user join the job
	if (!jPlayer.joinJob(job))
	    return;

	// JobsJoin event
	JobsJoinEvent jobsjoinevent = new JobsJoinEvent(jPlayer, job);
	Bukkit.getServer().getPluginManager().callEvent(jobsjoinevent);
	// If event is canceled, dont do anything
	if (jobsjoinevent.isCancelled())
	    return;

	Jobs.getJobsDAO().joinJob(jPlayer, jPlayer.getJobProgression(job));
	jPlayer.setLeftTime(job);

	PerformCommands.performCommandsOnJoin(jPlayer, job);

	Jobs.takeSlot(job);
	Jobs.getSignUtil().updateAllSign(job);

	job.updateTotalPlayers();
	jPlayer.maxJobsEquation = getMaxJobs(jPlayer);
    }

    /**
     * Causes player to leave the given job.
     * 
     * @param jPlayer {@link JobsPlayer}
     * @param job {@link Job}
     */
    public boolean leaveJob(JobsPlayer jPlayer, Job job) {
	if (!jPlayer.isInJob(job))
	    return false;

	JobsLeaveEvent jobsleaveevent = new JobsLeaveEvent(jPlayer, job);
	Bukkit.getServer().getPluginManager().callEvent(jobsleaveevent);
	// If event is canceled, don't do anything
	if (jobsleaveevent.isCancelled())
	    return false;

	Jobs.getJobsDAO().recordToArchive(jPlayer, job);
	// let the user leave the job
	if (!jPlayer.leaveJob(job))
	    return false;

	if (!Jobs.getJobsDAO().quitJob(jPlayer, job))
	    return false;
	PerformCommands.performCommandsOnLeave(jPlayer, job);
	Jobs.leaveSlot(job);

	jPlayer.getLeftTimes().remove(jPlayer.getUniqueId());

	Jobs.getSignUtil().updateAllSign(job);
	job.updateTotalPlayers();
	return true;
    }

    /**
     * Causes player to leave all their jobs
     * 
     * @param jPlayer {@link JobsPlayer}
     */
    public void leaveAllJobs(JobsPlayer jPlayer) {
	for (JobProgression job : new ArrayList<>(jPlayer.getJobProgression()))
	    leaveJob(jPlayer, job.getJob());

	jPlayer.leaveAllJobs();
    }

    /**
     * Transfers player job to a new one
     * 
     * @param jPlayer {@link JobsPlayer}
     * @param oldjob - the old job
     * @param newjob - the new job
     */
    public boolean transferJob(JobsPlayer jPlayer, Job oldjob, Job newjob) {
	if (!jPlayer.transferJob(oldjob, newjob) || !Jobs.getJobsDAO().quitJob(jPlayer, oldjob))
	    return false;

	oldjob.updateTotalPlayers();
	Jobs.getJobsDAO().joinJob(jPlayer, jPlayer.getJobProgression(newjob));
	newjob.updateTotalPlayers();
	jPlayer.save();
	return true;
    }

    /**
     * Promotes player in their job
     * 
     * @param jPlayer {@link JobsPlayer}
     * @param job - the job
     * @param levels - number of levels to promote
     */
    public void promoteJob(JobsPlayer jPlayer, Job job, int levels) {
	jPlayer.promoteJob(job, levels);
	jPlayer.save();

	Jobs.getSignUtil().updateAllSign(job);
    }

    /**
     * Demote player in their job
     * 
     * @param jPlayer {@link JobsPlayer}
     * @param job - the job
     * @param levels - number of levels to demote
     */
    public void demoteJob(JobsPlayer jPlayer, Job job, int levels) {
	jPlayer.demoteJob(job, levels);
	jPlayer.save();

	Jobs.getSignUtil().updateAllSign(job);
    }

    /**
     * Adds experience to the player
     * 
     * @param jPlayer {@link JobsPlayer}
     * @param job - the job
     * @param experience - experience gained
     */
    public void addExperience(JobsPlayer jPlayer, Job job, double experience) {
	JobProgression prog = jPlayer.getJobProgression(job);
	if (prog == null || experience > Double.MAX_VALUE)
	    return;

	int oldLevel = prog.getLevel();
	if (prog.addExperience(experience)) {
	    performLevelUp(jPlayer, job, oldLevel);
	    Jobs.getSignUtil().updateAllSign(job);
	}

	jPlayer.save();
    }

    /**
     * Removes experience from the player
     * 
     * @param jPlayer {@link JobsPlayer}
     * @param job {@link Job}
     * @param experience - experience gained
     */
    public void removeExperience(JobsPlayer jPlayer, Job job, double experience) {
	JobProgression prog = jPlayer.getJobProgression(job);
	if (prog == null || experience > Double.MAX_VALUE)
	    return;

	prog.addExperience(-experience);

	jPlayer.save();
	Jobs.getSignUtil().updateAllSign(job);
    }

    /**
     * Broadcasts level up about a player
     * 
     * @param jPlayer {@link JobsPlayer}
     * @param job {@link Job}
     * @param oldLevel
     */
    public void performLevelUp(JobsPlayer jPlayer, Job job, int oldLevel) {
	JobProgression prog = jPlayer.getJobProgression(job);
	if (prog == null)
	    return;

	Player player = jPlayer.getPlayer();

	// when the player loses income
	if (prog.getLevel() < oldLevel) {
	    String message = Jobs.getLanguage().getMessage("message.leveldown.message");

	    message = message.replace("%jobname%", job.getNameWithColor());
	    message = message.replace("%playername%", player != null ? plugin.getComplement().getDisplayName(player) : jPlayer.getName());
	    message = message.replace("%joblevel%", "" + prog.getLevel());
	    message = message.replace("%lostLevel%", "" + oldLevel);

	    if (player != null) {
		for (String line : message.split("\n")) {
		    if (Jobs.getGCManager().LevelChangeActionBar)
			ActionBarManager.send(player, line);
		    if (Jobs.getGCManager().LevelChangeChat)
			player.sendMessage(line);
		}
	    }

	    jPlayer.reloadHonorific();
	    Jobs.getPermissionHandler().recalculatePermissions(jPlayer);
	    performCommandOnLevelUp(jPlayer, prog.getJob(), oldLevel);
	    Jobs.getSignUtil().updateAllSign(job);
	    return;
	}

	// LevelUp event
	JobsLevelUpEvent levelUpEvent = new JobsLevelUpEvent(
	    jPlayer,
	    job.getName(),
	    prog.getLevel(),
	    Jobs.getTitleManager().getTitle(oldLevel, prog.getJob().getName()),
	    Jobs.getTitleManager().getTitle(prog.getLevel(), prog.getJob().getName()),
	    Jobs.getGCManager().SoundLevelupSound,
	    Jobs.getGCManager().SoundLevelupVolume,
	    Jobs.getGCManager().SoundLevelupPitch,
	    Jobs.getGCManager().SoundTitleChangeSound,
	    Jobs.getGCManager().SoundTitleChangeVolume,
	    Jobs.getGCManager().SoundTitleChangePitch);
	Bukkit.getServer().getPluginManager().callEvent(levelUpEvent);
	// If event is canceled, dont do anything
	if (levelUpEvent.isCancelled())
	    return;

	// If it fails, we can ignore it
	try {
	    if (Jobs.getGCManager().SoundLevelupUse) {
		Sound sound = levelUpEvent.getSound();
		if (player != null)
		    player.getWorld().playSound(player.getLocation(), sound, levelUpEvent.getSoundVolume(), levelUpEvent.getSoundPitch());
	    }
	} catch (Exception e) {
	}

	if (Jobs.getGCManager().FireworkLevelupUse) {
	    Bukkit.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
		@Override
		public void run() {
		    if (player == null || !player.isOnline())
			return;

		    Firework f = player.getWorld().spawn(player.getLocation(), Firework.class);
		    FireworkMeta fm = f.getFireworkMeta();

		    if (Jobs.getGCManager().UseRandom) {
			ThreadLocalRandom r = ThreadLocalRandom.current();
			int rt = r.nextInt(4) + 1;
			Type type = Type.BALL;

			switch (rt) {
			case 2:
			    type = Type.BALL_LARGE;
			    break;
			case 3:
			    type = Type.BURST;
			    break;
			case 4:
			    type = Type.CREEPER;
			    break;
			case 5:
			    type = Type.STAR;
			    break;
			default:
			    break;
			}

			int r1i = r.nextInt(17) + 1;
			int r2i = r.nextInt(17) + 1;

			Color c1 = Util.getColor(r1i);
			Color c2 = Util.getColor(r2i);

			FireworkEffect effect = FireworkEffect.builder().flicker(r.nextBoolean()).withColor(c1)
			    .withFade(c2).with(type).trail(r.nextBoolean()).build();
			fm.addEffect(effect);

			int rp = r.nextInt(2) + 1;
			fm.setPower(rp);
		    } else {
			Pattern comma = Pattern.compile(",", 16);
			List<String> colorStrings = Jobs.getGCManager().FwColors;
			Color[] colors = new Color[colorStrings.size()];

			for (int s = 0; s < colorStrings.size(); s++) {
			    String colorString = colorStrings.get(s);
			    String[] sSplit = comma.split(colorString);
			    if (sSplit.length < 3) {
				continue;
			    }

			    int[] colorRGB = new int[3];
			    for (int i = 0; i < 3; i++) {
				try {
				    colorRGB[i] = Integer.parseInt(sSplit[i]);
				} catch (NumberFormatException e) {
				}
			    }

			    int r = colorRGB[0], g = colorRGB[1], b = colorRGB[2];
			    if (r > 255 || r < 0) {
				r = 1;
			    }

			    if (g > 255 || g < 0) {
				g = 5;
			    }

			    if (b > 255 || b < 0) {
				b = 3;
			    }

			    colors[s] = Color.fromRGB(r, g, b);
			}

			fm.addEffect(FireworkEffect.builder()
			    .flicker(Jobs.getGCManager().UseFlicker)
			    .trail(Jobs.getGCManager().UseTrail)
			    .with(Type.valueOf(Jobs.getGCManager().FireworkType))
			    .withColor(colors)
			    .withFade(colors)
			    .build());

			fm.setPower(Jobs.getGCManager().FireworkPower);
		    }

		    f.setFireworkMeta(fm);
		}
	    }, Jobs.getGCManager().ShootTime);
	}

	String message = Jobs.getLanguage().getMessage("message.levelup." + (Jobs.getGCManager().isBroadcastingLevelups()
	    ? "broadcast" : "nobroadcast"));

	message = message.replace("%jobname%", job.getNameWithColor());

	if (levelUpEvent.getOldTitle() != null)
	    message = message.replace("%titlename%", levelUpEvent.getOldTitle()
		.getChatColor().toString() + levelUpEvent.getOldTitle().getName());

	message = message.replace("%playername%", player != null ? plugin.getComplement().getDisplayName(player) : jPlayer.getName());
	message = message.replace("%joblevel%", "" + prog.getLevel());

	for (String line : message.split("\n")) {
	    if (Jobs.getGCManager().isBroadcastingLevelups()) {
		if (Jobs.getGCManager().BroadcastingLevelUpLevels.contains(oldLevel + 1) || Jobs.getGCManager().BroadcastingLevelUpLevels.contains(0))
		    plugin.getComplement().broadcastMessage(line);
	    } else if (player != null) {
		if (Jobs.getGCManager().LevelChangeActionBar)
		    ActionBarManager.send(player, line);
		if (Jobs.getGCManager().LevelChangeChat)
		    player.sendMessage(line);
	    }
	}

	if (levelUpEvent.getNewTitle() != null && !levelUpEvent.getNewTitle().equals(levelUpEvent.getOldTitle())) {

	    // If it fails, we can ignore it
	    try {
		if (Jobs.getGCManager().SoundTitleChangeUse) {
		    Sound sound = levelUpEvent.getTitleChangeSound();
		    if (player != null)
			player.getWorld().playSound(player.getLocation(), sound, levelUpEvent.getTitleChangeVolume(),
			    levelUpEvent.getTitleChangePitch());
		}
	    } catch (Exception e) {
	    }
	    // user would skill up
	    message = Jobs.getLanguage().getMessage("message.skillup." + (Jobs.getGCManager().isBroadcastingSkillups()
		? "broadcast" : "nobroadcast"));

	    message = message.replace("%playername%", player != null ? plugin.getComplement().getDisplayName(player) : jPlayer.getName());
	    message = message.replace("%titlename%", levelUpEvent.getNewTitle()
		.getChatColor().toString() + levelUpEvent.getNewTitle().getName());
	    message = message.replace("%jobname%", job.getNameWithColor());

	    for (String line : message.split("\n")) {
		if (Jobs.getGCManager().isBroadcastingSkillups()) {
		    plugin.getComplement().broadcastMessage(line);
		} else if (player != null) {
		    if (Jobs.getGCManager().TitleChangeActionBar)
			ActionBarManager.send(player, line);
		    if (Jobs.getGCManager().TitleChangeChat)
			player.sendMessage(line);
		}
	    }
	}

	jPlayer.reloadHonorific();
	Jobs.getPermissionHandler().recalculatePermissions(jPlayer);
	performCommandOnLevelUp(jPlayer, prog.getJob(), oldLevel);
	Jobs.getSignUtil().updateAllSign(job);

	if (player != null && prog.getLevel() == jPlayer.getMaxJobLevelAllowed(prog.getJob())) {
	    for (String cmd : job.getMaxLevelCommands()) {
		if (cmd.isEmpty()) {
		    continue;
		}

		if (cmd.contains(":")) {
		    String[] split = cmd.split(":", 2);

		    String command = "";
		    if (split.length > 1) {
			command = split[1];
			command = command.replace("[playerName]", player.getName());
			command = command.replace("[job]", job.getName());
		    }

		    if (split[0].equalsIgnoreCase("player:")) {
			player.performCommand(command);
		    } else {
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
		    }
		} else {
		    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
		}
	    }
	}
    }

    /**
     * Performs command on level up
     * 
     * @param jPlayer {@link JobsPlayer}
     * @param job {@link Job}
     * @param oldLevel
     */
    public void performCommandOnLevelUp(JobsPlayer jPlayer, Job job, int oldLevel) {
	JobProgression prog = jPlayer.getJobProgression(job);
	if (prog == null)
	    return;

	int newLevel = oldLevel + 1;
	Player player = Bukkit.getPlayer(jPlayer.getUniqueId());

	for (JobCommands command : job.getCommands()) {
	    if ((command.getLevelFrom() == 0 && command.getLevelUntil() == 0) || newLevel >= command.getLevelFrom()
		&& newLevel <= command.getLevelUntil()) {
		for (String commandString : new ArrayList<>(command.getCommands())) {
		    commandString = commandString.replace("[player]", player.getName());
		    commandString = commandString.replace("[oldlevel]", Integer.toString(oldLevel));
		    commandString = commandString.replace("[newlevel]", Integer.toString(newLevel));
		    commandString = commandString.replace("[jobname]", job.getName());
		    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandString);
		}
	    }
	}
    }

    /**
     * Checks whenever the given jobs player is under the max allowed jobs.
     * 
     * @param player {@link JobsPlayer}
     * @param currentCount the current jobs size
     * @return true if the player is under the given jobs size
     */
    public boolean getJobsLimit(JobsPlayer jPlayer, short currentCount) {
	return getMaxJobs(jPlayer) > currentCount;
    }

    /**
     * Gets the maximum jobs from player.
     * 
     * @param jPlayer {@link JobsPlayer}
     * @return the maximum allowed jobs
     */
    public int getMaxJobs(JobsPlayer jPlayer) {
	if (jPlayer == null) {
	    return Jobs.getGCManager().getMaxJobs();
	}

	int max = Jobs.getPermissionManager().getMaxPermission(jPlayer, "jobs.max", false).intValue();
	return max == 0 ? Jobs.getGCManager().getMaxJobs() : max;
    }

    public BoostMultiplier getBoost(JobsPlayer player, Job job) {
	return getBoost(player, job, false);
    }

    public BoostMultiplier getBoost(JobsPlayer player, Job job, boolean force) {
	BoostMultiplier b = new BoostMultiplier();
	for (CurrencyType one : CurrencyType.values()) {
	    b.add(one, getBoost(player, job, one, force));
	}
	return b;
    }

    public double getBoost(JobsPlayer player, Job job, CurrencyType type) {
	return getBoost(player, job, type, false);
    }

    public double getBoost(JobsPlayer player, Job job, CurrencyType type, boolean force) {
	return player.getBoost(job.getName(), type, force);
    }

    /**
     * Perform reload for all jobs players.
     */
    public void reload() {
	for (JobsPlayer jPlayer : players.values()) {
	    for (JobProgression progression : jPlayer.getJobProgression()) {
		Job job = Jobs.getJob(progression.getJob().getName());
		if (job != null)
		    progression.setJob(job);
	    }
	    if (jPlayer.isOnline()) {
		jPlayer.reloadHonorific();
		jPlayer.reloadLimits();
		Jobs.getPermissionHandler().recalculatePermissions(jPlayer);
	    }
	}
    }

    private final Map<UUID, Map<Job, ItemBonusCache>> cache = new HashMap<>();

    public void resetItemBonusCache(UUID uuid) {
	cache.remove(uuid);
    }

    public BoostMultiplier getItemBoostNBT(Player player, Job prog) {
	Map<Job, ItemBonusCache> cj = cache.get(player.getUniqueId());
	if (cj == null) {
	    cj = new HashMap<>();
	    cache.put(player.getUniqueId(), cj);
	}

	ItemBonusCache c = cj.get(prog);
	if (c == null) {
	    c = new ItemBonusCache(getInventoryBoost(player, prog));
	    cj.put(prog, c);
	}

	return c.getBoostMultiplier();
    }

    public BoostMultiplier getInventoryBoost(Player player, Job prog) {
	BoostMultiplier data = new BoostMultiplier();
	if (player == null || prog == null)
	    return data;

	ItemStack iih;
	List<JobItems> jitems = new ArrayList<>();

	// Check mainhand slot
	if (Jobs.getGCManager().boostedItemsInMainHand && (iih = Jobs.getNms().getItemInMainHand(player)) != null) {
	    jitems.add(getJobsItemByNbt(iih));
	}

	// Check offhand slot
	if (Version.isCurrentEqualOrHigher(Version.v1_9_R1) && Jobs.getGCManager().boostedItemsInOffHand
		&& (iih = CMIReflections.getItemInOffHand(player)) != null) {
	    jitems.add(getJobsItemByNbt(iih));
	}

	// Check armor slots
	if (Jobs.getGCManager().boostedArmorItems) {
	    for (ItemStack oneArmor : player.getInventory().getArmorContents()) {
		if (oneArmor != null && oneArmor.getType() != org.bukkit.Material.AIR) {
		    jitems.add(getJobsItemByNbt(oneArmor));
		}
	    }
	}

	for (JobItems jitem : jitems) {
	    if (jitem != null && jitem.getJobs().contains(prog)) {
		data.add(jitem.getBoost(getJobsPlayer(player).getJobProgression(prog)));
	    }
	}

	return data;
}

    private final String jobsItemBoost = "JobsItemBoost";

    public boolean containsItemBoostByNBT(ItemStack item) {
	return item != null && Jobs.getReflections().hasNbtString(item, jobsItemBoost);
    }

    public JobItems getJobsItemByNbt(ItemStack item) {
	if (item == null)
	    return null;

	Object itemName = CMIReflections.getNbt(item, jobsItemBoost);

	if (itemName == null || itemName.toString().isEmpty()) {
	    // Checking old boost items and converting to new format if needed
	    if (Jobs.getReflections().hasNbt(item, jobsItemBoost)) {
		for (Job one : Jobs.getJobs()) {
		    itemName = Jobs.getReflections().getNbt(item, jobsItemBoost, one.getName());
		    if (itemName != null) {
			JobItems b = ItemBoostManager.getItemByKey(itemName.toString());
			if (b != null) {
			    ItemStack ic = CMIReflections.setNbt(item, jobsItemBoost, b.getNode());
			    item.setItemMeta(ic.getItemMeta());
			}
			break;
		    }
		}
	    }
	    if (itemName == null)
		return null;
	}

	return ItemBoostManager.getItemByKey(itemName.toString());
    }

//    public BoostMultiplier getJobsBoostByNbt(ItemStack item) {
//	JobItems b = getJobsItemByNbt(item);
//	if (b == null)
//	    return null;
//	return b.getBoost();
//    }

    public enum BoostOf {
	McMMO, PetPay, NearSpawner, Permission, Global, Dynamic, Item, Area
    }

    public Boost getFinalBonus(JobsPlayer player, Job prog, boolean force, boolean getall) {
	return getFinalBonus(player, prog, null, null, force, getall);
    }

    public Boost getFinalBonus(JobsPlayer player, Job prog, boolean force) {
	return getFinalBonus(player, prog, null, null, force, false);
    }

    public Boost getFinalBonus(JobsPlayer player, Job prog) {
	return getFinalBonus(player, prog, null, null, false, false);
    }

    public Boost getFinalBonus(JobsPlayer player, Job prog, Entity ent, LivingEntity victim) {
	return getFinalBonus(player, prog, ent, victim, false, false);
    }

    public Boost getFinalBonus(JobsPlayer player, Job prog, Entity ent, LivingEntity victim, boolean force, boolean getall) {
	Boost boost = new Boost();

	if (player == null || !player.isOnline() || prog == null)
	    return boost;

	Player pl = player.getPlayer();

	if (HookManager.getMcMMOManager().mcMMOPresent || HookManager.getMcMMOManager().mcMMOOverHaul)
	    boost.add(BoostOf.McMMO, new BoostMultiplier().add(HookManager.getMcMMOManager().getMultiplier(pl)));

	double petPay = 0D;

	if (ent instanceof Tameable) {
	    Tameable t = (Tameable) ent;
	    if (t.isTamed() && t.getOwner() instanceof Player) {
		petPay = Jobs.getPermissionManager().getMaxPermission(player, "jobs.petpay", false, false);
		if (petPay != 0D)
		    boost.add(BoostOf.PetPay, new BoostMultiplier().add(petPay));
	    }
	}

	if (ent != null && HookManager.getMyPetManager() != null && HookManager.getMyPetManager().isMyPet(ent, pl)) {
	    if (petPay == 0D)
		petPay = Jobs.getPermissionManager().getMaxPermission(player, "jobs.petpay", false, false);
	    if (petPay != 0D)
		boost.add(BoostOf.PetPay, new BoostMultiplier().add(petPay));
	}

	if (victim != null && victim.hasMetadata(mobSpawnerMetadata)) {
	    Double amount = Jobs.getPermissionManager().getMaxPermission(player, "jobs.nearspawner", false, false);
	    if (amount != 0D)
		boost.add(BoostOf.NearSpawner, new BoostMultiplier().add(amount));
	}

	if (getall) {
	    if (petPay == 0D)
		petPay = Jobs.getPermissionManager().getMaxPermission(player, "jobs.petpay", force, false);
	    else
		boost.add(BoostOf.PetPay, new BoostMultiplier().add(petPay));

	    Double amount = Jobs.getPermissionManager().getMaxPermission(player, "jobs.nearspawner", force);
	    if (amount != 0D)
		boost.add(BoostOf.NearSpawner, new BoostMultiplier().add(amount));
	}

	boost.add(BoostOf.Permission, getBoost(player, prog, force));
	boost.add(BoostOf.Global, prog.getBoost());

	if (Jobs.getGCManager().useDynamicPayment)
	    boost.add(BoostOf.Dynamic, new BoostMultiplier().add(prog.getBonus()));

	if (pl != null) {
	    boost.add(BoostOf.Item, getItemBoostNBT(pl, prog));
	}

	if (!Jobs.getRestrictedAreaManager().getRestrictedAres().isEmpty())
	    boost.add(BoostOf.Area, new BoostMultiplier().add(Jobs.getRestrictedAreaManager().getRestrictedMultiplier(pl)));

	return boost;
    }

    public void autoJoinJobs(final Player player) {
	if (!Jobs.getGCManager().AutoJobJoinUse || player == null || player.isOp())
	    return;

	Bukkit.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
	    @Override
	    public void run() {
		if (!player.isOnline())
		    return;

		JobsPlayer jPlayer = getJobsPlayer(player);
		if (jPlayer == null || player.hasPermission("jobs.*"))
		    return;

		int confMaxJobs = Jobs.getGCManager().getMaxJobs();
		short playerMaxJobs = (short) jPlayer.getJobProgression().size();

		if (confMaxJobs > 0 && playerMaxJobs >= confMaxJobs && !getJobsLimit(jPlayer, playerMaxJobs))
		    return;

		for (Job one : Jobs.getJobs()) {
		    if (one.getMaxSlots() != null && Jobs.getUsedSlots(one) >= one.getMaxSlots())
			continue;

		    if (!jPlayer.isInJob(one) && player.hasPermission("jobs.autojoin." + one.getName().toLowerCase()))
			joinJob(jPlayer, one);
		}
	    }
	}, Jobs.getGCManager().AutoJobJoinDelay * 20L);
    }
}
