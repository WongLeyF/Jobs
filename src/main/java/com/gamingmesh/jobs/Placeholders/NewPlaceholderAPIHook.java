package com.gamingmesh.jobs.Placeholders;

import org.bukkit.OfflinePlayer;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.Placeholders.Placeholder.JobsPlaceHolders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class NewPlaceholderAPIHook extends PlaceholderExpansion {

    private Jobs plugin;

    public NewPlaceholderAPIHook(Jobs plugin) {
	this.plugin = plugin;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
	JobsPlaceHolders placeHolder = JobsPlaceHolders.getByName(identifier);
	if (placeHolder == null)
	    return null;

	return plugin.getPlaceholderAPIManager().getValue(player, placeHolder, "%" + Placeholder.pref + "_" + identifier + "%");
    }

    @Override
    public String getAuthor() {
	return null;
    }

    @Override
    public String getIdentifier() {
	return Placeholder.pref;
    }

    @Override
    public String getVersion() {
	return plugin.getDescription().getVersion();
    }
}
