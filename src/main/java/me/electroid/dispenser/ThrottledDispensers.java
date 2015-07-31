package me.electroid.dispenser;

import org.apache.commons.lang.mutable.MutableInt;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEntityEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * A simple plugin to throttle the speed at which dispensers shoot tnt.
 */
public class ThrottledDispensers extends JavaPlugin implements Listener {

    private static final String META = "throttled-dispenser";

    /** The minimum interval in milliseconds between two dispenser tnt shots. */
    private double cooldown = 1250;

    @Override
    public void onEnable() {
        saveConfig();
        FileConfiguration config = getConfig();
        try {
            cooldown = config.getDouble("cooldown");
        } catch (NullPointerException npe) {
            npe.printStackTrace();
            getLogger().log(Level.SEVERE, "Unable to register config values, disabling the plugin..");
            getServer().getPluginManager().disablePlugin(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTntDispense(BlockDispenseEntityEvent event) {
        Block block = event.getBlock();
        if (block.getState() instanceof Dispenser && event.getEntity() instanceof TNTPrimed) {
            Dispenser dispenser = (Dispenser) block.getState();
            if (block.hasMetadata(META) && block.getMetadata(META, this).value() instanceof ThrottledDispenserInfo) {
                ThrottledDispenserInfo info = (ThrottledDispenserInfo) block.getMetadata(META, this).value();
                /** Cancel the dispense event if there is a current cooldown active. */
                if (System.currentTimeMillis() < info.getTime()) {
                    event.setCancelled(true);
                } else {
                    block.setMetadata(META, new FixedMetadataValue(this, new ThrottledDispenserInfo(dispenser)));
                }
            } else {
                block.setMetadata(META, new FixedMetadataValue(this, new ThrottledDispenserInfo(dispenser)));
            }
        }
    }

    private void checkDispenserBreak(Block block) {
        if (block.getState() instanceof Dispenser) {
            if (block.hasMetadata(META) && block.getMetadata(META, this).value() instanceof ThrottledDispenserInfo) {
                /** Remove the throttle info and armor stand if the dispenser is removed. */
                ThrottledDispenserInfo info = (ThrottledDispenserInfo) block.getMetadata(META, this).value();
                info.getStand().remove();
                block.removeMetadata(META, this);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerBreak(BlockBreakEvent event) {
        checkDispenserBreak(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityBreak(EntityChangeBlockEvent event) {
        if (event.getTo() != Material.DISPENSER) {
            checkDispenserBreak(event.getBlock());
        }
    }

    /**
     * The info object that is stored in the dispenser's metadata.
     */
    public class ThrottledDispenserInfo {
        private final long time;
        private final ArmorStand stand;

        public ThrottledDispenserInfo(final Dispenser dispenser) {
            time = (long) (System.currentTimeMillis() + cooldown);
            stand = dispenser.getWorld().spawn(dispenser.getLocation().add(0.5, -0.15, 0.5), ArmorStand.class);
            stand.setGravity(false);
            stand.setVisible(false);
            stand.setSmall(true);
            stand.setCustomName("");
            stand.setCustomNameVisible(true);
            /** Use a mutable integer to reference the task id inside the runnable. */
            final MutableInt integer = new MutableInt(-1);
            integer.setValue(Bukkit.getScheduler().scheduleSyncRepeatingTask(ThrottledDispensers.this, new Runnable() {
                public void run() {
                    long time = getTime() - System.currentTimeMillis();
                    if (time <= 0 || dispenser.getBlock().getType() != Material.DISPENSER) {
                        getStand().remove();
                        Bukkit.getScheduler().cancelTask(integer.intValue());
                    } else {
                        getStand().setCustomName(ChatColor.GOLD + Double.toString(time / 1000.0) + " sec");
                    }
                }
            }, 1L, 0L));
        }

        public final long getTime() {
            return time;
        }

        public final ArmorStand getStand() {
            return stand;
        }

    }

}
