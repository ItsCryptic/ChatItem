package me.dadus33.chatitem.invsee;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.dadus33.chatitem.ChatItem;

public abstract class InvShower {

	private static final HashMap<String, InvShower> INV_SHOWER = new HashMap<>();
	public static InvShower get(String s) {
		return INV_SHOWER.get(s);
	}
	public static void add(String s, InvShower shower) {
		INV_SHOWER.put(s, shower);
	}
	public static HashMap<String, InvShower> getInvShower() {
		return INV_SHOWER;
	}
	
	static {
		Bukkit.getScheduler().runTaskTimer(ChatItem.getInstance(), () -> {
			for(Entry<String, InvShower> entries : new HashMap<>(INV_SHOWER).entrySet())
				if(entries.getValue().hasExpired())
					INV_SHOWER.remove(entries.getKey());
		}, 200, 200);
	}
	
	protected final long time;
	protected final UUID uuid;
	
	public InvShower(Player cible) {
		this.time = System.currentTimeMillis() + 3600 * 1000;
		this.uuid = cible.getUniqueId();
	}
	
	public UUID getUuid() {
		return uuid;
	}
	
	public long getTimeExpire() {
		return time;
	}
	
	public boolean hasExpired() {
		return System.currentTimeMillis() > time || Bukkit.getPlayer(uuid) == null || !Bukkit.getPlayer(uuid).isOnline();
	}
	
	public abstract void open(Player p);
	
}
