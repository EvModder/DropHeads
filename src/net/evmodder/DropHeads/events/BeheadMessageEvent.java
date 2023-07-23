package net.evmodder.DropHeads.events;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import net.evmodder.EvLib.extras.TellrawUtils.Component;

/** Cancellable event created by the DropHeads plugin when a behead message is sent.
 */
public class BeheadMessageEvent extends Event implements Cancellable{
	private static final HandlerList HANDLERS = new HandlerList();
	/** Static version of getHandlers().
	 * @return the list of handlers for this event */
	public static HandlerList getHandlerList(){return HANDLERS;} // This is actually required for some dumb reason.

	private final Player recipient;
	private final Entity victim;
	private final Entity killer;
	private Component beheadMessage;
	private boolean cancelled = false;
	private boolean isGlobal;
	private boolean isPetDeath;

	/** Create an BeheadMessageEvent for DropHeads.
	 * @param recipient the Player being sent the behead message
	 * @param victim the Entity which was beheaded
	 * @param killer the Entity which caused the death of <code>victim</code>, or <code>null</code> if no entity was responsible
	 * @param beheadMessage the message being sent
	 * @param isGlobal whether the behead message is globally announced
	 * @param isPetDeath whether the behead message is for a pet
	 */
	public BeheadMessageEvent(final Player recipient, final Entity victim, final Entity killer,
			final Component beheadMessage, final boolean isGlobal, final boolean isPetDeath){
		this.recipient = recipient;
		this.victim = victim;
		this.killer = killer;
		this.beheadMessage = beheadMessage;
		this.isGlobal = isGlobal;
		this.isPetDeath = isPetDeath;
	}

	/** Get the player who is being sent the behead message.
	 * For global behead messages, this can be any online player.
	 * @return the Player recipient
	 */
	public Player getRecipient(){return recipient;}

	/** Get the entity that was beheaded.
	 * @return the Entity object representing the victim
	 */
	public Entity getVictim(){return victim;}
	
	/** Get the entity that did the beheading.
	 * @return the Entity for the killer, or <code>null</code> if no entity was responsible
	 */
	public Entity getKiller(){return killer;}

	/** Get whether this message is sent to all online players.
	 * @return whether the behead message is globally announced
	 */
	public boolean isGlobal(){return isGlobal;}

	/** Get whether this is a pet death message sent to their owner.
	 * @return whether the behead message is for a pet
	 */
	public boolean isPetDeath(){return isPetDeath;}

	/** Get the behead message.
	 * @return a Component message
	 */
	public Component getMessage(){return beheadMessage;}

	/** Set the behead message.
	 * @param message the new behead Component message to use
	 */
	public void setMessage(final Component message){beheadMessage = message;}

	/** Get whether this event has been cancelled.
	 * @return whether the event has been cancelled
	 */
	@Override public boolean isCancelled(){return cancelled;}

	/** Set whether this event should be cancelled.
	 * @param cancel whether the event should be cancelled
	 */
	@Override public void setCancelled(boolean cancel){cancelled = cancel;}

	/** Get the list of handlers for this event.
	 * @return the list of handlers for this event
	 */
	@Override public HandlerList getHandlers(){return HANDLERS;}
}