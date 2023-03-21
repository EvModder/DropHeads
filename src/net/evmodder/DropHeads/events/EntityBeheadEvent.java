package net.evmodder.DropHeads.events;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Cancellable event created by the DropHeads plugin when an entity is beheaded.
 */
public class EntityBeheadEvent extends EntityEvent implements Cancellable{
	private static final HandlerList HANDLERS = new HandlerList();
	/** Static version of getHandlers().
	 * @return the list of handlers for this event */
	public static HandlerList getHandlerList(){return HANDLERS;} // This is actually required for some dumb reason.

	private final Entity victim;
	private final Entity killer;
	private final Event sourceEvent;
	private ItemStack headDrop;
	private boolean cancelled = false;
	//TODO: private final boolean isVanilla;
	//TODO: private final boolean charged creeper kill

	/**
	 * Create an EntityBeheadEvent for DropHeads.
	 * @param victim the Entity being beheaded
	 * @param killer the Entity doing the beheading (can be <code>null</code>)
	 * @param sourceEvent the Event which triggered this EntityBeheadEvent
	 * @param headDrop the ItemStack used to represent the victim's head
	 */
	public EntityBeheadEvent(final Entity victim, final Entity killer, final Event sourceEvent, final ItemStack headDrop){
		super(victim);
		this.victim = victim;
		this.killer = killer;
		this.sourceEvent = sourceEvent;
		this.headDrop = headDrop;
	}

	/**
	 * Get the entity that was beheaded.
	 * @return the Entity object representing the victim
	 */
	public Entity getVictim(){return victim;}

	/**
	 * Get the entity that did the beheading.
	 * It is possible that this differs from getSourceEvent().getKiller() because
	 * it can identify non-player killers, projectile sources, etc., if configured.
	 * Use getSourceEvent().getKiller() if you want the killer as determined by Minecraft.
	 * @return the Entity object representing the killer, or null if no entity was responsible
	 */
	public Entity getKiller(){return killer;}

	/**
	 * Get the entity that was beheaded.
	 * Same as getVictim().
	 * @return the Entity object representing the victim
	 */
	@Override public Entity getEntity(){return entity;}

	/**
	 * Get the Event which triggered this EntityBeheadEvent (usually an EntityDeathEvent).
	 * @return a reference to the source event
	 */
	public Event getSourceEvent(){return sourceEvent;}

	/**
	 * Get the ItemStack which will result from the beheading.
	 * @return mutable ItemStack that will be used as the resulting head item if this event is left uncancelled
	 */
	public ItemStack getHeadItem(){return headDrop;}

	/**
	 * Set the ItemStack which will result from the beheading.
	 * @param headDrop The ItemStack to use as the victim's head
	 */
	public void setHeadItem(final ItemStack headDrop){this.headDrop = headDrop;}

	/**
	 * Get whether this event has been cancelled.
	 * @return Whether the event has been cancelled
	 */
	@Override public boolean isCancelled(){return cancelled;}

	/**
	 * Set whether this event should be cancelled.
	 * @param cancel whether the event should be cancelled
	 */
	@Override public void setCancelled(boolean cancel){cancelled = cancel;}

	/**
	 * Get the list of handlers for this event.
	 * @return the list of handlers for this event
	 */
	@Override public HandlerList getHandlers(){return HANDLERS;}
}