package me.dadus33.chatitem.hook;

import java.lang.reflect.Method;
import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.dadus33.chatitem.ChatItem;
import me.dadus33.chatitem.namer.NamerManager;
import me.dadus33.chatitem.utils.PacketUtils;
import me.dadus33.chatitem.utils.Storage;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.HoverEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;

public class ChatListener implements Listener {

    public final static char SEPARATOR = ((char)0x0007);
	private final static String NAME = "{name}";
	private final static String AMOUNT = "{amount}";
	private final static String TIMES = "{times}";
    private final static String LEFT = "{remaining}";
    private final HashMap<String, Long> COOLDOWNS = new HashMap<>();
	private Storage c;
	private Method saveMethod;
	
	public ChatListener(Storage c) {
		this.c = c;
		
		try {
			Class<?> nbtTag = PacketUtils.getNmsClass("NBTTagCompound", "nbt.");
			Class<?> itemClass = PacketUtils.getNmsClass("ItemStack", "world.item.");
    		for(Method m : itemClass.getDeclaredMethods()) {
    			if(m.getParameterTypes().length == 1) {
    				if(m.getParameterTypes()[0].equals(nbtTag) && m.getReturnType().equals(nbtTag)) {
    					saveMethod = m;
    				}
    			}
    		}
		} catch (Exception e) {
			
		}
		if(saveMethod == null)
			ChatItem.getInstance().getLogger().info("Failed to find save method. Using default system...");
		else
			ChatItem.getInstance().getLogger().info("Save method founded: " + saveMethod.getName() + ".");
	}
	
	public void setStorage(Storage c) {
		this.c = c;
	}

    private String calculateTime(long seconds){
        if(seconds < 60){
            return seconds+c.SECONDS;
        }
        if(seconds < 3600){
            StringBuilder builder = new StringBuilder();
            int minutes = (int) seconds / 60;
            builder.append(minutes).append(c.MINUTES);
            int secs = (int) seconds - minutes*60;
            if(secs != 0){
                builder.append(" ").append(secs).append(c.SECONDS);
            }
            return builder.toString();
        }
        StringBuilder builder = new StringBuilder();
        int hours = (int) seconds / 3600;
        builder.append(hours).append(c.HOURS);
        int minutes = (int) (seconds/60) - (hours*60);
        if(minutes != 0){
            builder.append(" ").append(minutes).append(c.MINUTES);
        }
        int secs = (int) (seconds - ((seconds/60)*60));
        if(secs != 0){
            builder.append(" ").append(secs).append(c.SECONDS);
        }
        return builder.toString();
    }
	
	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.MONITOR)
	public void onChat(AsyncPlayerChatEvent e) {
		if(e.isCancelled())
			return;
        boolean found = false;

        for (String rep : c.PLACEHOLDERS)
            if (e.getMessage().contains(rep)) {
                found = true;
                break;
            }

        if (!found) {
            return;
        }
		Player p = e.getPlayer();
        if (!p.hasPermission("chatitem.use")) {
            if(!c.LET_MESSAGE_THROUGH) {
                e.setCancelled(true);
            }
            if(!c.NO_PERMISSION_MESSAGE.isEmpty() && c.SHOW_NO_PERM_NORMAL){
                p.sendMessage(c.NO_PERMISSION_MESSAGE);
            }
            return;
        }
        if (p.getItemInHand().getType().equals(Material.AIR)) {
            if (c.DENY_IF_NO_ITEM) {
                e.setCancelled(true);
                if (!c.DENY_MESSAGE.isEmpty())
                    e.getPlayer().sendMessage(c.DENY_MESSAGE);
                return;
            }
            if(c.HAND_DISABLED) {
                return;
            }
        }
        if(c.COOLDOWN > 0 && !p.hasPermission("chatitem.ignore-cooldown")){
            if(COOLDOWNS.containsKey(p.getName())){
                long start = COOLDOWNS.get(p.getName());
                long current = System.currentTimeMillis()/1000;
                long elapsed = current - start;
                if(elapsed >= c.COOLDOWN){
                    COOLDOWNS.remove(p.getName());
                } else {
                    if(!c.LET_MESSAGE_THROUGH) {
                        e.setCancelled(true);
                    }
                    if(!c.COOLDOWN_MESSAGE.isEmpty()){
                        long left = (start + c.COOLDOWN) - current;
                        p.sendMessage(c.COOLDOWN_MESSAGE.replace(LEFT, calculateTime(left)));
                    }
                    return;
                }
            }
        }
		e.setCancelled(true);
		String format = e.getFormat(), defMsg = e.getMessage();
		boolean isAlreadyParsed = false;
		if(format.contains("%1$s") && format.contains("%2$s")) // message not parsed but not default way
			isAlreadyParsed = false;
		if(format.equalsIgnoreCase(defMsg)) // is message already parsed
			isAlreadyParsed = true;
		if(format.equalsIgnoreCase("<%1$s> %2$s")) // default MC message
			isAlreadyParsed = false;
		String msg = isAlreadyParsed ? format : String.format(format, p.getDisplayName(), defMsg);
		ItemStack item = p.getItemInHand();
		ItemMeta meta = item == null ? null : item.getItemMeta();
		e.getRecipients().forEach((pl) -> {
			TextComponent component = new TextComponent("");
			ChatColor color = ChatColor.getByChar(getColorChat(e.getFormat()));
			for (String args : msg.split(" ")) {
				if (c.PLACEHOLDERS.contains(args)) {
					if(meta != null) {
						TextComponent itemComponent = new TextComponent(ChatListener.styleItem(pl, item, c));
						String itemJson = convertItemStackToJson(item);
						itemComponent.setHoverEvent(
								new HoverEvent(Action.SHOW_ITEM, new BaseComponent[] { new TextComponent(itemJson) }));
						component.addExtra(itemComponent);
					} else {
						if(c.HAND_DISABLED)
							component.addExtra(color + args);
						else
							component.addExtra(c.HAND_NAME.replace("{name}", p.getName()).replace("{display-name}", p.getDisplayName()));
					}
				} else {
					component.addExtra(color + args);
				}
				component.addExtra(" ");
				char maybeNextCode = getColorChat(args);
				if(maybeNextCode != 'r') {
					color = ChatColor.getByChar(maybeNextCode);
				}
			}
			pl.spigot().sendMessage(component);
		});
		//Utils.getOnlinePlayers().forEach((pl) -> pl.spigot().sendMessage(component));
	}

	/**
	 * Converts an {@link org.bukkit.inventory.ItemStack} to a Json string for
	 * sending with {@link net.md_5.bungee.api.chat.BaseComponent}'s.
	 *
	 * @param itemStack
	 *            the item to convert
	 * @return the Json string representation of the item
	 */
	public String convertItemStackToJson(ItemStack itemStack) {
		try {
			Class<?> nbtTag = PacketUtils.getNmsClass("NBTTagCompound", "nbt.");
			Class<?> craftItemClass = PacketUtils.getObcClass("inventory.CraftItemStack");
			Object nmsNbtTagCompoundObj = nbtTag.newInstance();
			if(saveMethod == null) {
				Object nmsItemStackObj = craftItemClass.getMethod("asNMSCopy", ItemStack.class).invoke(null, itemStack);
				return nmsItemStackObj.getClass().getMethod("save", nbtTag).invoke(nmsItemStackObj, nmsNbtTagCompoundObj).toString();
			} else {
				Object nmsItemStackObj = craftItemClass.getMethod("asNMSCopy", ItemStack.class).invoke(null, itemStack);
				return saveMethod.invoke(nmsItemStackObj, nmsNbtTagCompoundObj).toString();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Get the latest color chat
	 * 
	 * @param msg the message that contains color
	 * @return the char of the color or 'r' if nothing found
	 */
	private char getColorChat(String msg) {
		if(msg.length() < 2)
			return 'r';
		for(int i = msg.length() - 2; i > 0; i--) {
			char s = msg.charAt(i);
			if(ChatColor.COLOR_CHAR == s) {
				return msg.charAt(i + 1);
			}
		}
		return 'r';
	}


	public static String styleItem(Player p, ItemStack item, Storage c) {
		String replacer = c.NAME_FORMAT;
		String amount = c.AMOUNT_FORMAT;
		
		if (item.getAmount() == 1) {
			if (c.FORCE_ADD_AMOUNT) {
				amount = amount.replace(TIMES, "1");
				replacer = replacer.replace(AMOUNT, amount);
			} else {
				replacer = replacer.replace(AMOUNT, "");
			}
		} else {
			amount = amount.replace(TIMES, String.valueOf(item.getAmount()));
			replacer = replacer.replace(AMOUNT, amount);
		}
		replacer = replacer.replace(NAME, NamerManager.getName(p, item, c));
		return replacer;
	}
}