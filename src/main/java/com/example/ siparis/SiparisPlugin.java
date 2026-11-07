package com.example.siparis;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

public class SiparisPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private static final Map<UUID, String> SIPARIS_ADIM = new HashMap<>();
    private static final Map<UUID, String> SIPARIS_ISIM = new HashMap<>();
    private static final Map<UUID, Integer> SIPARIS_MIKTAR = new HashMap<>();
    
    private File pazarDosya;
    private Economy econ = null;
    private Object ppAPI = null;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        pazarDosya = new File(getDataFolder(), "pazar.yml");
        if (!pazarDosya.exists()) {
            try { pazarDosya.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) econ = rsp.getProvider();
        }

        Plugin ppPlugin = getServer().getPluginManager().getPlugin("PlayerPoints");
        if (ppPlugin != null && ppPlugin.isEnabled()) {
            try {
                ppAPI = ppPlugin.getClass().getMethod("getAPI").invoke(ppPlugin);
            } catch (Exception e) {
                getLogger().warning("PlayerPoints API yuklenemedi: " + e.getMessage());
            }
        }

        if (getCommand("siparis") != null) getCommand("siparis").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Premium Pazar Sistemi aktif!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        pazarMenuAc(p, "hepsi", false);
        return true;
    }

    // FİLTRELEME VE MENÜ AÇMA
    private void pazarMenuAc(Player p, String filtre, boolean kendiSiparislerim) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8§l» §6§lPAZAR SİSTEMİ §8§l«");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(pazarDosya);
        
        if (yml.contains("siparisler")) {
            ConfigurationSection section = yml.getConfigurationSection("siparisler");
            int slot = 0;
            for (String id : section.getKeys(false)) {
                if (slot >= 45) break; // Sadece üst 5 satır (0-44)
                
                String oyuncu = section.getString(id + ".oyuncu");
                String esya = section.getString(id + ".esya");
                int miktar = section.getInt(id + ".miktar");
                String birim = section.getString(id + ".birim");
                double toplam = section.getDouble(id + ".toplam_fiyat");
                
                // Kendi siparişlerini göster veya hepsini göster
                if (kendiSiparislerim && !oyuncu.equals(p.getName())) continue;
                
                Material mat = Material.matchMaterial(esya.toUpperCase());
                if (mat == null) mat = Material.PAPER;
                
                // Filtreleme Mantığı
                if (filtre.equals("bloklar") && !mat.isBlock()) continue;
                if (filtre.equals("esyalar") && mat.isBlock()) continue;
                if (filtre.equals("para") && !birim.equals("para")) continue;
                if (filtre.equals("puan") && !birim.equals("puan")) continue;
                
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§e" + esya + " §7x" + miktar);
                    meta.setLore(Arrays.asList(
                        "§7Siparişi Veren: §f" + oyuncu,
                        "§7Toplam Fiyat: §a" + toplam + " " + birim.toUpperCase(),
                        "",
                        "§aSatın almak için tıkla!",
                        "§8ID: " + id
                    ));
                    item.setItemMeta(meta);
                }
                inv.setItem(slot, item);
                slot++;
            }
        }
        
        // --- ALT MENÜ BUTONLARI (45-53) ---
        ItemStack cam = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 45; i < 54; i++) inv.setItem(i, cam);

        // Senin Siparişlerin
        ItemStack kafa = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta kafaMeta = (SkullMeta) kafa.getItemMeta();
        if (kafaMeta != null) {
            kafaMeta.setOwningPlayer(p);
            kafaMeta.setDisplayName("§d§lSenin Siparişlerin");
            kafaMeta.setLore(Arrays.asList("§7Sadece senin açtığın siparişleri gör."));
            kafa.setItemMeta(kafaMeta);
        }
        inv.setItem(45, kafa);

        // Sipariş Ver
        ItemStack ver = new ItemStack(Material.PAPER);
        ItemMeta verMeta = ver.getItemMeta();
        if (verMeta != null) {
            verMeta.setDisplayName("§a§lSipariş Ver");
            verMeta.setLore(Arrays.asList("§7Aradığın eşya için sipariş oluştur."));
            ver.setItemMeta(verMeta);
        }
        inv.setItem(46, ver);

        // Bloklar
        ItemStack blok = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta blokMeta = blok.getItemMeta();
        if (blokMeta != null) { blokMeta.setDisplayName("§2§lSadece Bloklar"); blokMeta.setLore(Arrays.asList("§7Sadece blokları göster.")); blok.setItemMeta(blokMeta); }
        inv.setItem(48, blok);

        // Eşyalar
        ItemStack esyaF = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta esyaMeta = esyaF.getItemMeta();
        if (esyaMeta != null) { esyaMeta.setDisplayName("§b§lSadece Eşyalar"); esyaMeta.setLore(Arrays.asList("§7Sadece eşyaları göster.")); esyaF.setItemMeta(esyaMeta); }
        inv.setItem(49, esyaF);

        // Puan
        ItemStack puanF = new ItemStack(Material.DIAMOND);
        ItemMeta puanMeta = puanF.getItemMeta();
        if (puanMeta != null) { puanMeta.setDisplayName("§e§lDcraft Puanı"); puanMeta.setLore(Arrays.asList("§7Puanla satılanları göster.")); puanF.setItemMeta(puanMeta); }
        inv.setItem(50, puanF);

        // Para
        ItemStack paraF = new ItemStack(Material.GOLD_INGOT);
        ItemMeta paraMeta = paraF.getItemMeta();
        if (paraMeta != null) { paraMeta.setDisplayName("§6§lPara"); paraMeta.setLore(Arrays.asList("§7Parayla satılanları göster.")); paraF.setItemMeta(paraMeta); }
        inv.setItem(51, paraF);

        // Kapat (Bariyer)
        ItemStack kapat = new ItemStack(Material.BARRIER);
        ItemMeta kapatMeta = kapat.getItemMeta();
        if (kapatMeta != null) { kapatMeta.setDisplayName("§c§lKapat"); kapat.setItemMeta(kapatMeta); }
        inv.setItem(53, kapat);

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().equals("§8§l» §6§lPAZAR SİSTEMİ §8§l«")) return;
        
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;

        // ALT MENÜ TIKLAMALARI
        if (e.getClickedInventory() == e.getView().getBottomInventory()) return; // Envanter tıklamalarını engelle

        if (clicked.getType() == Material.BARRIER) {
            p.closeInventory();
        } else if (clicked.getType() == Material.PLAYER_HEAD) {
            pazarMenuAc(p, "hepsi", true);
        } else if (clicked.getType() == Material.PAPER) {
            p.closeInventory();
            SIPARIS_ADIM.put(p.getUniqueId(), "ISIM");
            p.sendMessage("");
            p.sendMessage("§6§lSİPARİŞ OLUŞTURMA");
            p.sendMessage("§7Lütfen aradığın eşyanın ismini yaz (Örn: Elmas).");
            p.sendMessage("§cİptal için 'iptal' yaz.");
            p.sendMessage("");
        } else if (clicked.getType() == Material.GRASS_BLOCK) {
            pazarMenuAc(p, "bloklar", false);
        } else if (clicked.getType() == Material.DIAMOND_SWORD) {
            pazarMenuAc(p, "esyalar", false);
        } else if (clicked.getType() == Material.DIAMOND) {
            pazarMenuAc(p, "puan", false);
        } else if (clicked.getType() == Material.GOLD_INGOT) {
            pazarMenuAc(p, "para", false);
        } 
        
        // SİPARİŞ SATIN ALMA
        else if (clicked.hasItemMeta() && clicked.getItemMeta().getLore() != null) {
            List<String> lore = clicked.getItemMeta().getLore();
            String id = null;
            for (String s : lore) {
                if (s.startsWith("§8ID: ")) { id = s.replace("§8ID: ", ""); break; }
            }
            if (id != null) satinAl(p, id);
        }
    }

    private void satinAl(Player alici, String siparisId) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(pazarDosya);
        String path = "siparisler." + siparisId;
        
        if (!yml.contains(path)) { alici.sendMessage("§cBu sipariş artık mevcut değil!"); alici.closeInventory(); return; }
        
        String saticiIsim = yml.getString(path + ".oyuncu");
        String esyaIsim = yml.getString(path + ".esya");
        int miktar = yml.getInt(path + ".miktar");
        double toplamFiyat = yml.getDouble(path + ".toplam_fiyat");
        String birim = yml.getString(path + ".birim");

        if (alici.getName().equals(saticiIsim)) { alici.sendMessage("§cKendi siparişini satın alamazsın!"); return; }

        if (birim.equals("puan")) {
            if (ppAPI == null) { alici.sendMessage("§cPlayerPoints bulunamadı!"); return; }
            try {
                int bakiye = (int) ppAPI.getClass().getMethod("look", UUID.class).invoke(ppAPI, alici.getUniqueId());
                if (bakiye < toplamFiyat) { alici.sendMessage("§cYetersiz Dcraft Puanın! Gerekli: §e" + toplamFiyat); return; }
                
                ppAPI.getClass().getMethod("take", UUID.class, int.class).invoke(ppAPI, alici.getUniqueId(), (int) toplamFiyat);
                Player satici = Bukkit.getPlayer(saticiIsim);
                if (satici != null && satici.isOnline()) {
                    ppAPI.getClass().getMethod("give", UUID.class, int.class).invoke(ppAPI, satici.getUniqueId(), (int) toplamFiyat);
                    satici.sendMessage("§a[Pazar] §e" + alici.getName() + " §7senin siparişini aldı! Hesabına §e" + toplamFiyat + " Puan §7eklendi.");
                } else {
                    ppAPI.getClass().getMethod("give", UUID.class, int.class).invoke(ppAPI, Bukkit.getOfflinePlayer(saticiIsim).getUniqueId(), (int) toplamFiyat);
                }
            } catch (Exception ex) { alici.sendMessage("§cPuan işlemi hatası!"); return; }
        } else {
            if (econ == null) { alici.sendMessage("§cVault bulunamadı!"); return; }
            if (econ.getBalance(alici) < toplamFiyat) { alici.sendMessage("§cYetersiz bakiyen! Gerekli: §e" + toplamFiyat); return; }
            
            econ.withdrawPlayer(alici, toplamFiyat);
            Player satici = Bukkit.getPlayer(saticiIsim);
            if (satici != null && satici.isOnline()) {
                econ.depositPlayer(satici, toplamFiyat);
                satici.sendMessage("§a[Pazar] §e" + alici.getName() + " §7senin siparişini aldı! Hesabına §e" + toplamFiyat + " Para §7eklendi.");
            } else {
                econ.depositPlayer(Bukkit.getOfflinePlayer(saticiIsim), toplamFiyat);
            }
        }

        Material mat = Material.matchMaterial(esyaIsim.toUpperCase());
        if (mat == null) mat = Material.PAPER;
        ItemStack esyaItem = new ItemStack(mat, miktar);
        HashMap<Integer, ItemStack> kalanlar = alici.getInventory().addItem(esyaItem);
        for (ItemStack kalan : kalanlar.values()) alici.getWorld().dropItemNaturally(alici.getLocation(), kalan);

        yml.set(path, null);
        try { yml.save(pazarDosya); } catch (IOException ex) { ex.printStackTrace(); }

        alici.sendMessage("§a§l✔ §aSiparişi satın aldın! Eşya envanterine eklendi.");
        alici.playSound(alici.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        pazarMenuAc(alici, "hepsi", false); // Menüyü yenile
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (!SIPARIS_ADIM.containsKey(uuid)) return;
        
        e.setCancelled(true);
        String mesaj = e.getMessage();
        if (mesaj.equalsIgnoreCase("iptal")) {
            SIPARIS_ADIM.remove(uuid); SIPARIS_ISIM.remove(uuid); SIPARIS_MIKTAR.remove(uuid);
            p.sendMessage("§cİşlem iptal edildi."); return;
        }

        String adim = SIPARIS_ADIM.get(uuid);

        if (adim.equals("ISIM")) {
            SIPARIS_ISIM.put(uuid, mesaj);
            SIPARIS_ADIM.put(uuid, "MIKTAR");
            p.sendMessage("§7Kaç adet almak istiyorsun? (Sadece sayı yaz)");
        } 
        else if (adim.equals("MIKTAR")) {
            try {
                int miktar = Integer.parseInt(mesaj);
                if (miktar <= 0) throw new NumberFormatException();
                SIPARIS_MIKTAR.put(uuid, miktar);
                SIPARIS_ADIM.put(uuid, "BIRIM");
                p.sendMessage("§7Hangi birimle satın alacaksın? (para veya puan yaz)");
            } catch (NumberFormatException ex) { p.sendMessage("§cGeçerli bir sayı gir!"); }
        } 
        else if (adim.equals("BIRIM")) {
            if (mesaj.equalsIgnoreCase("para") || mesaj.equalsIgnoreCase("puan")) {
                SIPARIS_ADIM.put(uuid, "FIYAT");
                SIPARIS_ISIM.put(uuid, SIPARIS_ISIM.get(uuid) + "|" + mesaj.toLowerCase());
                p.sendMessage("§7Tanesi için ne kadar §e" + mesaj.toUpperCase() + " §7vermek istersin? (Sadece sayı)");
            } else {
                p.sendMessage("§cSadece 'para' veya 'puan' yaz!");
            }
        }
        else if (adim.equals("FIYAT")) {
            try {
                double fiyat = Double.parseDouble(mesaj);
                if (fiyat <= 0) throw new NumberFormatException();
                
                String[] parts = SIPARIS_ISIM.get(uuid).split("\\|");
                String esya = parts[0];
                String birim = parts[1];
                int miktar = SIPARIS_MIKTAR.get(uuid);
                double toplam = fiyat * miktar;
                
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(pazarDosya);
                String id = String.valueOf(System.currentTimeMillis());
                yml.set("siparisler." + id + ".oyuncu", p.getName());
                yml.set("siparisler." + id + ".esya", esya);
                yml.set("siparisler." + id + ".miktar", miktar);
                yml.set("siparisler." + id + ".birim", birim);
                yml.set("siparisler." + id + ".birim_fiyat", fiyat);
                yml.set("siparisler." + id + ".toplam_fiyat", toplam);
                
                try { yml.save(pazarDosya); } catch (IOException ioEx) { p.sendMessage("§cHata oluştu!"); }

                p.sendMessage("§a§l✔ §aSiparişin pazara eklendi!");
                p.sendTitle("§a§lSİPARİŞ VERİLDİ", "§7Pazara eklendi!", 10, 40, 10);
                
            } catch (NumberFormatException ex) { p.sendMessage("§cGeçerli bir fiyat gir!"); }
            
            SIPARIS_ADIM.remove(uuid); SIPARIS_ISIM.remove(uuid); SIPARIS_MIKTAR.remove(uuid);
        }
    }
                                                     }
