package com.disksmp;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class DiskSMP extends JavaPlugin implements Listener, TabExecutor {
    private NamespacedKey discKey;
    private final Map<UUID, Integer> kills = new HashMap<>();
    private boolean smpRunning = true;
    private static final String[] TIERS = {"WOODEN","STONE","IRON","GOLD","DIAMOND","NETHERITE","GOD"};
    private static final String[] NAMES = {"Wooden Disc","Stone Disc","Iron Disc","Golden Disc","Diamond Disc","Netherite Disc","GOD DISC"};
    private final Material[] ICONS = {Material.OAK_BUTTON,Material.STONE,Material.IRON_INGOT,Material.GOLD_INGOT,Material.DIAMOND,Material.NETHERITE_INGOT,Material.NETHER_STAR};

    @Override public void onEnable() {
        saveDefaultConfig();
        discKey = new NamespacedKey(this, "disksmp_disc");
        loadData();
        Objects.requireNonNull(getCommand("disksmp")).setExecutor(this);
        Objects.requireNonNull(getCommand("disksmp")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("DiskSMP enabled.");
    }
    @Override public void onDisable() { saveData(); }

    private void loadData() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        var f = new java.io.File(getDataFolder(), "players.yml");
        if (!f.exists()) return;
        var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        for (String id : y.getKeys(false)) try { kills.put(UUID.fromString(id), y.getInt(id)); } catch (IllegalArgumentException ignored) {}
    }
    private void saveData() {
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        kills.forEach((id,k) -> y.set(id.toString(), k));
        try { y.save(new java.io.File(getDataFolder(), "players.yml")); } catch (java.io.IOException e) { getLogger().severe("Could not save players.yml: "+e.getMessage()); }
    }
    private int getKills(UUID id) { return kills.getOrDefault(id, 0); }
    private int tier(int total) {
        int spent=0;
        for (int i=0;i<6;i++) { spent += getConfig().getInt("upgrade-kills."+TIERS[i], new int[]{1,2,4,6,8,12}[i]); if (total < spent) return i; }
        return 6;
    }
    private int killsIntoTier(int total, int t) {
        int before=0; for(int i=0;i<t;i++) before += getConfig().getInt("upgrade-kills."+TIERS[i], new int[]{1,2,4,6,8,12}[i]);
        return t==6 ? total-before : Math.min(total-before, getConfig().getInt("upgrade-kills."+TIERS[t],1));
    }
    private int requirement(int t) { return t>=6 ? 0 : getConfig().getInt("upgrade-kills."+TIERS[t], new int[]{1,2,4,6,8,12}[t]); }
    private int level(int total, int t) {
        if (t==0) return 0;
        int base=0; for(int i=0;i<t;i++) base+=getConfig().getInt("upgrade-kills."+TIERS[i],new int[]{1,2,4,6,8,12}[i]);
        int max=getConfig().getInt("levels."+TIERS[t]+".max", t==6?10:5);
        int per=Math.max(1,getConfig().getInt("kills-per-level",5));
        return Math.min(max, 1+Math.max(0,total-base)/per);
    }
    private ItemStack makeDisc(Player p) {
        int total=getKills(p.getUniqueId()), t=tier(total), lvl=level(total,t);
        ItemStack item=new ItemStack(ICONS[t]);
        ItemMeta m=item.getItemMeta();
        m.setDisplayName((t==6?ChatColor.LIGHT_PURPLE:ChatColor.GOLD)+NAMES[t]+(t==6?"": " "+ChatColor.GRAY+"(Level "+Math.max(1,lvl)+")"));
        List<String> lore=new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY+"DISKSMP PROGRESSION");
        lore.add(ChatColor.GRAY+"Kills: "+ChatColor.WHITE+(t==6?"MAX":killsIntoTier(total,t)+"/"+requirement(t)));
        if(t<6) lore.add(ChatColor.GRAY+"Next: "+ChatColor.AQUA+NAMES[t+1]);
        if(t>0) lore.add(ChatColor.GRAY+"Level: "+ChatColor.YELLOW+lvl+"/"+getConfig().getInt("levels."+TIERS[t]+".max",t==6?10:5));
        if(t==0) lore.add(ChatColor.GRAY+"No enchantments");
        else lore.add(ChatColor.DARK_GRAY+"Your disc evolves as you earn kills.");
        m.setLore(lore);
        m.getPersistentDataContainer().set(discKey, PersistentDataType.BYTE, (byte)1);
        if(t>0) {
            int ub=getConfig().getInt("levels."+TIERS[t]+".unbreaking-base",0);
            int sh=getConfig().getInt("levels."+TIERS[t]+".sharpness-base",0);
            if(ub>0) m.addEnchant(Enchantment.UNBREAKING, Math.min(ub+Math.max(0,lvl-1),10), true);
            if(sh>0) m.addEnchant(Enchantment.SHARPNESS, Math.min(sh+Math.max(0,lvl-1),10), true);
        }
        item.setItemMeta(m);
        return item;
    }
    private boolean isDisc(ItemStack i) { return i!=null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(discKey, PersistentDataType.BYTE); }
    private void giveOrUpdate(Player p) {
        boolean found=false;
        for(int slot=0;slot<p.getInventory().getSize();slot++) if(isDisc(p.getInventory().getItem(slot))) {
            if(!found) { p.getInventory().setItem(slot,makeDisc(p)); found=true; } else p.getInventory().setItem(slot,null);
        }
        if(!found) p.getInventory().addItem(makeDisc(p)).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(),left));
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p=e.getPlayer();
        if(!p.hasPlayedBefore()) { kills.putIfAbsent(p.getUniqueId(),0); }
        Bukkit.getScheduler().runTask(this,()->giveOrUpdate(p));
    }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) { Bukkit.getScheduler().runTask(this,()->giveOrUpdate(e.getPlayer())); }
    @EventHandler public void onDeath(PlayerDeathEvent e) {
        Player victim=e.getEntity(), killer=victim.getKiller();
        if(killer==null || killer.equals(victim) || !smpRunning) return;
        kills.merge(killer.getUniqueId(),1,Integer::sum);
        int oldTier=tier(getKills(killer.getUniqueId())-1), newTier=tier(getKills(killer.getUniqueId()));
        giveOrUpdate(killer);
        if(newTier>oldTier) {
            killer.sendTitle(ChatColor.GOLD+"DISC EVOLVED!",ChatColor.YELLOW+NAMES[newTier],10,50,15);
            killer.playSound(killer.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,1f,1f);
        } else killer.sendMessage(ChatColor.GREEN+"DiskSMP: "+ChatColor.YELLOW+"Kill progress "+killsIntoTier(getKills(killer.getUniqueId()),newTier)+"/"+requirement(newTier));
        saveData();
    }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { if(isDisc(e.getItemDrop().getItemStack())) e.setCancelled(true); }
    @EventHandler public void onClick(InventoryClickEvent e) {
        if(e.getWhoClicked() instanceof Player p && isDisc(e.getCurrentItem()) && e.getClick().isShiftClick()) {
            // Disc remains usable as a progression item but cannot be moved by shift-click.
            e.setCancelled(true);
        }
    }

    @Override public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if(a.length==0) { s.sendMessage(ChatColor.GOLD+"/disksmp start|stop|center set|border set <size>|border expand <amount>|invsee <player>|reload|stats [player]"); return true; }
        if(!s.hasPermission("disksmp.admin")) { s.sendMessage(ChatColor.RED+"No permission."); return true; }
        switch(a[0].toLowerCase(Locale.ROOT)) {
            case "start" -> { smpRunning=true; s.sendMessage(ChatColor.GREEN+"DiskSMP started. Player kills count toward progression."); }
            case "stop" -> { smpRunning=false; s.sendMessage(ChatColor.RED+"DiskSMP stopped. Progression kills are paused."); }
            case "reload" -> { reloadConfig(); s.sendMessage(ChatColor.GREEN+"DiskSMP config reloaded."); }
            case "center" -> {
                if(a.length<2 || !a[1].equalsIgnoreCase("set") || !(s instanceof Player p)) { s.sendMessage(ChatColor.RED+"Usage: /disksmp center set (in-game)"); return true; }
                WorldBorder b=p.getWorld().getWorldBorder(); b.setCenter(p.getLocation()); s.sendMessage(ChatColor.GREEN+"World border centre set to your location.");
            }
            case "border" -> {
                if(a.length<3) { s.sendMessage(ChatColor.RED+"Usage: /disksmp border set <diameter> OR expand <amount>"); return true; }
                try {
                    if(!(s instanceof Player p)) { s.sendMessage("Run this command in-game."); return true; }
                    WorldBorder b=p.getWorld().getWorldBorder(); double n=Double.parseDouble(a[2]);
                    if(n<=0) throw new NumberFormatException();
                    if(a[1].equalsIgnoreCase("set")) b.setSize(n);
                    else if(a[1].equalsIgnoreCase("expand")) b.setSize(b.getSize()+n);
                    else { s.sendMessage("Use set or expand."); return true; }
                    s.sendMessage(ChatColor.GREEN+"World border updated.");
                } catch(NumberFormatException ex) { s.sendMessage(ChatColor.RED+"Enter a positive number."); }
            }
            case "invsee" -> {
                if(a.length<2) { s.sendMessage("Usage: /disksmp invsee <player>"); return true; }
                Player target=Bukkit.getPlayerExact(a[1]);
                if(target==null) s.sendMessage(ChatColor.RED+"That player is not online.");
                else if(s instanceof Player p) p.openInventory(target.getInventory());
                else s.sendMessage("Console cannot open an inventory.");
            }
            case "stats" -> {
                Player target=a.length>=2?Bukkit.getPlayerExact(a[1]):(s instanceof Player p?p:null);
                if(target==null) { s.sendMessage("Player not online. Usage: /disksmp stats <online-player>"); return true; }
                int t=tier(getKills(target.getUniqueId()));
                s.sendMessage(ChatColor.GOLD+target.getName()+ChatColor.GRAY+" | "+NAMES[t]+" | Total kills: "+getKills(target.getUniqueId()));
            }
            default -> s.sendMessage(ChatColor.RED+"Unknown subcommand. Use /disksmp.");
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if(args.length==1) return List.of("start","stop","center","border","invsee","stats","reload");
        if(args.length==2 && args[0].equalsIgnoreCase("center")) return List.of("set");
        if(args.length==2 && args[0].equalsIgnoreCase("border")) return List.of("set","expand");
        if(args.length==2 && (args[0].equalsIgnoreCase("invsee")||args[0].equalsIgnoreCase("stats"))) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
