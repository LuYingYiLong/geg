package io.github.luyingyilong.geg.money;

import java.io.IOException;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CoinAuditService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Logger LOGGER = LoggerFactory.getLogger(CoinAuditService.class);
    private static final Map<MinecraftServer, Integer> WRITTEN = new WeakHashMap<>();

    private CoinAuditService() {
    }

    public static void flush(MinecraftServer server) {
        List<CoinAuditEntry> entries = MoneySavedData.get(server).audit();
        Integer last = WRITTEN.get(server);
        try {
            Path folder = folder(server).resolve("audit");
            Files.createDirectories(folder);
            if (last == null) {
                try (Stream<Path> files = Files.list(folder)) {
                    for (Path path : files.filter(path -> path.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}\\.jsonl")).toList()) {
                        Files.delete(path);
                    }
                }
                Map<LocalDate, List<String>> grouped = new HashMap<>();
                for (CoinAuditEntry entry : entries) {
                    grouped.computeIfAbsent(date(entry.time()), ignored -> new ArrayList<>()).add(json(entry));
                }
                for (Map.Entry<LocalDate, List<String>> group : grouped.entrySet()) {
                    Files.write(folder.resolve(group.getKey() + ".jsonl"), group.getValue(), StandardCharsets.UTF_8);
                }
            } else {
                for (int index = last; index < entries.size(); index++) {
                    CoinAuditEntry entry = entries.get(index);
                    Files.writeString(folder.resolve(date(entry.time()) + ".jsonl"), json(entry) + "\n",
                            StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            }
            WRITTEN.put(server, entries.size());
        } catch (IOException exception) {
            WRITTEN.remove(server);
            LOGGER.error("Failed to write GEG coin audit mirror", exception);
        }
    }

    public static Path export(MinecraftServer server, LocalDate day) throws IOException {
        Path folder = folder(server).resolve("reports");
        Files.createDirectories(folder);
        Path output = folder.resolve(day + ".csv");
        List<String> rows = new ArrayList<>();
        rows.add("sequence,time,reason,currency,from,to,amount_minor_units,reference");
        for (CoinAuditEntry entry : MoneySavedData.get(server).audit()) {
            if (date(entry.time()).equals(day)) {
                rows.add(entry.sequence() + "," + Instant.ofEpochMilli(entry.time()) + ","
                        + csv(entry.reason()) + "," + csv(entry.currency()) + ","
                        + csv(entry.from()) + "," + csv(entry.to()) + ","
                        + entry.amount() + "," + csv(entry.reference()));
            }
        }
        Files.write(output, rows, StandardCharsets.UTF_8);
        return output;
    }

    public static Summary summary(MinecraftServer server, int days, UUID player) {
        LocalDate earliest = LocalDate.now(ZONE).minusDays(days - 1L);
        BigInteger minted = BigInteger.ZERO;
        BigInteger sunk = BigInteger.ZERO;
        BigInteger transferred = BigInteger.ZERO;
        BigInteger opening = BigInteger.ZERO;
        int count = 0;
        String wallet = player == null ? null : MoneyService.wallet(player);
        String escrow = player == null ? null : "escrow:" + player + ":";
        String pending = player == null ? null : "pending:" + player + ":";
        String questPending = player == null ? null : "quest_pending:" + player + ":";
        for (CoinAuditEntry entry : MoneySavedData.get(server).audit()) {
            if (!entry.currency().equals(MoneyCurrency.COIN.id()) || date(entry.time()).isBefore(earliest)
                    || (wallet != null && !involves(entry.from(), wallet, escrow, pending, questPending)
                    && !involves(entry.to(), wallet, escrow, pending, questPending))) continue;
            count++;
            BigInteger amount = BigInteger.valueOf(entry.amount());
            if (entry.from().equals("system:opening")) opening = opening.add(amount);
            else if (entry.from().equals("system:mint")) minted = minted.add(amount);
            else if (entry.to().equals("system:sink")) sunk = sunk.add(amount);
            else transferred = transferred.add(amount);
        }
        return new Summary(count, opening, minted, sunk, transferred);
    }

    public static LocalDate date(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate();
    }

    public static String format(BigInteger minorUnits) {
        return new BigDecimal(minorUnits, 2).toPlainString();
    }

    private static boolean involves(String account, String wallet, String escrow,
                                    String pending, String questPending) {
        return account.equals(wallet) || account.startsWith(escrow)
                || account.startsWith(pending) || account.startsWith(questPending)
                || account.startsWith("mail_pending:" + wallet.substring("wallet:".length()) + ":");
    }

    private static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("geg");
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String json(CoinAuditEntry entry) {
        com.google.gson.JsonObject object = new com.google.gson.JsonObject();
        object.addProperty("sequence", entry.sequence());
        object.addProperty("time", Instant.ofEpochMilli(entry.time()).toString());
        object.addProperty("reason", entry.reason());
        object.addProperty("currency", entry.currency());
        object.addProperty("from", entry.from());
        object.addProperty("to", entry.to());
        object.addProperty("amountMinorUnits", entry.amount());
        object.addProperty("reference", entry.reference());
        return object.toString();
    }

    public record Summary(int count, BigInteger opening, BigInteger minted,
                          BigInteger sunk, BigInteger transferred) {
    }
}
