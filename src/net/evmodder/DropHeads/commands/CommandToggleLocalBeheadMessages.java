package net.evmodder.DropHeads.commands;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import com.google.common.collect.ImmutableList;
import net.evmodder.DropHeads.DropHeads;
import net.evmodder.DropHeads.JunkUtils;
import net.evmodder.EvLib.EvCommand;
import net.evmodder.EvLib.extras.TellrawUtils.TranslationComponent;

public class CommandToggleLocalBeheadMessages extends EvCommand{
	final private DropHeads pl;

	final String CURRENT_STATUS_STR;
	final TranslationComponent ENABLED_COMP, DISABLED_COMP;
	final String CMD_MUST_BE_RUN_BY_A_PLAYER;

	public CommandToggleLocalBeheadMessages(DropHeads plugin) {
		super(plugin);
		pl = plugin;
		CURRENT_STATUS_STR = pl.getAPI().loadTranslationStr("commands.togglebeheadmessages.current-status", "&7Receive local behead messages: &6%s");
		//TODO: Use comps for ALL msgs
		ENABLED_COMP = pl.getAPI().loadTranslationComp("commands.togglebeheadmessages.enabled", "gui.yes");
		DISABLED_COMP = pl.getAPI().loadTranslationComp("commands.togglebeheadmessages.disabled", "gui.no");

		CMD_MUST_BE_RUN_BY_A_PLAYER = pl.getAPI().loadTranslationStr("commands.togglebeheadmessages.errors.run-by-player",
				"&cOnly ingame players can run this command");
	}

	// TODO: optional arg ON/OFF (or local language equivalent?)
	// TODO: optiaonl arg for GLOBAL vs LOCAL vs DIRECT, or for specific ENTITY|SUBTYPE key?
	@Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args){return ImmutableList.of();}

	private void sendTellraw(String target, String message){
		pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), "minecraft:tellraw "+target+" "+message);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		if(sender instanceof Player == false){
			sender.sendMessage(CMD_MUST_BE_RUN_BY_A_PLAYER);
			return true;
		}
		Player player = (Player)sender;
		if(JunkUtils.hasLocalBeheadMessagedEnabled(player)){
			player.setMetadata(JunkUtils.DISABLE_LOCAL_BEHEAD_MESSAGES_KEY, new FixedMetadataValue(pl, true));
			player.addScoreboardTag(JunkUtils.DISABLE_LOCAL_BEHEAD_MESSAGES_KEY);
			sendTellraw(player.getName(), new TranslationComponent(CURRENT_STATUS_STR, DISABLED_COMP).toString());
		}
		else{
			player.removeMetadata(JunkUtils.DISABLE_LOCAL_BEHEAD_MESSAGES_KEY, pl);
			player.removeScoreboardTag(JunkUtils.DISABLE_LOCAL_BEHEAD_MESSAGES_KEY);
			sendTellraw(player.getName(), new TranslationComponent(CURRENT_STATUS_STR, ENABLED_COMP).toString());
		}
		return true;
	}
}