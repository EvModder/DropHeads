package net.evmodder.DropHeads.events;

import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;

public class HeadRollEvent extends EntityEvent{
	private static final HandlerList HANDLERS = new HandlerList();

	private final Entity killer;
	private final Entity victim;
	private final double dropChance, dropRoll;
	private boolean dropSuccess;

	/**
	 * Create a HeadRollEvent for DropHeads.
	 * @param killer the Entity doing the beheading
	 * @param victim the Entity being beheaded
	 * @param dropChance the configured droprate for the victim's head, between 0 and 1 inclusive
	 * @param dropRoll the PRNG double drop roll value, between 0 and 1 inclusive
	 * @param dropSuccess whether the drop roll was determined successful.
	 */
	public HeadRollEvent(final Entity killer, final Entity victim, final double dropChance, final double dropRoll, final boolean dropSuccess){
		super(victim);
		this.killer = killer;
		this.victim = victim;
		this.dropChance = dropChance;
		this.dropRoll = dropRoll;
		this.dropSuccess = dropSuccess;
	}

	/**
	 * Get the entity that may have done the beheading.
	 * @return the Entity object representing the killer, or null if no entity was responsible
	 */
	public Entity getKiller(){return killer;}

	/**
	 * Get the entity that may have been beheaded.
	 * @return the Entity object representing the victim
	 */
	public Entity getVictim(){return victim;}

	/**
	 * Get the entity that may have been beheaded.
	 * @return the Entity object representing the victim
	 */
	@Override public Entity getEntity(){return entity;}

	/**
	 * Get the configured droprate for the victim's head, between 0 and 1 inclusive.
	 * @return the droprate
	 */
	public double getDropChance(){return dropChance;}

	/**
	 * Get the PRNG value of the drop roll, uniform between 0 and 1 inclusive.
	 * When this value is lower than the droprate, the roll is considered successful.
	 * @return the drop roll value in the range [0, 1]
	 */
	public double getDropRoll(){return dropRoll;}

	/**
	 * Get whether the effective drop roll was determined to be a success.
	 * @return the success of the drop roll
	 */
	public boolean getDropSuccess(){return dropSuccess;}

	/**
	 * Set whether the drop roll should be considered a success.
	 * @param value whether the head drop should succeed or fail
	 */
	public void setDropSuccess(final boolean success){dropSuccess = success;}

	/**
	 * Get the list of handlers for this event.
	 * @return the list of handlers for this event
	 */
	@Override public HandlerList getHandlers(){return HANDLERS;}
}
