package com.example.siparis;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class SiparisPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private static final Map<UUID, String> BEKLEYEN = new HashMap<>();
    private File siparisDosya;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        siparisDosya = new File(getDataFolder(), "siparisler.txt");

        getCommand("siparis").setExecutor(this);
        getCommand("siparisonayla").setExecutor(this);
        getCommand("siparisiptal").setExecutor(this);
        getCommand("siparisler").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("SiparisPlugin aktif!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cSadece oyuncular!");
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
            case "siparis": menuAc(p); return true;
            case "siparisonayla": onayla(p); return true;
            case "siparisiptal":
                BEKLEYEN.remove(p.getUniqueId());
                p.sendMessage("§cSipariş iptal edildi.");
                return true;
            case "siparisler":
                if (!p.hasPermission("siparis.admin")) {
                    p.sendMessage("§cYetkin yok!"); return true;
                }
                siparisleriGoster(p); return true;
        }
        return false;
    }

    private void menuAc(Player p) {
        String baslik = getConfig().getString("menu-baslik", "§6Sipariş");
        Inventory inv = Bukkit.createInventory(null, 27, baslik);

        ConfigurationSection urunler = getConfig().getConfigurationSection("urunler");
        if (urunler != null) {
            for (String key : urunler.getKeys(false)) {
                ConfigurationSection u = urunler.getConfigurationSection(key);
                if (u == null) continue;
                Material mat = Material.matchMaterial(u.getString("material", "STONE"));
                if (mat == null) mat = Material.STONE;
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(u.getString("isim", "§eÜrün"));
                    meta.setLore(u.getStringList("aciklama"));
                    item.setItemMeta(meta);
                }
                inv.setItem(u.getInt("slot", 10), item);
            }
        }

        ItemStack cam = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta();
        if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 0; i < 27; i++) if (inv.getItem(i) == null) inv.setItem(i, cam);

        p.openInventory(inv);
        p.sendTitle("§6§lSİPARİŞ", "§7Premium Menü", 10, 40, 10);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String baslik = getConfig().getString("menu-baslik", "§6Sipariş");
        if (!e.getView().getTitle().equals(baslik)) return;
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
        if (!clicked.hasItemMeta() || clicked.getItemMeta() == null) return;

        String urunAdi = clicked.getItemMeta().getDisplayName();
        ConfigurationSection urunler = getConfig().getConfigurationSection("urunler");
        if (urunler == null) return;

        double fiyat = 0;
        for (String key : urunler.getKeys(false)) {
            ConfigurationSection u = urunler.getConfigurationSection(key);
            if (u != null && u.getString("isim", "").equals(urunAdi)) {
                fiyat = u.getDouble("fiyat", 0);
                break;
            }
        }

        p.closeInventory();
        p.sendMessage("");
        p.sendMessage("§8§m----------------------------");
        p.sendMessage("§6§l  SİPARİŞ ONAYI");
        p.sendMessage("§7Ürün: " + urunAdi);
        p.sendMessage("§7Fiyat: §e" + fiyat + " TL");
        p.sendMessage("§aOnayla: §e/siparisonayla");
        p.sendMessage("§cİptal: §e/siparisiptal");
        p.sendMessage("§8§m----------------------------");
        BEKLEYEN.put(p.getUniqueId(), urunAdi + "|" + fiyat);
    }

    private void onayla(Player p) {
        String veri = BEKLEYEN.remove(p.getUniqueId());
        if (veri == null) { p.sendMessage("§cBekleyen siparişin yok!"); return; }

        String[] parca = veri.split("\\|");
        String urun = parca[0];
        double fiyat = Double.parseDouble(parca[1]);

        try {
            if (!getDataFolder().exists()) getDataFolder().mkdirs();
            if (!siparisDosya.exists()) siparisDosya.createNewFile();
            String tarih = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());
            String satir = String.format("[%s] %s -> %s (%.2f TL)%n", tarih, p.getName(), urun, fiyat);
            try (FileWriter fw = new FileWriter(siparisDosya, true)) { fw.write(satir); }
        } catch (IOException ex) {
            getLogger().warning("Kaydedilemedi: " + ex.getMessage());
        }

        p.sendMessage("§a§l✔ §aSiparişin alındı!");
        p.sendMessage("§7Ürün: " + urun + " §7| Fiyat: §e" + fiyat + " TL");
        p.sendTitle("§a§lSİPARİŞ ALINDI", "§7Teşekkürler!", 10, 50, 10);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("siparis.admin")) {
                online.sendMessage("§8[§6Sipariş§8] §e" + p.getName() + " §7-> " + urun + " §7(§a" + fiyat + " TL§7)");
            }
        }
    }

    private void siparisleriGoster(Player p) {
        List<String> liste = new ArrayList<>();
        if (siparisDosya.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(siparisDosya))) {
                String line;
                while ((line = br.readLine()) != null) liste.add(line);
            } catch (IOException ignored) {}
        }
        p.sendMessage("§8§m----------------------------");
        p.sendMessage("§6§l  SİPARİŞLER §7(" + liste.size() + ")");
        if (liste.isEmpty()) p.sendMessage("§7Henüz sipariş yok.");
        else { int i = 1; for (String s : liste) p.sendMessage("§e" + i++ + ". §f" + s); }
        p.sendMessage("§8§m----------------------------");
    }
          }
