package com.bossengine;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class BossListener implements Listener {

    private final BossEngine plugin;
    private final Random random = new Random();

    public BossListener(BossEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        // --- 1. KHI BOSS TẤN CÔNG (Chạy Animation Đánh & Sát thương tùy chỉnh) ---
        if (event.getDamager() instanceof LivingEntity boss && boss.getPersistentDataContainer().has(plugin.getBossKey(), PersistentDataType.STRING)) {
            String id = boss.getPersistentDataContainer().get(plugin.getBossKey(), PersistentDataType.STRING);
            String path = "bosses." + id + ".";

            // Áp sát thương tùy chỉnh
            double dmg = plugin.getBossConfig().getDouble(path + "damage", event.getDamage());
            event.setDamage(dmg);

            // Chạy Animation đánh thường (Attack Animation)
            String attackAnim = plugin.getBossConfig().getString(path + "animations.attack", "");
            plugin.playAnimation(boss, attackAnim);
        }

        // --- 2. KHI NGƯỜI CHƠI ĐÁNH BOSS (Phòng thủ, Thoại, Animation Skill) ---
        if (event.getEntity() instanceof LivingEntity boss && boss.getPersistentDataContainer().has(plugin.getBossKey(), PersistentDataType.STRING)) {
            String id = boss.getPersistentDataContainer().get(plugin.getBossKey(), PersistentDataType.STRING);
            String path = "bosses." + id + ".";

            // Giảm sát thương theo % thủ (Defense)
            double def = plugin.getBossConfig().getDouble(path + "defense", 0.0);
            double finalDmg = event.getDamage() * (1.0 - (def / 100.0));
            event.setDamage(Math.max(1.0, finalDmg));

            // Cập nhật thanh máu BossBar
            BossBar bar = plugin.getActiveBars().get(boss.getUniqueId());
            if (bar != null) {
                double cur = Math.max(0, boss.getHealth() - event.getFinalDamage());
                double max = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                bar.setProgress(Math.max(0.0, Math.min(1.0, cur / max)));
                
                boss.getWorld().getPlayers().forEach(p -> {
                    if (p.getLocation().distance(boss.getLocation()) <= 30) bar.addPlayer(p);
                    else bar.removePlayer(p);
                });
            }

            // Câu thoại khi bị đánh (Tỉ lệ 20%)
            if (event.getDamager() instanceof Player player && random.nextInt(100) < 20) {
                List<String> quotes = plugin.getBossConfig().getStringList(path + "dialogues.on-hit");
                if (!quotes.isEmpty()) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', quotes.get(random.nextInt(quotes.size()))));
                }
            }

            // --- TUYỆT CHIÊU KÈM ANIMATION KHI XUỐNG 50% MÁU ---
            double maxHp = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            if (boss.getHealth() <= (maxHp * 0.5) && !boss.hasMetadata("skill_executed")) {
                boss.setMetadata("skill_executed", new FixedMetadataValue(plugin, true));

                // 1. Chạy Animation gồng chiêu (Skill/Cast Animation)
                String skillAnim = plugin.getBossConfig().getString(path + "animations.skill", "");
                plugin.playAnimation(boss, skillAnim);

                // 2. Thoại kích hoạt chiêu
                List<String> skillQuotes = plugin.getBossConfig().getStringList(path + "dialogues.on-skill");
                if (!skillQuotes.isEmpty() && event.getDamager() instanceof Player p) {
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', skillQuotes.get(0)));
                }

                // 3. Thực thi lệnh sau 1 giây (để Animation kịp diễn ra)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    String cmd = plugin.getBossConfig().getString(path + "skills.command", "");
                    if (!cmd.isEmpty()) {
                        String parsedCmd = cmd.replace("%boss_uuid%", boss.getUniqueId().toString());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd);
                    }
                }, 20L);
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity boss = event.getEntity();
        if (!boss.getPersistentDataContainer().has(plugin.getBossKey(), PersistentDataType.STRING)) return;

        String id = boss.getPersistentDataContainer().get(plugin.getBossKey(), PersistentDataType.STRING);
        BossBar bar = plugin.getActiveBars().remove(boss.getUniqueId());
        if (bar != null) bar.removeAll();

        event.getDrops().clear();
        String dropPath = "bosses." + id + ".drops";
        
        for (var map : plugin.getBossConfig().getMapList(dropPath)) {
            double rate = Double.parseDouble(map.get("chance").toString());
            if (random.nextDouble() * 100 <= rate) {
                Material mat = Material.valueOf(map.get("item").toString());
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();

                if (map.containsKey("name")) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', map.get("name").toString()));
                }
                if (map.containsKey("lore")) {
                    List<String> lore = (List<String>) map.get("lore");
                    meta.setLore(lore.stream().map(s -> ChatColor.translateAlternateColorCodes('&', s)).toList());
                }
                item.setItemMeta(meta);
                event.getDrops().add(item);
            }
        }
    }
}