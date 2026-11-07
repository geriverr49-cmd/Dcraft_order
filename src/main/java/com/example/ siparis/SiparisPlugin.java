package com.example.siparis;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
    private final Map<UUID, String> TESLIMAT_SIPARIS_ID = new HashMap<>();

    private File orderDosya;
    private File bekleyenDosya; // Çevrimdışı eşyalar için
    private Economy econ = null;
    private Object ppAPI = null;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        orderDosya = new File(getDataFolder(), "order.yml");
        bekleyenDosya = new File(getDataFolder(), "bekleyen-esyalar.yml");
        
        if (!orderDosya.exists()) { try { orderDosya.createNewFile(); } catch (IOException e) { e.printStackTrace(); } }
        if (!bekleyenDosya.exists()) { try { bekleyenDosya.createNewFile(); } catch (IOException e) { e.printStackTrace(); } }

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
        getLogger().info("Premium Order Sistemi (Zirhlı Sürüm) aktif!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1.2f);
        p.sendTitle("§6§lORDER", "§7Premium Pazar Sistemi", 10, 40, 10);
        
        anaMenuAc(p, "hepsi", false);
        return true;
    }

    // --- ÇEVRİMDIŞI EŞYA TESLİMATI ---
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(bekleyenDosya);
        if (yml.contains(p.getUniqueId().toString())) {
            ConfigurationSection sec = yml.getConfigurationSection(p.getUniqueId().toString());
            for (String key : sec.getKeys(false)) {
                String esyaStr = sec.getString(key);
                Material mat = Material.matchMaterial(esyaStr.split(":")[0]);
                int miktar = Integer.parseInt(esyaStr.split(":")[1]);
                if (mat != null) {
                    HashMap<Integer, ItemStack> kalanlar = p.getInventory().addItem(new ItemStack(mat, miktar));
                    for (ItemStack kalan : kalanlar.values()) p.getWorld().dropItemNaturally(p.getLocation(), kalan);
                }
            }
            yml.set(p.getUniqueId().toString(), null);
            try { yml.save(bekleyenDosya); } catch (IOException ex) { ex.printStackTrace(); }
            p.sendMessage("§a[Order] §eÇevrimdışıyken gelen sipariş eşyaların envanterine eklendi!");
        }
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
                    meta.setDisplayName("§e§l" + mat.name() + " §7x" + miktar);
                    
                    List<String> lore = new ArrayList<>();
                    lore.add("§8§m-----------------------");
                    lore.add("§7Siparişi Veren: §f" + oyuncu);
                    lore.add("§7Toplam Fiyat: §a" + toplam + " " + birim.toUpperCase());
                    lore.add("§8§m-----------------------");
                    
                    if (kendiSiparislerim) {
                        lore.add("§cSiparişi iptal etmek için tıkla!");
                        lore.add("§7(Paran/Puanın iade edilir)");
                    } else {
                        lore.add("§aEşya teslim etmek için tıkla!");
                    }
                    lore.add("§8ID: " + id);
                    meta.setLore(lore);
                    
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
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
            kafaMeta.setLore(Arrays.asList("§7Sadece senin açtığın siparişleri gör.", "§7Ve iptal et."));
            kafa.setItemMeta(kafaMeta);
        }
        inv.setItem(45, kafa);

        ItemStack ver = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta verMeta = ver.getItemMeta();
        if (verMeta != null) {
            verMeta.setDisplayName("§a§lSİPARİŞ VER");
            verMeta.setLore(Arrays.asList("§7Yeni bir sipariş oluştur.", "§eTıkla ve ihtiyacın olanı iste!"));
            verMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            verMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
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
            hMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            hMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            huni.setItemMeta(hMeta);
        }
        inv.setItem(49, huni);

        ItemStack kapat = new ItemStack(Material.BARRIER);
        ItemMeta kapatMeta = kapat.getItemMeta();
        if (kapatMeta != null) { kapatMeta.setDisplayName("§c§lKapat"); kapat.setItemMeta(kapatMeta); }
        inv.setItem(53, kapat);

        p.openInventory(inv);
    }

    private void teslimatMenuAc(Player p, String siparisId) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(orderDosya);
        String path = "siparisler." + siparisId;
        if (!yml.contains(path)) { p.sendMessage("§cSipariş bulunamadı!"); return; }

        String esyaIsim = yml.getString(path + ".esya");
        int miktar = yml.getInt(path + ".miktar");
        double birimFiyat = yml.getDouble(path + ".birim_fiyat");
        String birim = yml.getString(path + ".birim");

        TESLIMAT_SIPARIS_ID.put(p.getUniqueId(), siparisId);
        
        Inventory inv = Bukkit.createInventory(null, 54, "§8§l» §6§lTESLİMAT: " + esyaIsim + " §8§l«");
        
        ItemStack cam = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta cm = cam.getItemMeta(); if (cm != null) { cm.setDisplayName(" "); cam.setItemMeta(cm); }
        for (int i = 45; i < 54; i++) inv.setItem(i, cam);

        ItemStack onay = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta oMeta = onay.getItemMeta();
        if (oMeta != null) {
            oMeta.setDisplayName("§a§lONAYLA VE TESLİM ET");
            oMeta.setLore(Arrays.asList(
                "§7Kalan Miktar: §f" + miktar,
                "§7Birim Fiyat: §a" + birimFiyat + " " + birim.toUpperCase(),
                "§7Bıraktığın eşyaları teslim edip",
                "§7paranı/puanını alırsın."
            ));
            oMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            oMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            onay.setItemMeta(oMeta);
        }
        inv.setItem(49, onay);

        ItemStack iptal = new ItemStack(Material.BARRIER);
        ItemMeta iMeta = iptal.getItemMeta();
        if (iMeta != null) { iMeta.setDisplayName("§c§lİPTAL"); iMeta.setLore(Arrays.asList("§7Bıraktığın eşyalar iade edilir.")); iptal.setItemMeta(iMeta); }
        inv.setItem(53, iptal);

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
        p.sendMessage("§eLütfen envanterinizin üst kısmına §c" + esyaIsim + " §eismini bırakıp Onayla'ya basın!");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!e.getView().getTitle().contains("§8§l» §6§lTESLİMAT:")) return;

        Inventory inv = e.getInventory();
        for (int i = 0; i < 45; i++) { 
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR && item.getType() != Material.BLACK_STAINED_GLASS_PANE) {
                HashMap<Integer, ItemStack> kalanlar = p.getInventory().addItem(item);
                for (ItemStack kalan : kalanlar.values()) p.getWorld().dropItemNaturally(p.getLocation(), kalan);
                inv.setItem(i, null); 
            }
        }
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
        p.playSound(p.getLocation(), Sound.BLOCK_WOOD_PLACE, 1f, 1f);
    }

    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        Player p = e.getPlayer();
        if (!ADIM.containsKey(p.getUniqueId())) return;

        String yazilan = e.getLine(0);
        e.setCancelled(true);

        Location tabelaLoc = TABELA_KONUM.remove(p.getUniqueId());
        if (tabelaLoc != null) tabelaLoc.getBlock().setType(Material.AIR);

        if (yazilan == null || yazilan.isEmpty() || yazilan.equalsIgnoreCase("iptal")) {
            p.sendMessage("§cİşlem iptal edildi.");
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
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
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
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                siparisOlusturMenuAc(p);
            } catch (NumberFormatException ex) {
                p.sendMessage("§cGeçersiz sayı! Lütfen 1 veya daha büyük bir sayı gir.");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            ADIM.remove(p.getUniqueId());

        } else if (adim.equals("FIYAT")) {
            try {
                double fiyat = Double.parseDouble(yazilan);
                if (fiyat <= 0) throw new NumberFormatException();
                FIYAT.put(p.getUniqueId(), fiyat);
                p.sendMessage("§aFiyat belirlendi: " + fiyat);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                birimSecimMenuAc(p);
            } catch (NumberFormatException ex) {
                p.sendMessage("§cGeçersiz fiyat! Lütfen geçerli bir sayı gir.");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
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

        // --- TESLİMAT MENÜSÜ TIKLAMALARI ---
        if (title.contains("§8§l» §6§lTESLİMAT:")) {
            // Alt envanteri (oyuncunun kendi envanteri) serbest bırak
            if (e.getClickedInventory() == null || e.getClickedInventory().equals(e.getView().getBottomInventory())) {
                return; 
            }
            
            if (clicked == null) { e.setCancelled(true); return; }

            // Sadece butonlara tıklamayı engelle, eşya koymaya izin ver
            if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE || 
                clicked.getType() == Material.LIME_STAINED_GLASS_PANE || 
                clicked.getType() == Material.BARRIER) {
                e.setCancelled(true);
            } else {
                // Eğer menüdeki eşyaya tıklıyorsa (geri almak için)
                return; 
            }

            if (clicked.getType() == Material.BARRIER) {
                p.playSound(p.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1f, 1f);
                p.closeInventory(); 
            } 
            else if (clicked.getType() == Material.LIME_STAINED_GLASS_PANE) {
                siparisiTeslimEt(p, e.getView().getTopInventory());
            }
            return;
        }

        if (clicked == null) return;
        
        // --- ANA MENÜ ---
        if (title.equals("§8§l» §6§lORDER SİSTEMİ §8§l«")) {
            e.setCancelled(true);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            
            if (clicked.getType() == Material.BARRIER) p.closeInventory();
            else if (clicked.getType() == Material.WRITABLE_BOOK) siparisOlusturMenuAc(p);
            else if (clicked.getType() == Material.PLAYER_HEAD) anaMenuAc(p, "hepsi", true);
            else if (clicked.getType() == Material.HOPPER) filtreMenuAc(p);
            else if (clicked.hasItemMeta() && clicked.getItemMeta().getLore() != null) {
                for (String s : clicked.getItemMeta().getLore()) {
                    if (s.startsWith("§8ID: ")) { 
                        String id = s.replace("§8ID: ", "");
                        // Eğer "Senin Siparişlerin" menüsündeyse İPTAL et, değilse TESLİM ET.
                        if (e.getView().getTitle().contains("ORDER SİSTEMİ") && clicked.getItemMeta().getLore().contains("§cSiparişi iptal etmek için tıkla!")) {
                            siparisiIptalEt(p, id);
                        } else {
                            teslimatMenuAc(p, id); 
                        }
                        break; 
                    }
                }
            }
            return;
        }

        // --- FİLTRE MENÜSÜ ---
        if (title.equals("§8§l» §6§lFİLTRELE §8§l«")) {
            e.setCancelled(true);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
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

        // --- SİPARİŞ OLUŞTUR ---
        if (title.equals("§8§l» §6§lSİPARİŞ OLUŞTUR §8§l«")) {
            e.setCancelled(true);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            if (clicked.getType() == Material.DIAMOND) tabelaAc(p, "ESYA");
            else if (clicked.getType() == Material.PAPER) {
                if (!ESYA.containsKey(p.getUniqueId())) { p.sendMessage("§cÖnce eşya seçmelisin!"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                tabelaAc(p, "MIKTAR");
            }
            else if (clicked.getType() == Material.GOLD_INGOT) {
                if (!ESYA.containsKey(p.getUniqueId()) || !MIKTAR.containsKey(p.getUniqueId())) { p.sendMessage("§cÖnce eşya ve miktar seçmelisin!"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                tabelaAc(p, "FIYAT");
            }
            return;
        }

        // --- EŞYA SEÇ ---
        if (title.equals("§8§l» §6§lEŞYA SEÇ §8§l«")) {
            e.setCancelled(true);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            if (clicked.getType() == Material.BARRIER) { siparisOlusturMenuAc(p); return; }
            if (clicked.hasItemMeta()) {
                ESYA.put(p.getUniqueId(), clicked.getType().name());
                p.sendMessage("§aEşya seçildi: " + clicked.getType().name());
                siparisOlusturMenuAc(p);
            }
            return;
        }

        // --- BİRİM SEÇ ---
        if (title.equals("§8§l» §6§lBİRİM SEÇ §8§l«")) {
            e.setCancelled(true);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            if (clicked.getType() == Material.GOLD_INGOT || clicked.getType() == Material.DIAMOND) {
                String birim = clicked.getType() == Material.GOLD_INGOT ? "para" : "puan";
                siparisiKaydet(p, birim);
            }
            return;
        }
    }

    private void siparisiIptalEt(Player p, String siparisId) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(orderDosya);
        String path = "siparisler." + siparisId;
        if (!yml.contains(path)) return;
        
        if (!yml.getString(path + ".oyuncu").equals(p.getName())) {
            p.sendMessage("§cBu senin siparişin değil!"); return;
        }

        double toplam = yml.getDouble(path + ".toplam_fiyat");
        String birim = yml.getString(path + ".birim");

        // Parayı İade Et
        if (birim.equals("para")) {
            if (econ != null) econ.depositPlayer(p, toplam);
        } else {
            if (ppAPI != null) {
                try { ppAPI.getClass().getMethod("give", UUID.class, int.class).invoke(ppAPI, p.getUniqueId(), (int) toplam); } catch (Exception ignored) {}
            }
        }

        yml.set(path, null);
        try { yml.save(orderDosya); } catch (IOException ignored) {}

        p.sendMessage("§a§l✔ §aSipariş iptal edildi ve §e" + toplam + " " + birim + " §aiade edildi!");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
        p.closeInventory();
        anaMenuAc(p, "hepsi", true);
    }

    private void siparisiKaydet(Player p, String birim) {
        String esya = ESYA.get(p.getUniqueId());
        int miktar = MIKTAR.get(p.getUniqueId());
        double fiyat = FIYAT.get(p.getUniqueId());
        double toplam = fiyat * miktar;

        if (birim.equals("para")) {
            if (econ == null) { p.sendMessage("§cEkonomi (Vault) bulunamadı!"); return; }
            if (econ.getBalance(p) < toplam) {
                p.sendMessage("§cYetersiz Para! Gerekli: §e" + toplam);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                p.closeInventory();
                return;
            }
            econ.withdrawPlayer(p, toplam);
        } else {
            if (ppAPI == null) { p.sendMessage("§cPlayerPoints bulunamadı!"); return; }
            try {
                int bakiye = (int) ppAPI.getClass().getMethod("look", UUID.class).invoke(ppAPI, p.getUniqueId());
                if (bakiye < toplam) {
                    p.sendMessage("§cYetersiz Dcraft Puanı! Gerekli: §e" + toplam);
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    p.closeInventory();
                    return;
                }
                ppAPI.getClass().getMethod("take", UUID.class, int.class).invoke(ppAPI, p.getUniqueId(), (int) toplam);
            } catch (Exception ex) { p.sendMessage("§cPuan kontrolünde hata!"); return; }
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(orderDosya);
        String id = String.valueOf(System.currentTimeMillis());
        yml.set("siparisler." + id + ".oyuncu", p.getName());
        yml.set("siparisler." + id + ".esya", esya);
        yml.set("siparisler." + id + ".miktar", miktar);
        yml.set("siparisler." + id + ".birim", birim);
        yml.set("siparisler." + id + ".birim_fiyat", fiyat);
        yml.set("siparisler." + id + ".toplam_fiyat", toplam);
        
        try { yml.save(orderDosya); } catch (IOException ignored) {}

        p.sendMessage("§a§l✔ §aSiparişin başarıyla Order'a eklendi! (Ödeme yapıldı)");
        
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        p.sendTitle("§a§lSİPARİŞ VERİLDİ", "§7Pazara başarıyla eklendi!", 10, 50, 10);
        
        for (int i = 0; i < 20; i++) {
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 1, 0.5, 0.5, 0.5, 0);
        }
        
        ESYA.remove(p.getUniqueId());
        MIKTAR.remove(p.getUniqueId());
        FIYAT.remove(p.getUniqueId());
        ADIM.remove(p.getUniqueId());
        p.closeInventory();
        anaMenuAc(p, "hepsi", false);
    }

    private void siparisiTeslimEt(Player islemYapan, Inventory teslimatEnv) {
        String siparisId = TESLIMAT_SIPARIS_ID.remove(islemYapan.getUniqueId());
        if (siparisId == null) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(orderDosya);
        String path = "siparisler." + siparisId;
        if (!yml.contains(path)) { islemYapan.sendMessage("§cSipariş bulunamadı!"); islemYapan.closeInventory(); return; }
        
        String siparisSahibiIsim = yml.getString(path + ".oyuncu");
        
        // KENDİ SİPARİŞİNİ TESLİM ETMEYİ ENGELLE
        if (islemYapan.getName().equals(siparisSahibiIsim)) {
            islemYapan.sendMessage("§cKendi siparişini teslim edemezsin!");
            islemYapan.closeInventory();
            return;
        }

        String esyaIsim = yml.getString(path + ".esya");
        int kalanMiktar = yml.getInt(path + ".miktar");
        double birimFiyat = yml.getDouble(path + ".birim_fiyat");
        String birim = yml.getString(path + ".birim");

        Material hedefMat = Material.matchMaterial(esyaIsim);
        if (hedefMat == null) hedefMat = Material.PAPER;

        int teslimEdilenMiktar = 0;
        for (int i = 0; i < 45; i++) {
            ItemStack item = teslimatEnv.getItem(i);
            if (item != null && item.getType() == hedefMat) {
                teslimEdilenMiktar += item.getAmount();
                teslimatEnv.setItem(i, null);
            } else if (item != null && item.getType() != Material.AIR) {
                islemYapan.getInventory().addItem(item);
                teslimatEnv.setItem(i, null);
            }
        }

        if (teslimEdilenMiktar <= 0) {
            islemYapan.sendMessage("§cHiç doğru eşya bırakmadın! İşlem iptal edildi.");
            islemYapan.playSound(islemYapan.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            islemYapan.closeInventory();
            return;
        }

        if (teslimEdilenMiktar > kalanMiktar) {
            teslimEdilenMiktar = kalanMiktar;
            int kalanFazla = 0;
            for (int i = 0; i < 45; i++) {
                ItemStack item = teslimatEnv.getItem(i);
                if (item != null && item.getType() == hedefMat) {
                    kalanFazla += item.getAmount();
                    teslimatEnv.setItem(i, null);
                }
            }
            if (kalanFazla > 0) {
                HashMap<Integer, ItemStack> kalanlar = islemYapan.getInventory().addItem(new ItemStack(hedefMat, kalanFazla));
                for (ItemStack kalan : kalanlar.values()) islemYapan.getWorld().dropItemNaturally(islemYapan.getLocation(), kalan);
            }
        }

        double odenecekPara = teslimEdilenMiktar * birimFiyat;

        if (birim.equals("puan")) {
            if (ppAPI != null) {
                try { ppAPI.getClass().getMethod("give", UUID.class, int.class).invoke(ppAPI, islemYapan.getUniqueId(), (int) odenecekPara); } catch (Exception ignored) {}
            }
        } else {
            if (econ != null) econ.depositPlayer(islemYapan, odenecekPara);
        }

        // EŞYAYI SİPARİŞ SAHİBİNE GÖNDER (Envanter doluysa yere düşür veya bekleyenlere at)
        Player siparisSahibi = Bukkit.getPlayer(siparisSahibiIsim);
        ItemStack verilecekEsya = new ItemStack(hedefMat, teslimEdilenMiktar);
        
        if (siparisSahibi != null && siparisSahibi.isOnline()) {
            HashMap<Integer, ItemStack> kalanlar = siparisSahibi.getInventory().addItem(verilecekEsya);
            for (ItemStack kalan : kalanlar.values()) siparisSahibi.getWorld().dropItemNaturally(siparisSahibi.getLocation(), kalan);
            siparisSahibi.sendMessage("§a[Order] §e" + islemYapan.getName() + " §7sana §e" + teslimEdilenMiktar + " " + hedefMat.name() + " §7gönderdi!");
            siparisSahibi.playSound(siparisSahibi.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        } else {
            // Çevrimdışıysa bekleyen dosyasına kaydet
            YamlConfiguration bekleyenYml = YamlConfiguration.loadConfiguration(bekleyenDosya);
            String uuidStr = Bukkit.getOfflinePlayer(siparisSahibiIsim).getUniqueId().toString();
            bekleyenYml.set(uuidStr + "." + System.currentTimeMillis(), hedefMat.name() + ":" + teslimEdilenMiktar);
            try { bekleyenYml.save(bekleyenDosya); } catch (IOException ignored) {}
            islemYapan.sendMessage("§eSipariş sahibi çevrimdışı, eşya güvenli bir şekilde bekletiliyor.");
        }

        islemYapan.sendMessage("§a§l✔ §a" + teslimEdilenMiktar + " adet eşya teslim ettin ve §e" + odenecekPara + " " + birim + " §aalındı!");
        
        islemYapan.playSound(islemYapan.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        islemYapan.sendTitle("§a§lTESLİMAT BAŞARILI", "§7Para/Puan hesabına yattı!", 10, 50, 10);
        
        for (int i = 0; i < 30; i++) {
            islemYapan.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, islemYapan.getLocation().add(0, 1, 0), 1, 0.5, 0.5, 0.5, 0.1);
        }
        
        int yeniKalan = kalanMiktar - teslimEdilenMiktar;
        if (yeniKalan <= 0) {
            yml.set(path, null);
        } else {
            yml.set(path + ".miktar", yeniKalan);
            yml.set(path + ".toplam_fiyat", yeniKalan * birimFiyat);
        }
        try { yml.save(orderDosya); } catch (IOException ignored) {}
        
        islemYapan.closeInventory();
        anaMenuAc(islemYapan, "hepsi", false);
    }
            }
