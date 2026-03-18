package tan0528.land;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MyPlugin extends JavaPlugin implements CommandExecutor {

    private final Map<UUID, Location[]> selections = new HashMap<>();
    private static Economy econ = null;

    public Map<UUID, Location[]> getSelections() {
        return selections;
    }

    public void setSelections(UUID id,Location[] locations){
        selections.putIfAbsent(id,locations);
    }

    private List<String> plots = new ArrayList<>();

    @Override
    public void onEnable() {
        // Vault経済機能のセットアップ
        if (!setupEconomy()) {
            getLogger().severe("Vaultまたは経済プラグインが見つかりません。経済機能を制限して起動します。");
        }

        // フォルダと設定ファイルの初期化
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        if (getConfig().getConfigurationSection("plots") == null) {
            getConfig().createSection("plots");
            saveConfig();
        }

        // イベントとコマンドの登録
        getServer().getPluginManager().registerEvents(new Event(this), this);

        if (getCommand("land") != null) getCommand("land").setExecutor(this);

        // 範囲選択時のパーティクル表示タスク (0.1秒ごと)
        Bukkit.getScheduler().runTaskTimer(this, this::showParticles, 0L, 2L);



        for (String plotName : getConfig().getConfigurationSection("plots").getKeys(false)) {

            // ここに各プロットに対する処理を書く
            getLogger().info("プロット: " + plotName);

            plots.add(plotName);

        }


    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    private void sendHelp(Player p) {
        p.sendMessage("§8§m---------------------------------------");
        p.sendMessage("§e§l[LandPlugin] §f土地管理システム");
        if (p.isOp()) {
            p.sendMessage("§b§l[管理者ガイド]");
            p.sendMessage("§71. 木のシャベルで2点をクリックして選択");
            p.sendMessage("§72. §f/land create <土地名> <値段> §7で土地を作成");
            p.sendMessage("§b[販売看板の作り方]");
            p.sendMessage("§7・看板を設置し、§e1行目: [PLOTS] §7、§e2行目: 土地名");
            p.sendMessage("§7・§f/land delete <土地名> §7で土地データを完全削除");
            p.sendMessage("§6 すべての土地名を取得 §e/land list");
        }
        p.sendMessage("§a§l[プレイヤー用]");
        p.sendMessage("§7・土地は1人1つまで。看板右クリックで購入確認。");
        p.sendMessage("§7・売却されると、代金は元の持ち主に自動送金されます。");
        p.sendMessage("§7・自分の土地で §e/land mode <土地名> selling/protected");
        p.sendMessage("§8§m---------------------------------------");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) { sendHelp(player); return true; }

        String sub = args[0].toLowerCase();

        if (sub.equals("create") && player.isOp()) {
            if (args.length < 3) { player.sendMessage("§c使い方: /land create <土地名> <値段>"); return true; }
            createPlot(player, args[1], args[2]);
        }
        else if (sub.equals("delete") && player.isOp()) {
            if (args.length < 2) { player.sendMessage("§c使い方: /land delete <土地名>"); return true; }
            deletePlot(player, args[1]);
        }
        else if (sub.equals("confirmbuy") && args.length >= 2) {
            buyPlot(player, args[1]);
            syncAllSigns(args[1]); // 看板の表示を即時更新
        }
        else if (sub.equals("mode") && args.length >= 3) {
            updateMode(player, args[1], args[2].toLowerCase());
        } else if (sub.equals("list") && args.length == 2) {
            plotList(player);

        } else {
            sendHelp(player);
        }
        return true;
    }

    private void deletePlot(Player player, String name) {
        if (!getConfig().contains("plots." + name)) {
            player.sendMessage("§c土地 '" + name + "' は存在しません。"); return;
        }
        getConfig().set("plots." + name, null);
        saveConfig();
        player.sendMessage("§e土地 '" + name + "' のデータを完全に削除しました。");
    }

    private void createPlot(Player player, String name, String priceStr) {
        double price;
        try { price = Double.parseDouble(priceStr); } catch (NumberFormatException e) {
            player.sendMessage("§c値段は半角数字で入力してください。"); return;
        }

        Location[] locs = selections.get(player.getUniqueId());
        if (locs == null || locs[0] == null || locs[1] == null) {
            player.sendMessage("§c木のシャベルで範囲を選択してください。"); return;
        }
        String p = "plots." + name;
        getConfig().set(p + ".world", locs[0].getWorld().getName());
        getConfig().set(p + ".x1", locs[0].getBlockX()); getConfig().set(p + ".y1", locs[0].getBlockY()); getConfig().set(p + ".z1", locs[0].getBlockZ());
        getConfig().set(p + ".x2", locs[1].getBlockX()); getConfig().set(p + ".y2", locs[1].getBlockY()); getConfig().set(p + ".z2", locs[1].getBlockZ());
        getConfig().set(p + ".owner", "none");
        getConfig().set(p + ".status", "selling");
        getConfig().set(p + ".price", price);
        saveConfig();
        player.sendMessage("§a土地 '" + name + "' を作成しました！ (価格: " + price + ")");
    }

    private void buyPlot(Player p, String name) {
        if (!getConfig().contains("plots." + name)) return;

        // 所持数チェック (OPは無制限)
        if (!p.isOp() && getOwnedCount(p) >= 1) {
            p.sendMessage("§c土地は1人1つまでしか所有できません。"); return;
        }

        double price = getConfig().getDouble("plots." + name + ".price", 0);
        if (econ != null && !econ.has(p, price)) {
            p.sendMessage("§cお金が足りません！ (必要: " + price + ")"); return;
        }

        if (!getConfig().getString("plots." + name + ".status", "").equalsIgnoreCase("selling")) {
            p.sendMessage("§cこの土地は現在販売されていません。"); return;
        }

        // --- 送金処理 ---
        String oldOwnerUUID = getConfig().getString("plots." + name + ".owner", "none");
        if (econ != null) {
            econ.withdrawPlayer(p, price); // 購入者から代金を引く
            if (!oldOwnerUUID.equals("none")) {
                UUID oldUUID = UUID.fromString(oldOwnerUUID);
                econ.depositPlayer(Bukkit.getOfflinePlayer(oldUUID), price); // 前所有者へ振り込む
                Player oldOwner = Bukkit.getPlayer(oldUUID);
                if (oldOwner != null && oldOwner.isOnline()) {
                    oldOwner.sendMessage("§a§l[Land] §f土地 '" + name + "' が §e" + price + "円 §fで売れました！");
                }
            }
        }

        // 所有権の更新
        getConfig().set("plots." + name + ".owner", p.getUniqueId().toString());
        getConfig().set("plots." + name + ".status", "protected");
        saveConfig();
        p.sendMessage("§a§l土地 '" + name + "' を購入しました！");
    }

    private int getOwnedCount(Player p) {
        int count = 0;
        ConfigurationSection plots = getConfig().getConfigurationSection("plots");
        if (plots == null) return 0;
        String uuid = p.getUniqueId().toString();
        for (String key : plots.getKeys(false)) {
            if (uuid.equals(getConfig().getString("plots." + key + ".owner"))) count++;
        }
        return count;
    }

    public void updateSign(Block block, String plotName) {
        if (!(block.getState() instanceof Sign sign)) return;
        String status = getConfig().getString("plots." + plotName + ".status", "selling");
        double price = getConfig().getDouble("plots." + plotName + ".price", 0);
        String ownerUUID = getConfig().getString("plots." + plotName + ".owner", "none");
        String ownerName = ownerUUID.equals("none") ? "なし" : Bukkit.getOfflinePlayer(UUID.fromString(ownerUUID)).getName();

        sign.line(0, Component.text("[" + plotName + "]").color(NamedTextColor.BLUE).decorate(TextDecoration.BOLD));
        sign.line(1, Component.text(status.equalsIgnoreCase("selling") ? "§6§l販売中" : "§a§l保護中"));
        sign.line(2, Component.text(ownerUUID.equals("none") ? "価格: " + price : "所有者: " + (ownerName != null ? ownerName : "不明")));
        sign.line(3, Component.text("§8右クリックで購入"));
        sign.update();
    }

    private void updateMode(Player player, String plotName, String mode) {
        if (!getConfig().contains("plots." + plotName)) return;
        String owner = getConfig().getString("plots." + plotName + ".owner", "none");
        if (!player.isOp() && !owner.equals(player.getUniqueId().toString())) {
            player.sendMessage("§cあなたはこの土地の所有者ではありません。"); return;
        }
        getConfig().set("plots." + plotName + ".status", mode);
        saveConfig();
        syncAllSigns(plotName);
        player.sendMessage("§a土地の状態を " + mode + " に変更しました。");
    }

    public boolean canAccess(Player player, Location loc) {
        if (player.isOp()) return true;
        ConfigurationSection plots = getConfig().getConfigurationSection("plots");
        if (plots == null) return true;
        for (String key : plots.getKeys(false)) {
            if (isInside(loc, key)) {
                return player.getUniqueId().toString().equals(getConfig().getString("plots." + key + ".owner"));
            }
        }
        return true;
    }

    private boolean isInside(Location l, String k) {
        String path = "plots." + k;
        String world = getConfig().getString(path + ".world");
        if (world == null || !l.getWorld().getName().equals(world)) return false;
        int x1=getConfig().getInt(path+".x1"), x2=getConfig().getInt(path+".x2");
        int y1=getConfig().getInt(path+".y1"), y2=getConfig().getInt(path+".y2");
        int z1=getConfig().getInt(path+".z1"), z2=getConfig().getInt(path+".z2");
        return l.getBlockX()>=Math.min(x1,x2) && l.getBlockX()<=Math.max(x1,x2) &&
                l.getBlockY()>=Math.min(y1,y2) && l.getBlockY()<=Math.max(y1,y2) &&
                l.getBlockZ()>=Math.min(z1,z2) && l.getBlockZ()<=Math.max(z1,z2);
    }



    private void syncAllSigns(String plotName) {
        ConfigurationSection s = getConfig().getConfigurationSection("plots." + plotName + ".signs");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            String[] split = key.split(",");
            if (split.length < 4) continue;
            Location loc = new Location(Bukkit.getWorld(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3]));
            updateSign(loc.getBlock(), plotName);
        }
    }

    /* Event.javaクラスに移行
    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        String l0 = PlainTextComponentSerializer.plainText().serialize(e.line(0));
        if (l0.equalsIgnoreCase("[PLOTS]")) {
            if (!e.getPlayer().isOp()) { e.setCancelled(true); return; }
            String plotName = PlainTextComponentSerializer.plainText().serialize(e.line(1));
            if (!getConfig().contains("plots." + plotName)) { e.getPlayer().sendMessage("§c土地名が正しくありません。"); return; }
            Location l = e.getBlock().getLocation();
            String locStr = l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
            getConfig().set("plots." + plotName + ".signs." + locStr, true);
            saveConfig();
            Bukkit.getScheduler().runTask(this, () -> updateSign(e.getBlock(), plotName));
        }
    }

     */

    public void sendConfirmation(Player player, String plotName) {
        if (!getConfig().getString("plots." + plotName + ".status", "selling").equalsIgnoreCase("selling")) return;
        double price = getConfig().getDouble("plots." + plotName + ".price", 0);
        player.sendMessage("§6§l土地 '" + plotName + "' を §e" + price + "円 §6で購入しますか？");
        Component button = Component.text("[ ここをクリックして購入を確定 ]")
                .color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand("/land confirmbuy " + plotName));
        player.sendMessage(button);
    }

    /*  Event.javaクラスに移行
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShovelSelect(PlayerInteractEvent e) {
        if (!e.getPlayer().isOp() || e.getItem() == null || e.getItem().getType() != Material.WOODEN_SHOVEL) return;
        if (e.getClickedBlock() == null) return;
        e.setCancelled(true);
        UUID uuid = e.getPlayer().getUniqueId();
        selections.putIfAbsent(uuid, new Location[2]);
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) { selections.get(uuid)[0] = e.getClickedBlock().getLocation(); e.getPlayer().sendMessage("§a始点を設定しました。"); }
        else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) { selections.get(uuid)[1] = e.getClickedBlock().getLocation(); e.getPlayer().sendMessage("§b終点を設定しました。"); }
    }

     */

    private void showParticles() {
        for (UUID u : selections.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || p.getInventory().getItemInMainHand().getType() != Material.WOODEN_SHOVEL) continue;
            Location[] l = selections.get(u);
            if (l[0] != null && l[1] != null) drawBox(p, l[0], l[1]);
        }
    }

    private void drawBox(Player player, Location l1, Location l2) {
        double minX = Math.min(l1.getX(), l2.getX()); double maxX = Math.max(l1.getX(), l2.getX()) + 1.0;
        double minY = Math.min(l1.getY(), l2.getY()); double maxY = Math.max(l1.getY(), l2.getY()) + 1.0;
        double minZ = Math.min(l1.getZ(), l2.getZ()); double maxZ = Math.max(l1.getZ(), l2.getZ()) + 1.0;
        Particle part = Particle.VILLAGER_HAPPY; // 最新バージョン向けに修正
        for (double x = minX; x <= maxX; x += 1.0) { player.spawnParticle(part, x, minY, minZ, 1, 0,0,0,0); player.spawnParticle(part, x, maxY, minZ, 1, 0,0,0,0); player.spawnParticle(part, x, minY, maxZ, 1, 0,0,0,0); player.spawnParticle(part, x, maxY, maxZ, 1, 0,0,0,0); }
        for (double y = minY; y <= maxY; y += 1.0) { player.spawnParticle(part, minX, y, minZ, 1, 0,0,0,0); player.spawnParticle(part, maxX, y, minZ, 1, 0,0,0,0); player.spawnParticle(part, minX, y, maxZ, 1, 0,0,0,0); player.spawnParticle(part, maxX, y, maxZ, 1, 0,0,0,0); }
        for (double z = minZ; z <= maxZ; z += 1.0) { player.spawnParticle(part, minX, minY, z, 1, 0,0,0,0); player.spawnParticle(part, maxX, minY, z, 1, 0,0,0,0); player.spawnParticle(part, minX, maxY, z, 1, 0,0,0,0); player.spawnParticle(part, maxX, maxY, z, 1, 0,0,0,0); }
    }

    private void plotList(Player player){

        player.sendMessage("§6=========土地一覧=========");
        for (int i = 0 ; plots.size() > i; i++) {
            player.sendMessage("§a " + plots.get(i));
        }
        player.sendMessage("§6========================");

    }

}
