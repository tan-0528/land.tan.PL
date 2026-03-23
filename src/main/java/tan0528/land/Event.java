package tan0528.land;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

public class Event  implements Listener {

    private final MyPlugin MYPLUGIN;

    public Event(MyPlugin myPlugin) {
        this.MYPLUGIN = myPlugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        if (!MYPLUGIN.canAccess(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true); e.getPlayer().sendMessage("§cここは保護されています。");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent e) {
        if (!MYPLUGIN.canAccess(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true); e.getPlayer().sendMessage("§cここは保護されています。");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        Block block = e.getClickedBlock();
        if (block == null){
            return;
        }
        Player player  = e.getPlayer();
        //看板保護
        if (block.getState() instanceof Sign sign) {
            String l0 = PlainTextComponentSerializer.plainText().serialize(sign.line(0));
            if (l0.startsWith("[") && l0.endsWith("]")) {
                if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    e.setCancelled(true);
                    MYPLUGIN.sendConfirmation(player, l0.replace("[", "").replace("]", ""));
                }
                return;
            }
        }


        if ((e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL) && block.getType().isInteractable()) {
            if (!MYPLUGIN.canAccess(player, block.getLocation())) {
                e.setCancelled(true);
                if (e.getAction() != Action.PHYSICAL) {
                    e.getPlayer().sendMessage("§c所有者以外は操作できません。");
                }
            }
        }
    }

    @EventHandler
    public void clickedItemFrame(PlayerInteractEntityEvent event){
        Entity entity = event.getRightClicked();

        //額縁保護
        if (entity instanceof ItemFrame itemFrame || entity instanceof ArmorStand armorStand) {
            Player player = event.getPlayer();
            if(player.isOp()){
                return;
            }

            if(!MYPLUGIN.canAccess(player,entity.getLocation())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c所有者以外は操作できません。");

            }


        }

    }

    @EventHandler
    public void breakItemFrame(EntityDamageByEntityEvent event){


        // 絵画、防具立て、絵画保護
        if (event.getEntity() instanceof ItemFrame itemFrame || event.getEntity() instanceof ArmorStand ) {

            if (event.getDamager() instanceof Projectile projectile){
                event.setCancelled(true);
            }
            
            if (event.getDamager() instanceof Player player) {
                if (player.isOp()){
                    return;
                }
                if (!MYPLUGIN.canAccess(player,event.getEntity().getLocation())) {
                    event.setCancelled(true);
                    player.sendMessage("§c所有者以外は操作できません。");
                }

            }

        }

    }

    @EventHandler
    public void hangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player) {
            if (player.isOp()){
                return;
            }
            if (!MYPLUGIN.canAccess(player, event.getEntity().getLocation())) {
                event.setCancelled(true);
                player.sendMessage("§c所有者以外は操作できません。");
            }
        }
    }

    @EventHandler
    public void entityPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof ArmorStand armorStand) {
            Player player = event.getPlayer();
            if (player != null){
                if (player.isOp()){
                    return;
                }
                if (!MYPLUGIN.canAccess(player,event.getEntity().getLocation())) {
                    event.setCancelled(true);
                    player.sendMessage("§c所有者以外は操作できません。");

                }
            }
        }
    }


    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        String l0 = PlainTextComponentSerializer.plainText().serialize(e.line(0));
        if (l0.equalsIgnoreCase("[PLOTS]")) {
            if (!e.getPlayer().isOp()) { e.setCancelled(true); return; }
            String plotName = PlainTextComponentSerializer.plainText().serialize(e.line(1));
            if (!MYPLUGIN.getConfig().contains("plots." + plotName)) { e.getPlayer().sendMessage("§c土地名が正しくありません。"); return; }
            Location l = e.getBlock().getLocation();
            String locStr = l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
            MYPLUGIN.getConfig().set("plots." + plotName + ".signs." + locStr, true);
            MYPLUGIN.saveConfig();
            Bukkit.getScheduler().runTask(MYPLUGIN, () -> MYPLUGIN.updateSign(e.getBlock(), plotName));
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShovelSelect(PlayerInteractEvent e) {
        if (!e.getPlayer().isOp() || e.getItem() == null || e.getItem().getType() != Material.WOODEN_SHOVEL) return;
        if (e.getClickedBlock() == null) return;
        e.setCancelled(true);
        UUID uuid = e.getPlayer().getUniqueId();
        MYPLUGIN.setSelections(uuid, new Location[2]);

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            MYPLUGIN.getSelections().get(uuid)[0] = e.getClickedBlock().getLocation(); e.getPlayer().sendMessage("§a始点を設定しました。");
        }else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            MYPLUGIN.getSelections().get(uuid)[1] = e.getClickedBlock().getLocation(); e.getPlayer().sendMessage("§b終点を設定しました。");
        }
    }


}
