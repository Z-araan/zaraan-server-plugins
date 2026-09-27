package cn.zaraan.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class ZaraanAI extends JavaPlugin {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("ZaraanAI 已启用，使用云端DeepSeek API");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "该命令只有玩家可以使用");
            return true;
        }
        if (args.length == 0) {
            p.sendMessage(ChatColor.YELLOW + "AI向导 » /ai <问题>");
            p.sendMessage(ChatColor.GRAY + "例: /ai 怎么赚钱");
            return true;
        }

        StringBuilder q = new StringBuilder();
        for (String a : args) q.append(a).append(' ');
        askAI(p, q.toString().trim());
        return true;
    }

    private void askAI(Player p, String question) {
        String apiKey = getConfig().getString("api-key", "");
        String apiUrl = getConfig().getString("api-url", "https://api.deepseek.com/v1/chat/completions");
        String model = getConfig().getString("model", "deepseek-chat");

        if (apiKey.isEmpty()) {
            p.sendMessage(ChatColor.RED + "AI API密钥未配置，请联系管理员");
            getLogger().warning("DeepSeek API Key 未配置");
            return;
        }

        p.sendMessage(ChatColor.GRAY + "[AI] 正在思考...");

        String systemPrompt = "你是Minecraft服务器「zaraan星火之域」的AI助手，名字叫小星。用简短中文回答（不超过100字）。"
                + "服务器常用命令: /menu /mtpa /mhome /mpay /rtp /team /tc /msg /home /sethome /tpa /back /spawn /psi /quests /crates /shop /sellall /pwarp /zaraancore buy"
                + "经济: 签到每天约120金币,商店买卖,传送费10-20金币,死亡保护100金币。"
                + "规则: 禁止偷窃和破坏他人建筑。只回答服务器和Minecraft相关问题，无关话题礼貌拒绝。";

        String requestBody = buildRequestBody(systemPrompt, question, model);

        HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() == 200) {
                        try {
                            String reply = extractContent(resp.body());
                            if (reply != null && !reply.isBlank()) {
                                for (String line : reply.split("\n")) {
                                    if (!line.isBlank()) {
                                        p.sendMessage(ChatColor.YELLOW + "[AI] " + line);
                                    }
                                }
                            } else {
                                p.sendMessage(ChatColor.RED + "[AI] AI回复为空");
                            }
                        } catch (Exception ex) {
                            p.sendMessage(ChatColor.RED + "[AI] 解析回复失败");
                            getLogger().log(Level.WARNING, "AI解析失败", ex);
                        }
                    } else {
                        p.sendMessage(ChatColor.RED + "[AI] API请求失败: " + resp.statusCode());
                        getLogger().warning("AI API错误: " + resp.statusCode() + " " + resp.body());
                    }
                })
                .exceptionally(ex -> {
                    p.sendMessage(ChatColor.RED + "[AI] 请求失败: " + ex.getMessage());
                    getLogger().log(Level.WARNING, "AI请求异常", ex);
                    return null;
                });
    }

    private String buildRequestBody(String system, String user, String model) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("messages", new Object[]{
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        });
        payload.put("temperature", 0.7);
        payload.put("max_tokens", 500);

        com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(
                new com.google.gson.GsonBuilder().create().toJson(payload)).getAsJsonObject();

        return obj.toString();
    }

    private String extractContent(String responseBody) {
        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(responseBody).getAsJsonObject();
            if (obj.has("choices") && !obj.get("choices").getAsJsonArray().isEmpty()) {
                com.google.gson.JsonObject choice = obj.get("choices").getAsJsonArray().get(0).getAsJsonObject();
                if (choice.has("message") && choice.get("message").getAsJsonObject().has("content")) {
                    return choice.get("message").getAsJsonObject().get("content").getAsString();
                }
            }
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "解析AI回复失败", ex);
        }
        return null;
    }
}
