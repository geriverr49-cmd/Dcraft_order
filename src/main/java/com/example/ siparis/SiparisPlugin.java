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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class SiparisPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private static final Map<UUID, String> BEKLEYEN = new HashMap<>();
    private static final Map<UUID, String> SIPARIS_ADIM = new HashMap<>();
    private static final Map<UUID, String> SIPARIS_ISIM = new HashMap<>();
    private static final Map<UUID, Integer> SIPARIS_MIKTAR = new HashMap<>();
    private static final Map<UUID, String> SIPARIS_BIRIM = new HashMap<>();
    
    private File siparisDosya;
    private File pazarDosya;
    private Economy econ = null;
    private Object ppAPI = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        siparisDosya = new File(getDataFolder(), "siparisler.txt");
        
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

        getCommand("siparis").setExecutor(this);
        getCommand("siparisonayla").setExecutor(this);
        getCommand("siparisiptal").setExecutor(this);
        getCommand("siparisler").setExecutor(this);
        getCommand("pazar").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("Pazar ve Siparis Sistemi aktif!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cSadece oyuncular!");
            return true;
        }
        
        switch (cmd.getName().toLowerCase()) {
            case "pazar": pazarMenuAc(p); return true;
            case "siparis": menuAc(p); return true;
            case "siparisonayla": onayla(p); return true;
            case "siparisiptal":
                BEKLEYEN.remove(p.getUniqueId());
                SIPARIS_ADIM.remove(p.getUniqueId());
                p.sendMessage("§cİşlem iptal edildi.");
                return true;
            case "siparisler":
                if (!p.hasPermission("siparis.admin")) { p.sendMessage("§cYetkin yok!"); return true; }
                siparisleriGoster(p); return true;
        }
        return false;
    }

    private void pazarMenuAc(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8§l» §6§lPAZAR SİSTEMİ §8§l«");
        
        ItemStack siparis = new ItemStack(Material.PAPER);
        ItemMeta siparisMeta = siparis.getItemMeta();
        if (siparisMeta != null) {
            siparisMeta.setDisplayName("§e§lSipariş Oluştur");
            siparisMeta.setLore(Arrays.asList("§7Aradığın eşyayı bulamadın mı?", "§7Fiyat ve miktar belirleyip sipariş ver!", "§aTıkla ve eşya ismini sohbete yaz."));
            siparis.setItemMeta(siparisMeta);
        }
        inv.setItem(11, siparis);
        
        ItemStack sat = new ItemStack(Material.GOLD_INGOT);
        ItemMeta satMeta = sat.getItemMeta();
        if (satMeta != null) {
            satMeta.setDisplayName("§a§lEşya Sat");
            satMeta.setLore(Arrays.asList("§7Elindeki eşyayı pazara koy.", "§c(Yakında Eklenecek)"));
            sat.setItemMeta(satMeta);
        }
        inv.setItem(13, sat);

        ItemStack gor = new ItemStack(Material.CHEST);
        ItemMeta gorMeta = gor.getItemMeta();
        if (gorMeta != null) {
            gorMeta.setDisplayName("§b§lAktif Siparişler");
            gorMeta.setLore(Arrays.asList("§7Diğer oyuncuların açtığı siparişleri gör.", "§7Elindeki eşyayı onlara sat!", "§aTıkla ve pazara göz at."));
            gor.setItemMeta(gorMeta);
        }
        inv.setItem(15, gor);

        ItemStack cam = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 0; i < 27; i++) if (inv.getItem(i) == null) inv.setItem(i, cam);

        p.openInventory(inv);
    }

    private void aktifSiparislerMenuAc(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8§l» §6§lAKTİF SİPARİŞLER §8§l«");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(pazarDosya);
        
        if (yml.contains("siparisler")) {
            ConfigurationSection section = yml.getConfigurationSection("siparisler");
            int slot = 0;
            for (String id : section.getKeys(false)) {
                if (slot >= 45) break;
                
                String oyuncu = section.getString(id + ".oyuncu");
                String esya = section.getString(id + ".esya");
                int miktar = section.getInt(id + ".miktar");
                String birim = section.getString(id + ".birim");
                double birim_fiyat = section.getDouble(id + ".birim_fiyat");
                double toplam = section.getDouble(id + ".toplam_fiyat");
                
                Material mat = Material.matchMaterial(esya.toUpperCase());
                if (mat == null) mat = Material.PAPER;
                
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§e" + esya + " §7x" + miktar);
                    meta.setLore(Arrays.asList(
                        "§7Siparişi Veren: §f" + oyuncu,
                        "§7Birim Fiyat: §a" + birim_fiyat + " " + birim.toUpperCase(),
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
        
        ItemStack cam = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 45; i < 54; i++) inv.setItem(i, cam);
        
        ItemStack geri = new ItemStack(Material.BARRIER);
        ItemMeta geriMeta = geri.getItemMeta();
        if (geriMeta != null) { geriMeta.setDisplayName("§cGeri Dön"); geri.setItemMeta(geriMeta); }
        inv.setItem(49, geri);

        p.openInventory(inv);
    }

    private void birimSecimMenuAc(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8§l» §6§lBİRİM SEÇİMİ §8§l«");

        ItemStack para = new ItemStack(Material.GOLD_INGOT);
        ItemMeta paraMeta = para.getItemMeta();
        if (paraMeta != null) {
            paraMeta.setDisplayName("§e§lPARA ile Al");
            paraMeta.setLore(Arrays.asList("§7Normal sunucu paranı kullanarak", "§7bu siparişi oluşturursun."));
            para.setItemMeta(paraMeta);
        }
        inv.setItem(11, para);

        ItemStack puan = new ItemStack(Material.DIAMOND);
        ItemMeta puanMeta = puan.getItemMeta();
        if (puanMeta != null) {
            puanMeta.setDisplayName("§b§lDCRAFT PUANI ile Al");
            puanMeta.setLore(Arrays.asList("§7PlayerPoints bakiyeni kullanarak", "§7bu siparişi oluşturursun."));
            puan.setItemMeta(puanMeta);
        }
        inv.setItem(15, puan);

        ItemStack cam = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 0; i < 27; i++) if (inv.getItem(i) == null) inv.setItem(i, cam);

        p.openInventory(inv);
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
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 0; i < 27; i++) if (inv.getItem(i) == null) inv.setItem(i, cam);
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        
        if (e.getView().getTitle().equals("§8§l» §6§lPAZAR SİSTEMİ §8§l«")) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null) return;
            
            if (clicked.getType() == Material.PAPER) {
                p.closeInventory();
                SIPARIS_ADIM.put(p.getUniqueId(), "ISIM");
                p.sendMessage("");
                p.sendMessage("§6§lSİPARİŞ OLUŞTURMA");
                p.sendMessage("§7Lütfen aradığın eşyanın ismini yaz (Örn: Elmas).");
                p.sendMessage("§cİptal için 'iptal' yaz.");
                p.sendMessage("");
            } else if (clicked.getType() == Material.CHEST) {
                aktifSiparislerMenuAc(p);
            }
            return;
        }

        if (e.getView().getTitle().equals("§8§l» §6§lAKTİF SİPARİŞLER §8§l«")) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
            
            if (clicked.getType() == Material.BARRIER) {
                pazarMenuAc(p);
                return;
            }
            
            if (clicked.hasItemMeta() && clicked.getItemMeta().getLore() != null) {
                List<String> lore = clicked.getItemMeta().getLore();
                String id = null;
                for (String s : lore) {
                    if (s.startsWith("§8ID: ")) { id = s.replace("§8ID: ", ""); break; }
                }
                if (id != null) satinAl(p, id);
            }
            return;
        }

        if (e.getView().getTitle().equals("§8§l» §6§lBİRİM SEÇİMİ §8§l«")) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null) return;
            UUID uuid = p.getUniqueId();

            if (clicked.getType() == Material.GOLD_INGOT) {
                SIPARIS_BIRIM.put(uuid, "para");
                SIPARIS_ADIM.put(uuid, "FIYAT");
                p.closeInventory();
                p.sendMessage("§aBirim: §ePARA");
                p.sendMessage("§7Son olarak, 1 adetinin fiyatını yaz. (Örn: 15).");
            } else if (clicked.getType() == Material.DIAMOND) {
                SIPARIS_BIRIM.put(uuid, "puan");
                SIPARIS_ADIM.put(uuid, "FIYAT");
                p.closeInventory();
                p.sendMessage("§aBirim: §eDCRAFT PUANI (PlayerPoints)");
                p.sendMessage("§7Son olarak, 1 adetinin fiyatını yaz. (Örn: 15).");
            }
            return;
        }
    }

    private void satinAl(Player alici, String siparisId) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(pazarDosya);
        String path = "siparisler." + siparisId;
        
        if (!yml.contains(path)) {
            alici.sendMessage("§cBu sipariş artık mevcut değil!");
            alici.closeInventory();
            return;
        }
        
        String saticiIsim = yml.getString(path + ".oyuncu");
        String esyaIsim = yml.getString(path + ".esya");
        int miktar = yml.getInt(path + ".miktar");
        double toplamFiyat = yml.getDouble(path + ".toplam_fiyat");
        String birim = yml.getString(path + ".birim");

        if (alici.getName().equals(saticiIsim)) {
            alici.sendMessage("§cKendi siparişini satın alamazsın!");
            return;
        }

        if (birim.equals("puan")) {
            if (ppAPI == null) {
                alici.sendMessage("§cPlayerPoints eklentisi bulunamadı veya aktif değil!");
                return;
            }
            try {
                int bakiye = (int) ppAPI.getClass().getMethod("look", UUID.class).invoke(ppAPI, alici.getUniqueId());
                if (bakiye < toplamFiyat) {
                    alici.sendMessage("§cYetersiz Dcraft Puanın! Gerekli: §e" + toplamFiyat);
                    return;
                }
                ppAPI.getClass().getMethod("take", UUID.class, int.class).invoke(ppAPI, alici.getUniqueId(), (int) toplamFiyat);
                
                Player satici = Bukkit.getPlayer(saticiIsim);
                if (satici != null && satici.isOnline()) {
                    ppAPI.getClass().getMethod("give", UUID.class, int.class).invoke(ppAPI, satici.getUniqueId(), (int) toplamFiyat);
                    satici.sendMessage("§a[Pazar] §e" + alici.getName() + " §7senin siparişini satın aldı! Hesabına §e" + toplamFiyat + " Dcraft Puanı §7eklendi.");
                } else {
                    ppAPI.getClass().getMethod("give", UUID.class, int.class).invoke(ppAPI, Bukkit.getOfflinePlayer(saticiIsim).getUniqueId(), (int) toplamFiyat);
                }
            } catch (Exception ex) {
                alici.sendMessage("§cPuan işlemi sırasında bir hata oluştu: " + ex.getMessage());
                return;
            }
        } else {
            if (econ == null) {
                alici.sendMessage("§cEkonomi sistemi (Vault) sunucuda bulunamadı!");
                return;
            }
            if (econ.getBalance(alici) < toplamFiyat) {
                alici.sendMessage("§cYetersiz bakiyen! Gerekli: §e" + toplamFiyat + " PARA");
                return;
            }
            econ.withdrawPlayer(alici, toplamFiyat);
            
            Player satici = Bukkit.getPlayer(saticiIsim);
            if (satici != null && satici.isOnline()) {
                econ.depositPlayer(satici, toplamFiyat);
                satici.sendMessage("§a[Pazar] §e" + alici.getName() + " §7senin siparişini satın aldı! Hesabına §e" + toplamFiyat + " PARA §7eklendi.");
            } else {
                econ.depositPlayer(Bukkit.getOfflinePlayer(saticiIsim), toplamFiyat);
            }
        }

        Material mat = Material.matchMaterial(esyaIsim.toUpperCase());
        if (mat == null) mat = Material.PAPER;
        
        ItemStack esyaItem = new ItemStack(mat, miktar);
        HashMap<Integer, ItemStack> kalanlar = alici.getInventory().addItem(esyaItem);
        for (ItemStack kalan : kalanlar.values()) {
            alici.getWorld().dropItemNaturally(alici.getLocation(), kalan);
        }

        yml.set(path, null);
        try { yml.save(pazarDosya); } catch (IOException ex) { ex.printStackTrace(); }

        alici.sendMessage("");
        alici.sendMessage("§8§m----------------------------");
        alici.sendMessage("§a§lSATIN ALMA BAŞARILI!");
        alici.sendMessage("§7Eşya: §e" + esyaIsim + " §7x" + miktar);
        alici.sendMessage("§7Ödenen: §e" + toplamFiyat + " " + birim.toUpperCase());
        alici.sendMessage("§8§m----------------------------");
        alici.closeInventory();
        alici.playSound(alici.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        
        if (!SIPARIS_ADIM.containsKey(uuid)) return;
        
        e.setCancelled(true);
        String mesaj = e.getMessage();
        
        if (mesaj.equalsIgnoreCase("iptal")) {
            SIPARIS_ADIM.remove(uuid);
            SIPARIS_ISIM.remove(uuid);
            SIPARIS_MIKTAR.remove(uuid);
            SIPARIS_BIRIM.remove(uuid);
            p.sendMessage("§cİşlem iptal edildi.");
            return;
        }

        String adim = SIPARIS_ADIM.get(uuid);

        if (adim.equals("ISIM")) {
            SIPARIS_ISIM.put(uuid, mesaj);
            SIPARIS_ADIM.put(uuid, "MIKTAR");
            p.sendMessage("§aEşya: §e" + mesaj);
            p.sendMessage("§7Şimdi kaç adet almak istediğini yaz (Sadece sayı, Örn: 32).");
        } 
        else if (adim.equals("MIKTAR")) {
            try {
                int miktar = Integer.parseInt(mesaj);
                if (miktar <= 0) throw new NumberFormatException();
                SIPARIS_MIKTAR.put(uuid, miktar);
                Bukkit.getScheduler().runTask(this, () -> birimSecimMenuAc(p));
            } catch (NumberFormatException ex) {
                p.sendMessage("§cLütfen geçerli bir sayı gir!");
            }
        } 
        else if (adim.equals("FIYAT")) {
            try {
                double fiyat = Double.parseDouble(mesaj);
                if (fiyat <= 0) throw new NumberFormatException();
                
                String esya = SIPARIS_ISIM.get(uuid);
                int miktar = SIPARIS_MIKTAR.get(uuid);
                String birim = SIPARIS_BIRIM.get(uuid);
                double toplam = fiyat * miktar;
                
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(pazarDosya);
                String id = String.valueOf(System.currentTimeMillis());
                yml.set("siparisler." + id + ".oyuncu", p.getName());
                yml.set("siparisler." + id + ".esya", esya);
                yml.set("siparisler." + id + ".miktar", miktar);
                yml.set("siparisler." + id + ".birim", birim);
                yml.set("siparisler." + id + ".birim_fiyat", fiyat);
                yml.set("siparisler." + id + ".toplam_fiyat", toplam);
                
                try {
                    yml.save(pazarDosya);
                } catch (IOException ioEx) {
                    p.sendMessage("§cSipariş kaydedilirken bir hata oluştu!");
                    ioEx.printStackTrace();
                }

                p.sendMessage("");
                p.sendMessage("§8§m----------------------------");
                p.sendMessage("§a§lBAŞARILI!");
                p.sendMessage("§7Siparişin pazara eklendi.");
                p.sendMessage("§7Eşya: §e" + esya + " §7x" + miktar);
                p.sendMessage("§7Toplam Fiyat: §e" + toplam + " " + birim.toUpperCase());
                p.sendMessage("§8§m----------------------------");
                
            } catch (NumberFormatException ex) {
                p.sendMessage("§cLütfen geçerli bir fiyat gir!");
            }
            
            SIPARIS_ADIM.remove(uuid);
            SIPARIS_ISIM.remove(uuid);
            SIPARIS_MIKTAR.remove(uuid);
            SIPARIS_BIRIM.remove(uuid);
        }
    }

    private void onayla(Player p) {
        String veri = BEKLEYEN.remove(p.getUniqueId());
        if (veri == null) { p.sendMessage("§cBekleyen siparişin yok!"); return; }
        String[] parca = veri.split("\\|");
        String urun = parca[0];
        double fiyat = Double.parseDouble(parca[1]);
        
        try {
            if (!siparisDosya.exists()) siparisDosya.createNewFile();
            String tarih = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());
            String satir = String.format("[%s] %s -> %s (%.2f TL)%n", tarih, p.getName(), urun, fiyat);
            try (FileWriter fw = new FileWriter(siparisDosya, true)) { fw.write(satir); }
        } catch (IOException ex) { getLogger().warning("Kaydedilemedi: " + ex.getMessage()); }

        p.sendMessage("§a§l✔ §aSiparişin alındı!");
        p.sendTitle("§a§lSİPARİŞ ALINDI", "§7Teşekkürler!", 10, 50, 10);
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
