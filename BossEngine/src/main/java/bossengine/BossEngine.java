package com.bossengine;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class BossEngine extends JavaPlugin implements CommandExecutor {

    private static BossEngine instance;
    private NamespacedKey bossKey;
    private YamlConfiguration config;
    private final Map<UUID, BossBar> activeBars = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        this.bossKey = new NamespacedKey(this, "engine_boss_tag");

        saveDefaultConfig();
        reloadBossData();

        getServer().getPluginManager().registerEvents(new BossListener(this), this);
        getCommand("boss").setExecutor(this);
        getLogger().info("[BossEngine 26.3] Khoi dong thanh cong - Da san sang Model & Animation!");
    }

    @Override
    public void onDisable() {
        activeBars.values().forEach(BossBar::removeAll);
        activeBars.clear();
    }

    public void reloadBossData() {
        File f = new File(getDataFolder(), "bosses.yml");
        if (!f.exists()) saveResource("bosses.yml", false);
        config = YamlConfiguration.loadConfiguration(f);
    }

    // --- HỆ THỐNG PHÁT ANIMATION QUA MODEL ENGINE ---
    public void playAnimation(LivingEntity entity, String animName) {
        if (animName == null || animName.isEmpty()) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) return;

        try {
            ModeledEntity modeled = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
            if (modeled != null) {
                for (ActiveModel model : modeled.getModels().values()) {
                    var handler = model.getAnimationHandler();
                    if (handler != null) {
                        // Phát hoạt ảnh: lerpIn 0.2s, lerpOut 0.2s, tốc độ 1.0, ghi đè (force)
                        handler.playAnimation(animName, 0.2, 0.2, 1.0, true);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    // --- LOGIC TRIỆU HỒI BOSS ---
    public void spawnCustomBoss(String id, Location loc) {
        String path = "bosses." + id + ".";
        if (!config.contains(path + "name")) return;

        EntityType type = EntityType.valueOf(config.getString(path + "entity-type", "ZOMBIE"));
        LivingEntity mob = (LivingEntity) loc.getWorld().spawnEntity(loc, type);

        // Đặt tên
        String name = ChatColor.translateAlternateColorCodes('&', config.getString(path + "name"));
        mob.setCustomName(name);
        mob.setCustomNameVisible(true);

        // Đặt chỉ số Máu
        double maxHp = config.getDouble(path + "max-health", 1000.0);
        mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHp);
        mob.setHealth(maxHp);

        // Đánh dấu thẻ Tag an toàn (PDC)
        mob.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, id);

        // Gắn Model 3D từ Model Engine
        String modelId = config.getString(path + "model-id", "");
        if (!modelId.isEmpty() && Bukkit.getPluginManager().isPluginEnabled("ModelEngine")) {
            try {
                ModeledEntity modeled = ModelEngineAPI.getOrCreateModeledEntity(mob);
                ActiveModel activeModel = ModelEngineAPI.createActiveModel(modelId);
                modeled.addModel(activeModel, true);
            } catch (Exception ignored) {}
        }

        // Chạy hoạt ảnh xuất hiện (Spawn Animation)
        String spawnAnim = config.getString(path + "animations.spawn", "");
        Bukkit.getScheduler().runTaskLater(this, () -> playAnimation(mob, spawnAnim), 5L);

        // Khởi tạo BossBar
        BarColor color = BarColor.valueOf(config.getString(path + "bossbar.color", "PURPLE"));
        BarStyle style = BarStyle.valueOf(config.getString(path + "bossbar.style", "SOLID"));
        BossBar bar = Bukkit.createBossBar(name, color, style);
        bar.setProgress(1.0);
        activeBars.put(mob.getUniqueId(), bar);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("boss.admin")) return true;

        if (args.length >= 2 && args[0].equalsIgnoreCase("spawn")) {
            if (sender instanceof Player p) {
                spawnCustomBoss(args[1], p.getLocation());
                p.sendMessage(ChatColor.GREEN + "Triệu hồi thành công Boss: " + args[1]);
            }
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            reloadBossData();
            sender.sendMessage(ChatColor.GREEN + "Đã tải lại cấu hình bosses.yml!");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Cách dùng: /boss spawn <id> | /boss reload");
        return true;
    }

    public static BossEngine getInstance() { return instance; }
    public NamespacedKey getBossKey() { return bossKey; }
    public YamlConfiguration getBossConfig() { return config; }
    public Map<UUID, BossBar> getActiveBars() { return activeBars; }
}