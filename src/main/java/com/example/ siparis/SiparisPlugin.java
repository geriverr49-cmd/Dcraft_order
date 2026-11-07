package com.example.siparis;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

public class SiparisPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<UUID, String> ADIM = new HashMap<>();
    private final Map<UUID, String> ESYA = new HashMap<>();
    private final Map<UUID, Integer> MIKTAR = new HashMap<>();
    private final Map<UUID, Double> FIYAT = new HashMap<>();
    
    private final Map<UUID, Location> TABELA_KONUM = new HashMap<>();
    private final List<Material> ARAMA_SONUCLARI = new ArrayList<>();

    private File orderDosya;
    private Economy econ = null;
    private Object ppAPI = null;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        orderDosya = new File(getDataFolder(), "order.yml");
        if (!orderDosya.exists()) {
            try { orderDosya.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) econ = rsp.getProvider();
        }

        Plugin ppPlugin = getServer().getPluginManager().getPlugin("PlayerPoints");
        if (ppPlugin != null && ppPlugin.isEnabled()) {
            try { ppAPI = ppPlugin.getClass().getMethod("getAPI").invoke(ppPlugin); } catch (Exception ignored) {}
        }

        if (getCommand("siparis") != null) getCommand("siparis").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("Order ve Filtre Sistemi aktif!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        anaMenuAc(p, "hepsi", false);
        return true;
    }

    private void anaMenuAc(Player p, String filtre, boolean kendiSiparislerim) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8§l» §6§lORDER SİSTEMİ §8§l«");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(orderDosya);
        
        if (yml.contains("siparisler")) {
            ConfigurationSection section = yml.getConfigurationSection("siparisler");
            int slot = 0;
            for (String id : section.getKeys(false)) {
                if (slot >= 45) break;
                
                String oyuncu = section.getString(id + ".oyuncu");
                String esya = section.getString(id + ".esya");
                int miktar = section.getInt(id + ".miktar");
                String birim = section.getString(id + ".birim");
                double toplam = section.getDouble(id + ".toplam_fiyat");
                
                if (kendiSiparislerim && !oyuncu.equals(p.getName())) continue;
                
                Material mat = Material.matchMaterial(esya);
                if (mat == null) mat = Material.PAPER;
                
                if (filtre.equals("blok") && !mat.isBlock()) continue;
                if (filtre.equals("esya") && mat.isBlock()) continue;
                if (filtre.equals("para") && !birim.equals("para")) continue;
                if (filtre.equals("puan") && !birim.equals("puan")) continue;
                if (filtre.equals("ucuz") && toplam > 50) continue;
                if (filtre.equals("pahali") && toplam <= 50) continue;
                
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§e" + mat.name() + " §7x" + miktar);
                    meta.setLore(Arrays.asList(
                        "§7Siparişi Veren: §f" + oyuncu,
                        "§7Toplam Fiyat: §a" + toplam + " " + birim.toUpperCase(),
                        "", "§aSatın almak için tıkla!", "§8ID: " + id
                    ));
                    item.setItemMeta(meta);
                }
                inv.setItem(slot++, item);
            }
        }
        
        ItemStack cam = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 45; i < 54; i++) inv.setItem(i, cam);

        ItemStack kafa = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta kafaMeta = (SkullMeta) kafa.getItemMeta();
        if (kafaMeta != null) {
            kafaMeta.setOwningPlayer(p);
            kafaMeta.setDisplayName("§d§lSenin Siparişlerin");
            kafaMeta.setLore(Arrays.asList("§7Sadece senin açtığın siparişleri gör."));
            kafa.setItemMeta(kafaMeta);
        }
        inv.setItem(45, kafa);

        ItemStack ver = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta verMeta = ver.getItemMeta();
        if (verMeta != null) {
            verMeta.setDisplayName("§a§lSipariş Ver");
            verMeta.setLore(Arrays.asList("§7Yeni bir sipariş oluştur."));
            ver.setItemMeta(verMeta);
        }
        inv.setItem(46, ver);

        ItemStack huni = new ItemStack(Material.HOPPER);
        ItemMeta hMeta = huni.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName("§e§lFiltrele");
            hMeta.setLore(Arrays.asList(
                "§7Siparişleri filtrelemek için tıkla.",
                "§8- Bloklar", "§8- Eşyalar", "§8- Para", "§8- Dcraft Puanı", "§8- Ucuz", "§8- Pahalı"
            ));
            huni.setItemMeta(hMeta);
        }
        inv.setItem(49, huni);

        ItemStack kapat = new ItemStack(Material.BARRIER);
        ItemMeta kapatMeta = kapat.getItemMeta();
        if (kapatMeta != null) { kapatMeta.setDisplayName("§c§lKapat"); kapat.setItemMeta(kapatMeta); }
        inv.setItem(53, kapat);

        p.openInventory(inv);
    }

    private void filtreMenuAc(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8§l» §6§lFİLTRELE §8§l«");
        ItemStack cam = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 0; i < 27; i++) inv.setItem(i, cam);

        ItemStack hepsi = new ItemStack(Material.PAPER);
        ItemMeta hMeta = hepsi.getItemMeta(); if (hMeta != null) { hMeta.setDisplayName("§f§lTümünü Göster"); hepsi.setItemMeta(hMeta); }
        inv.setItem(10, hepsi);

        ItemStack blok = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta bMeta = blok.getItemMeta(); if (bMeta != null) { bMeta.setDisplayName("§2§lSadece Bloklar"); blok.setItemMeta(bMeta); }
        inv.setItem(11, blok);

        ItemStack esya = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta eMeta = esya.getItemMeta(); if (eMeta != null) { eMeta.setDisplayName("§b§lSadece Eşyalar"); esya.setItemMeta(eMeta); }
        inv.setItem(12, esya);

        ItemStack para = new ItemStack(Material.GOLD_INGOT);
        ItemMeta pMeta = para.getItemMeta(); if (pMeta != null) { pMeta.setDisplayName("§6§lSadece Para ile"); para.setItemMeta(pMeta); }
        inv.setItem(14, para);

        ItemStack puan = new ItemStack(Material.DIAMOND);
        ItemMeta puMeta = puan.getItemMeta(); if (puMeta != null) { puMeta.setDisplayName("§b§lSadece Puan ile"); puan.setItemMeta(puMeta); }
        inv.setItem(15, puan);

        ItemStack ucuz = new ItemStack(Material.COPPER_INGOT);
        ItemMeta uMeta = ucuz.getItemMeta(); if (uMeta != null) { uMeta.setDisplayName("§a§lUcuz Siparişler"); uMeta.setLore(Arrays.asList("§750 ve altı")); ucuz.setItemMeta(uMeta); }
        inv.setItem(16, ucuz);

        ItemStack pahali = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta pahMeta = pahali.getItemMeta(); if (pahMeta != null) { pahMeta.setDisplayName("§c§lPahalı Siparişler"); pahMeta.setLore(Arrays.asList("§750 üstü")); pahali.setItemMeta(pahMeta); }
        inv.setItem(17, pahali);

        ItemStack geri = new ItemStack(Material.BARRIER);
        ItemMeta gMeta = geri.getItemMeta(); if (gMeta != null) { gMeta.setDisplayName("§cGeri Dön"); geri.setItemMeta(gMeta); }
        inv.setItem(26, geri);

        p.openInventory(inv);
    }

    private void siparisOlusturMenuAc(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8§l» §6§lSİPARİŞ OLUŞTUR §8§l«");
        ItemStack cam = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 0; i < 27; i++) inv.setItem(i, cam);

        ItemStack esyaBtn = new ItemStack(Material.DIAMOND);
        ItemMeta eMeta = esyaBtn.getItemMeta();
        if (eMeta != null) { eMeta.setDisplayName("§b§l1. Eşya Seç"); eMeta.setLore(Arrays.asList("§7Tabelaya eşya ismini yaz.", "§7Örn: diamond, gold, iron")); esyaBtn.setItemMeta(eMeta); }
        inv.setItem(11, esyaBtn);

        ItemStack miktarBtn = new ItemStack(Material.PAPER);
        ItemMeta mMeta = miktarBtn.getItemMeta();
        if (mMeta != null) { mMeta.setDisplayName("§e§l2. Miktar Belirle"); mMeta.setLore(Arrays.asList("§7Tabelaya kaç adet istediğini yaz.", "§7Min: 1")); miktarBtn.setItemMeta(mMeta); }
        inv.setItem(13, miktarBtn);

        ItemStack fiyatBtn = new ItemStack(Material.GOLD_INGOT);
        ItemMeta fMeta = fiyatBtn.getItemMeta();
        if (fMeta != null) { fMeta.setDisplayName("§6§l3. Fiyat Belirle"); fMeta.setLore(Arrays.asList("§7Tabelaya fiyatı yaz.", "§7Sonra Para/Puan seç.")); fiyatBtn.setItemMeta(fMeta); }
        inv.setItem(15, fiyatBtn);

        p.openInventory(inv);
    }

    private void tabelaAc(Player p, String adim) {
        ADIM.put(p.getUniqueId(), adim);
        p.closeInventory();
        Location loc = p.getLocation();
        Block block = loc.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() - 2, loc.getBlockZ());
        block.setType(Material.OAK_SIGN);
        TABELA_KONUM.put(p.getUniqueId(), block.getLocation());
        Sign sign = (Sign) block.getState();
        p.openSign(sign);
    }

    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        Player p = e.getPlayer();
        if (!ADIM.containsKey(p.getUniqueId())) return;

        String yazilan = e.getLine(0);
        e.setCancelled(true);

        Location tabelaLoc = TABELA_KONUM.remove(p.getUniqueId());
        if (tabelaLoc != null) tabelaLoc.getBlock().setType(Material.AIR);

        if (yazilan == null || yazilan.isEmpty()) {
            p.sendMessage("§cBir şey yazmadın!");
            ADIM.remove(p.getUniqueId());
            return;
        }

        String adim = ADIM.get(p.getUniqueId());

        if (adim.equals("ESYA")) {
            ARAMA_SONUCLARI.clear();
            String aranan = yazilan.toUpperCase().replace("İ", "I");
            
            if (aranan.contains("ELMAS")) aranan = "DIAMOND";
            else if (aranan.contains("ALTIN")) aranan = "GOLD";
            else if (aranan.contains("DEMIR")) aranan = "IRON";
            else if (aranan.contains("ZUMRUT")) aranan = "EMERALD";
            else if (aranan.contains("KOMUR")) aranan = "COAL";
            
            for (Material mat : Material.values()) {
                if (mat.name().contains(aranan) && !mat.isAir() && mat.isItem()) {
                    ARAMA_SONUCLARI.add(mat);
                    if (ARAMA_SONUCLARI.size() >= 45) break;
                }
            }

            if (ARAMA_SONUCLARI.isEmpty()) {
                p.sendMessage("§cAradığın eşya bulunamadı! (İngilizce deneyin: diamond, iron)");
                ADIM.remove(p.getUniqueId());
                return;
            }

            Inventory inv = Bukkit.createInventory(null, 54, "§8§l» §6§lEŞYA SEÇ §8§l«");
            int slot = 0;
            for (Material mat : ARAMA_SONUCLARI) {
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) { meta.setDisplayName("§e" + mat.name()); meta.setLore(Arrays.asList("§aSeçmek için tıkla!")); item.setItemMeta(meta); }
                inv.setItem(slot++, item);
            }
            ItemStack kapat = new ItemStack(Material.BARRIER);
            ItemMeta km = kapat.getItemMeta(); if (km != null) { km.setDisplayName("§cGeri Dön"); kapat.setItemMeta(km); }
            inv.setItem(49, kapat);
            
            p.openInventory(inv);
            ADIM.put(p.getUniqueId(), "ESYA_SECIM");

        } else if (adim.equals("MIKTAR")) {
            try {
                int miktar = Integer.parseInt(yazilan);
                if (miktar < 1) throw new NumberFormatException();
                MIKTAR.put(p.getUniqueId(), miktar);
                p.sendMessage("§aMiktar belirlendi: " + miktar);
                siparisOlusturMenuAc(p);
            } catch (NumberFormatException ex) {
                p.sendMessage("§cGeçersiz sayı! Lütfen 1 veya daha büyük bir sayı gir.");
            }
            ADIM.remove(p.getUniqueId());

        } else if (adim.equals("FIYAT")) {
            try {
                double fiyat = Double.parseDouble(yazilan);
                if (fiyat <= 0) throw new NumberFormatException();
                FIYAT.put(p.getUniqueId(), fiyat);
                p.sendMessage("§aFiyat belirlendi: " + fiyat);
                birimSecimMenuAc(p);
            } catch (NumberFormatException ex) {
                p.sendMessage("§cGeçersiz fiyat! Lütfen geçerli bir sayı gir.");
                ADIM.remove(p.getUniqueId());
            }
        }
    }

    private void birimSecimMenuAc(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8§l» §6§lBİRİM SEÇ §8§l«");
        ItemStack cam = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 0; i < 27; i++) inv.setItem(i, cam);

        ItemStack para = new ItemStack(Material.GOLD_INGOT);
        ItemMeta pMeta = para.getItemMeta();
        if (pMeta != null) { pMeta.setDisplayName("§6§lPARA"); pMeta.setLore(Arrays.asList("§7Parayla sipariş oluştur.")); para.setItemMeta(pMeta); }
        inv.setItem(11, para);

        ItemStack puan = new ItemStack(Material.DIAMOND);
        ItemMeta puMeta = puan.getItemMeta();
        if (puMeta != null) { puMeta.setDisplayName("§b§lDCRAFT PUANI"); puMeta.setLore(Arrays.asList("§7Puanla sipariş oluştur.")); puan.setItemMeta(puMeta); }
        inv.setItem(15, puan);

        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;

        if (title.equals("§8§l» §6§lORDER SİSTEMİ §8§l«")) {
            e.setCancelled(true);
            if (clicked.getType() == Material.BARRIER) p.closeInventory();
            else if (clicked.getType() == Material.WRITABLE_BOOK) siparisOlusturMenuAc(p);
            else if (clicked.getType() == Material.PLAYER_HEAD) anaMenuAc(p, "hepsi", true);
            else if (clicked.getType() == Material.HOPPER) filtreMenuAc(p);
            else if (clicked.hasItemMeta() && clicked.getItemMeta().getLore() != null) {
                for (String s : clicked.getItemMeta().getLore()) {
                    if (s.startsWith("§8ID: ")) { satinAl(p, s.replace("§8ID: ", "")); break; }
                }
            }
            return;
        }

        if (title.equals("§8§l» §6§lFİLTRELE §8§l«")) {
            e.setCancelled(true);
            if (clicked.getType() == Material.BARRIER) { anaMenuAc(p, "hepsi", false); return; }
            
            String filtre = "hepsi";
            if (clicked.getType() == Material.GRASS_BLOCK) filtre = "blok";
            else if (clicked.getType() == Material.DIAMOND_SWORD) filtre = "esya";
            else if (clicked.getType() == Material.GOLD_INGOT) filtre = "para";
            else if (clicked.getType() == Material.DIAMOND) filtre = "puan";
            else if (clicked.getType() == Material.COPPER_INGOT) filtre = "ucuz";
            else if (clicked.getType() == Material.GOLD_BLOCK) filtre = "pahali";
            
            anaMenuAc(p, filtre, false);
            return;
        }

        if (title.equals("§8§l» §6§lSİPARİŞ OLUŞTUR §8§l«")) {
            e.setCancelled(true);
            if (clicked.getType() == Material.DIAMOND) tabelaAc(p, "ESYA");
            else if (clicked.getType() == Material.PAPER) {
                if (!ESYA.containsKey(p.getUniqueId())) { p.sendMessage("§cÖnce eşya seçmelisin!"); return; }
                tabelaAc(p, "MIKTAR");
            }
            else if (clicked.getType() == Material.GOLD_INGOT) {
                if (!ESYA.containsKey(p.getUniqueId()) || !MIKTAR.containsKey(p.getUniqueId())) { p.sendMessage("§cÖnce eşya ve miktar seçmelisin!"); return; }
                tabelaAc(p, "FIYAT");
            }
            return;
        }

        if (title.equals("§8§l» §6§lEŞYA SEÇ §8§l«")) {
            e.setCancelled(true);
            if (clicked.getType() == Material.BARRIER) { siparisOlusturMenuAc(p); return; }
            if (clicked.hasItemMeta()) {
                ESYA.put(p.getUniqueId(), clicked.getType().name());
                p.sendMessage("§aEşya seçildi: " + clicked.getType().name());
                siparisOlusturMenuAc(p);
            }
            return;
        }

        if (title.equals("§8§l» §6§lBİRİM SEÇ §8§l«")) {
            e.setCancelled(true);
            if (clicked.getType() == Material.GOLD_INGOT || clicked.getType() == Material.DIAMOND) {
                String birim = clicked.getType() == Material.GOLD_INGOT ? "para" : "puan";
                siparisiKaydet(p, birim);
            }
            return;
        }
    }

    private void siparisiKaydet(Player p, String birim) {
        String esya = ESYA.get(p.getUniqueId());
        int miktar = MIKTAR.get(p.getUniqueId());
        double fiyat = FIYAT.get(p.getUniqueId());
        double toplam = fiyat * miktar;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(orderDosya);
        String id = String.valueOf(System.currentTimeMillis());
        yml.set("siparisler." + id + ".oyuncu", p.getName());
        yml.set("siparisler." + id + ".esya", esya);
        yml.set("siparisler." + id + ".miktar", miktar);
        yml.set("siparisler." + id + ".birim", birim);
        yml.set("siparisler." + id + ".birim_fiyat", fiyat);
        yml.set("siparisler." + id + ".toplam_fiyat", toplam);
        
        try { yml.save(orderDosya); } catch (IOException ignored) {}

        p.sendMessage("§a§l✔ §aSiparişin başarıyla Order'a eklendi!");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        
        ESYA.remove(p.getUniqueId());
        MIKTAR.remove(p.getUniqueId());
        FIYAT.remove(p.getUniqueId());
        ADIM.remove(p.getUniqueId());
        p.closeInventory();
    }

    private void satinAl(Player alici, String siparisId) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(orderDosya);
        String path = "siparisler." + siparisId;
        if (!yml.contains(path)) { alici.sendMessage("§cBu sipariş artık yok!"); return; }
        
        String saticiIsim = yml.getString(path + ".oyuncu");
        String esyaIsim = yml.getString(path + ".esya");
        int miktar = yml.getInt(path + ".miktar");
        double toplamFiyat = yml.getDouble(path + ".toplam_fiyat");
        String birim = yml.getString(path + ".birim");

        if (alici.getName().equals(saticiIsim)) { alici.sendMessage("§cKendi siparişini alamazsın!"); return; }

        if (birim.equals("puan")) {
            if (ppAPI == null) { alici.sendMessage("§cPlayerPoints yok!"); return; }
            try {
                int bakiye = (int) ppAPI.getClass().getMethod("look", UUID.class).invoke(ppAPI, alici.getUniqueId());
                if (bakiye < toplamFiyat) { alici.sendMessage("§cYetersiz Puan! Gerekli: §e" + toplamFiyat); return; }
                ppAPI.getClass().getMethod("take", UUID.class, int.class).invoke(ppAPI, alici.getUniqueId(), (int) toplamFiyat);
                
                Player satici = Bukkit.getPlayer(saticiIsim);
                if (satici != null) {
                    ppAPI.getClass().getMethod("give", UUID.class, int.class).invoke(ppAPI, satici.getUniqueId(), (int) toplamFiyat);
                    satici.sendMessage("§a[Order] §e" + alici.getName() + " §7siparişini aldı! §e+" + toplamFiyat + " Puan");
                } else {
                    ppAPI.getClass().getMethod("give", UUID.class, int.class).invoke(ppAPI, Bukkit.getOfflinePlayer(saticiIsim).getUniqueId(), (int) toplamFiyat);
                }
            } catch (Exception ex) { alici.sendMessage("§cPuan hatası!"); return; }
        } else {
            if (econ == null) { alici.sendMessage("§cVault yok!"); return; }
            if (econ.getBalance(alici) < toplamFiyat) { alici.sendMessage("§cYetersiz Para! Gerekli: §e" + toplamFiyat); return; }
            econ.withdrawPlayer(alici, toplamFiyat);
            
            Player satici = Bukkit.getPlayer(saticiIsim);
            if (satici != null) {
                econ.depositPlayer(satici, toplamFiyat);
                satici.sendMessage("§a[Order] §e" + alici.getName() + " §7siparişini aldı! §e+" + toplamFiyat + " Para");
            } else {
                econ.depositPlayer(Bukkit.getOfflinePlayer(saticiIsim), toplamFiyat);
            }
        }

        Material mat = Material.matchMaterial(esyaIsim);
        if (mat == null) mat = Material.PAPER;
        ItemStack esyaItem = new ItemStack(mat, miktar);
        HashMap<Integer, ItemStack> kalanlar = alici.getInventory().addItem(esyaItem);
        for (ItemStack kalan : kalanlar.values()) alici.getWorld().dropItemNaturally(alici.getLocation(), kalan);

        yml.set(path, null);
        try { yml.save(orderDosya); } catch (IOException ignored) {}
        alici.sendMessage("§a§l✔ §aSiparişi satın aldın!");
        alici.playSound(alici.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        anaMenuAc(alici, "hepsi", false);
    }
                    }
