package io.github.luyingyilong.geg.market.client;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.SelectableTheme;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import io.github.luyingyilong.geg.client.ui.Modal;
import io.github.luyingyilong.geg.client.ui.OreTheme;
import io.github.luyingyilong.geg.market.*;
import io.github.luyingyilong.geg.money.MoneyCurrency;
import io.github.luyingyilong.geg.money.client.ClientMoneyState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;


final class MarketCanvasWidget extends Widget<MarketCanvasWidget> implements Interactable {
    private static final int BACKGROUND = 0xFF101821;
    private static final int SURFACE_HOVER = 0xFF2C4050;
    private static final int ACCENT = 0xFF52BEA8;
    private static final int TEXT = 0xFFFCFCFC;
    private static final int HEADER_TEXT = 0xFF1E1E1F;
    private static final int SECONDARY_TEXT = 0xFFD8D9DC;
    private static final int CARD_GAP = 4;
    private static final int MIN_CARD_WIDTH = 96;
    private static final int HEADER_HEIGHT = 25;
    private static final int FILTER_WIDTH = 82;
    private static final int FILTER_ROW_HEIGHT = 17;
    private static final int FOOTER_HEIGHT = 25;
    private final List<Card> cards = new ArrayList<>();
    private final List<String> modIds = new ArrayList<>();
    private Tab tab = Tab.MARKET;
    private Card modal;
    private String filter = "all";
    private String lastSearch = "";
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int scroll;
    private int maxScroll;
    private int selectedOffer;
    private long selectedDetailId;
    private boolean quantityMenu;
    private boolean requestMode;
    private Card requestSample;
    private int filterOffset;
    private boolean filterOpen;
    private boolean dirty = true;

    private static boolean sameCard(Card left, Card right) {
        if (left.kind != right.kind) {
            return false;
        }
        return switch (left.kind) {
            case GROUP -> MarketKey.of(left.item).equals(MarketKey.of(right.item));
            case FAVORITE -> left.favorite != null && right.favorite != null
                    && (left.favorite.orderId() == 0L
                    ? left.favoriteIndex == right.favoriteIndex
                    : left.favorite.orderId() == right.favorite.orderId());
            case OWN -> left.listing.id() == right.listing.id();
            case OWN_REQUEST -> left.order.id() == right.order.id();
            case SAMPLE -> MarketKey.of(left.item).equals(MarketKey.of(right.item));
            case INVENTORY -> left.slot == right.slot && MarketKey.of(left.item).equals(MarketKey.of(right.item));
            case HISTORY -> left.purchase.purchasedAt() == right.purchase.purchasedAt()
                    && MarketKey.of(left.item).equals(MarketKey.of(right.item));
            case DELIVERY -> left.delivery.id() == right.delivery.id();
            case COIN -> left.credit.id() == right.credit.id();
        };
    }

    private static int parseQuantity(int max) {
        int quantity = Integer.parseInt(ClientMarketScreen.quantityText());
        if (quantity <= 0 || quantity > max) {
            throw new IllegalArgumentException("Invalid quantity");
        }
        return quantity;
    }

    private static void send(MarketActionPacket.Action action, long id, long version,
                             long price, int quantity, int slot) {
        MarketNetwork.sendAction(new MarketActionPacket(action, id, version, price,
                quantity, slot, ItemStack.EMPTY));
    }

    private static int bodyBottom(int height) {
        return height - FOOTER_HEIGHT - 4;
    }

    private static String namespace(ItemStack item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.getItem());
        return item.isEmpty() || id == null ? "" : id.getNamespace();
    }

    private static String itemId(ItemStack item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item.getItem());
        return item.isEmpty() || id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
    }

    void invalidate() {
        dirty = true;
        if (modal == null) {
            return;
        }
        Card previous = modal;
        int previousOffset = selectedOffer;
        modal = sourceCards().stream().filter(card -> sameCard(previous, card)).findFirst().orElse(null);
        if (modal == null) {
            closeModal();
        } else if (!modal.offers.isEmpty()) {
            selectedOffer = Math.min(previousOffset, Math.max(0, modal.offers.size() - 5));
            if (selectedDetailId != 0L && modal.offers.stream().noneMatch(offer -> offer.id() == selectedDetailId)) {
                quantityMenu = false;
                ClientMarketScreen.showTradeQuantity(false);
            }
        } else if (modal.kind == Kind.GROUP || modal.kind == Kind.FAVORITE) {
            quantityMenu = false;
            ClientMarketScreen.showFields(false, false, 0, 0L);
        }
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        GuiGraphics graphics = context.getGraphics();
        Font font = context.getFont();
        int x = getArea().x;
        int y = getArea().y;
        int width = getArea().width;
        int height = getArea().height;
        if (width <= 0 || height <= 0) {
            return;
        }

        String search = ClientMarketScreen.searchText();
        if (dirty || lastWidth != width || lastHeight != height || !Objects.equals(lastSearch, search)) {
            rebuild(font, width, height, search);
        }

        // 绘制 header
        WidgetThemeEntry<WidgetTheme> headerEntry = context.getTheme().getWidgetTheme(OreTheme.HEADER_PANEL);
        drawPanelBackground(x, y, width, HEADER_HEIGHT, headerEntry);
        graphics.drawString(font, Component.translatable("market.geg.title"), x + 10, y + 8, HEADER_TEXT, false);
        drawFilter(graphics, font, x, y, width);
        if (tab == Tab.SELL) drawPublishSwitch(graphics, font, x, y);
        drawBody(graphics, font, x, y, width, height, context.getAbsMouseX(), context.getAbsMouseY());
        drawFooter(graphics, font, x, y, width, height);
        if (filterOpen) {
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            drawFilterMenu(graphics, font, x, y, width);
            graphics.flush();
            graphics.pose().popPose();
        }
        if (modal != null) {
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            drawModal(graphics, font, x, y, width, height);
            if (quantityMenu) {
                graphics.flush();
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 100.0F);
                drawQuantityMenu(graphics, font, x, y, width, height);
                graphics.flush();
                graphics.pose().popPose();
            }
            graphics.flush();
            graphics.pose().popPose();
        }
    }

    private void rebuild(Font font, int width, int height, String search) {
        lastWidth = width;
        lastHeight = height;
        lastSearch = search;
        dirty = false;

        List<Card> source = sourceCards();
        modIds.clear();
        modIds.add("all");
        ClientMarketState.summaries().stream().map(summary -> namespace(summary.item()))
                .filter(value -> !value.isEmpty())
                .distinct().sorted().forEach(modIds::add);
        if (!modIds.contains(filter)) {
            filter = "all";
        }
        filterOffset = Math.min(filterOffset, Math.max(0, modIds.size() - 8));

        String query = search.trim().toLowerCase(Locale.ROOT);
        cards.clear();
        for (Card card : source) {
            String namespace = namespace(card.item);
            if (!filter.equals("all") && !filter.equals(namespace)) {
                continue;
            }
            if (!query.isEmpty() && !MarketSearchIndex.matches(card.item, query)
                    && !itemId(card.item).contains(query)) {
                continue;
            }
            cards.add(card);
        }

        int contentWidth = Math.max(1, width - 16);
        int columns = Math.max(1, Math.min(4, (contentWidth + CARD_GAP) / (MIN_CARD_WIDTH + CARD_GAP)));
        int cardWidth = (contentWidth - (columns - 1) * CARD_GAP) / columns;
        int[] columnHeights = new int[columns];
        Arrays.fill(columnHeights, 4);
        for (Card card : cards) {
            int column = 0;
            for (int index = 1; index < columns; index++) {
                if (columnHeights[index] < columnHeights[column]) {
                    column = index;
                }
            }
            card.width = cardWidth;
            int titleWidth = cardWidth - (card.item.isEmpty() ? 12 : 31);
            boolean twoLines = font.width(card.title()) > titleWidth;
            boolean hasPrice = card.kind == Kind.GROUP || card.kind == Kind.OWN || card.kind == Kind.OWN_REQUEST
                    || card.kind == Kind.FAVORITE || card.kind == Kind.HISTORY || card.kind == Kind.COIN;
            card.height = (hasPrice && card.kind != Kind.COIN ? 58 : 48) + (twoLines ? 10 : 0);
            card.x = 8 + column * (cardWidth + CARD_GAP);
            card.y = columnHeights[column];
            columnHeights[column] += card.height + CARD_GAP;
        }
        int contentHeight = Arrays.stream(columnHeights).max().orElse(0);
        maxScroll = Math.max(0, contentHeight - Math.max(1, bodyBottom(height) - 52));
        scroll = Math.min(scroll, maxScroll);
    }

    private List<Card> sourceCards() {
        List<Card> result = new ArrayList<>();
        switch (tab) {
            case MARKET -> {
                for (MarketSummary summary : ClientMarketState.summaries()) {
                    Card card = new Card(Kind.GROUP, summary.item());
                    card.detailId = summary.representativeId();
                    card.quantity = summary.quantity();
                    card.price = summary.minPrice();
                    card.buyQuantity = summary.buyQuantity();
                    card.offers.addAll(ClientMarketState.detailsFor(card.item));
                    result.add(card);
                }
                result.sort(Comparator.comparingLong((Card card) -> card.price == 0L ? Long.MAX_VALUE : card.price)
                        .thenComparing(card -> itemId(card.item)));
            }
            case MINE -> {
                for (MarketListing listing : ClientMarketState.mine()) {
                    Card card = new Card(Kind.OWN, listing.item());
                    card.listing = listing;
                    card.quantity = listing.quantity();
                    card.price = listing.unitPrice();
                    result.add(card);
                }
                for (MarketBuyOrder order : ClientMarketState.mineRequests()) {
                    Card card = new Card(Kind.OWN_REQUEST, order.item());
                    card.order = order;
                    card.quantity = order.quantity();
                    card.price = order.unitPrice();
                    result.add(card);
                }
                result.sort(Comparator.comparingLong((Card card) -> card.listing != null
                        ? card.listing.createdAt() : card.order.createdAt()).reversed());
            }
            case SELL -> {
                if (requestMode && requestSample != null) result.add(requestSample);
                for (ClientMarketState.InventoryEntry entry : ClientMarketState.inventory()) {
                    Card card = new Card(Kind.INVENTORY, entry.item());
                    card.slot = entry.slot();
                    card.quantity = entry.item().getCount();
                    result.add(card);
                }
            }
            case FAVORITES -> {
                for (int index = 0; index < ClientMarketState.favorites().size(); index++) {
                    MarketFavorite favorite = ClientMarketState.favorites().get(index);
                    Card card = new Card(Kind.FAVORITE, favorite.item());
                    card.favorite = favorite;
                    card.favoriteIndex = index;
                    card.detailId = favorite.ended() ? 0L : favorite.orderId();
                    card.quantity = favorite.ended() ? 0L : favorite.quantity();
                    card.price = favorite.unitPrice();
                    MarketKey key = MarketKey.of(favorite.item());
                    for (MarketSummary summary : ClientMarketState.summaries()) {
                        if (!favorite.ended() && MarketKey.of(summary.item()).equals(key)) {
                            break;
                        }
                    }
                    if (!favorite.ended()) card.offers.addAll(ClientMarketState.detailsFor(card.item));
                    result.add(card);
                }
            }
            case HISTORY -> {
                for (MarketPurchase purchase : ClientMarketState.history()) {
                    Card card = new Card(Kind.HISTORY, purchase.item());
                    card.purchase = purchase;
                    card.quantity = purchase.quantity();
                    card.price = purchase.unitPrice();
                    result.add(card);
                }
            }
            case CLAIM -> {
                for (MarketCoinCredit credit : ClientMarketState.coinCredits()) {
                    Card card = new Card(Kind.COIN, ItemStack.EMPTY);
                    card.credit = credit;
                    card.price = credit.amount();
                    result.add(card);
                }
                for (MarketDelivery delivery : ClientMarketState.deliveries()) {
                    Card card = new Card(Kind.DELIVERY, delivery.item());
                    card.delivery = delivery;
                    card.quantity = delivery.quantity();
                    result.add(card);
                }
            }
        }
        return result;
    }

    private void drawBody(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                          int mouseX, int mouseY) {
        int top = y + 52;
        int bottom = y + bodyBottom(height);
        graphics.enableScissor(x + 6, top, x + width - 6, bottom);
        Card hovered = null;
        for (Card card : cards) {
            int cardY = top + card.y - scroll;
            if (cardY + card.height < top - (bottom - top) || cardY > bottom + (bottom - top)) {
                continue;
            }
            int cardX = x + card.x;
            boolean hover = mouseX >= cardX && mouseX < cardX + card.width
                    && mouseY >= cardY && mouseY < cardY + card.height;
            if (hover && mouseY >= top && mouseY < bottom) {
                hovered = card;
            }
            drawCard(graphics, font, card, cardX, cardY, hover);
        }
        graphics.disableScissor();

        if (cards.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable(ClientMarketState.ready() ? "market.geg.empty" : "market.geg.loading"),
                    x + width / 2, top + 24, SECONDARY_TEXT);
        }
        if (hovered != null && !hovered.item.isEmpty() && mouseX < x + hovered.x + 24
                && modal == null && !filterOpen) {
            graphics.renderTooltip(font, hovered.item, mouseX, mouseY);
        }
    }

    private void drawCard(GuiGraphics graphics, Font font, Card card, int x, int y, boolean hover) {
        boolean ended = card.kind == Kind.FAVORITE && card.favorite != null && card.favorite.ended();
        drawPanelBackground(x, y, card.width, card.height, getContext().getTheme().getPanelTheme());
        if (ended) {
            graphics.fill(x, y, x + card.width, y + card.height, 0xA032383D);
        } else if (hover) {
            graphics.fill(x, y, x + card.width, y + card.height, 0x24FFFFFF);
        }
//        graphics.fill(x, y, x + 2, y + card.height, ended ? SECONDARY_TEXT : ACCENT);
        if (!card.item.isEmpty()) {
            graphics.renderItem(card.item, x + 5, y + 6);
        }

        int textX = x + (card.item.isEmpty() ? 6 : 26);
        int textWidth = x + card.width - 5 - textX;
        String name = card.title().getString();
        String firstLine = font.plainSubstrByWidth(name, textWidth);
        graphics.drawString(font, firstLine, textX, y + 5, TEXT, false);
        boolean twoLines = firstLine.length() < name.length();
        if (firstLine.length() < name.length()) {
            String secondLine = font.plainSubstrByWidth(name.substring(firstLine.length()), textWidth);
            graphics.drawString(font, secondLine, textX, y + 15, TEXT, false);
        }
        Component count = switch (card.kind) {
            case GROUP -> Component.translatable("market.geg.market_count", card.quantity, card.buyQuantity);
            case OWN, OWN_REQUEST, FAVORITE -> Component.translatable("market.geg.count", card.quantity);
            case INVENTORY -> Component.translatable("market.geg.inventory_count", card.quantity);
            case SAMPLE -> Component.translatable("market.geg.sample");
            case HISTORY -> Component.translatable("market.geg.bought_count", card.quantity);
            case DELIVERY -> Component.translatable("market.geg.claim_count", card.quantity);
            case COIN -> Component.empty();
        };
        int nextLineY = y + (twoLines ? 26 : 16);
        if (card.kind != Kind.COIN) {
            graphics.drawString(font, font.plainSubstrByWidth(count.getString(), textWidth),
                    textX, nextLineY, SECONDARY_TEXT, false);
            nextLineY += 11;
        }

        Component price = switch (card.kind) {
            case GROUP, OWN, OWN_REQUEST, FAVORITE, HISTORY -> card.quantity == 0
                    ? card.kind == Kind.GROUP && card.buyQuantity > 0 ? Component.empty()
                    : Component.translatable(card.kind == Kind.FAVORITE
                    ? "market.geg.ended" : "market.geg.out_of_stock")
                    : Component.translatable("market.geg.price", MoneyCurrency.COIN.format(card.price));
            case COIN -> Component.translatable("market.geg.price", MoneyCurrency.COIN.format(card.price));
            default -> Component.empty();
        };
        if (!price.getString().isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(price.getString(), textWidth),
                    textX, nextLineY, ended ? SECONDARY_TEXT : ACCENT, false);
            nextLineY += 13;
        }

        Component action = Component.translatable(switch (card.kind) {
            case GROUP, FAVORITE -> "market.geg.details";
            case OWN, OWN_REQUEST, HISTORY -> "market.geg.details";
            case INVENTORY, SAMPLE -> requestMode ? "market.geg.request" : "market.geg.list";
            case DELIVERY, COIN -> "market.geg.claim";
        });
        int buttonX = textX;
        int buttonY = nextLineY + 1;
        drawButton(graphics, font, buttonX, buttonY, textWidth, 14, action);
    }

    private void drawFilter(GuiGraphics graphics, Font font, int x, int y, int width) {
        int buttonX = x + width - FILTER_WIDTH;
        String label = filter.equals("all") ? Component.translatable("market.geg.all_mods").getString() : filter;
        drawButton(graphics, font, buttonX, y, FILTER_WIDTH, HEADER_HEIGHT, Component.literal(label));
    }

    private void drawPublishSwitch(GuiGraphics graphics, Font font, int x, int y) {
        drawToggleButton(graphics, font, x + 6, y + 32, 67, 18,
                Component.translatable("market.geg.selling"), !requestMode);
        drawToggleButton(graphics, font, x + 73, y + 32, 67, 18,
                Component.translatable("market.geg.buying"), requestMode);
    }

    private void drawFilterMenu(GuiGraphics graphics, Font font, int x, int y, int width) {
        int buttonX = x + width - FILTER_WIDTH;
        int visible = Math.min(8, modIds.size() - filterOffset);
        drawPanelBackground(buttonX, y + HEADER_HEIGHT, FILTER_WIDTH,
                visible * FILTER_ROW_HEIGHT, getContext().getTheme().getWidgetTheme(OreTheme.MODAL));
        for (int index = 0; index < visible; index++) {
            String value = modIds.get(filterOffset + index);
            Component label = value.equals("all") ? Component.translatable("market.geg.all_mods") : Component.literal(value);
            graphics.drawString(font, font.plainSubstrByWidth(label.getString(), FILTER_WIDTH - 8),
                    buttonX + 4, y + HEADER_HEIGHT + index * FILTER_ROW_HEIGHT
                            + (FILTER_ROW_HEIGHT - font.lineHeight) / 2, TEXT, false);
        }
    }

    private void drawFooter(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        int columns = Tab.values().length;
        int top = y + height - FOOTER_HEIGHT;
        for (int index = 0; index < Tab.values().length; index++) {
            int tabX = x + index * width / columns;
            int tabEnd = x + (index + 1) * width / columns;
            drawToggleButton(graphics, font, tabX, top, tabEnd - tabX, FOOTER_HEIGHT,
                    Tab.values()[index].title(), Tab.values()[index] == tab);
        }
    }

    private void drawModal(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        detailDialog().draw(getContext(), new Modal.Rect(x, y, width, height),
                content -> drawModalContent(graphics, font, content));
    }

    private Modal detailDialog() {
        Modal dialog = new Modal(340, 224, 0, modal.title());
        if (modal.kind == Kind.GROUP || modal.kind == Kind.FAVORITE) {
            if (modal.kind == Kind.FAVORITE && modal.favorite.ended()) {
                dialog.action(Component.translatable("market.geg.unfavorite"), this::secondaryModalAction);
            }
            dialog.action(Component.translatable("market.geg.request_this"), this::primaryModalAction);
        } else if (modal.kind == Kind.OWN || modal.kind == Kind.OWN_REQUEST) {
            dialog.action(Component.translatable("market.geg.cancel_listing"), this::secondaryModalAction);
            dialog.action(Component.translatable("market.geg.reprice"), this::primaryModalAction);
        } else if (modal.kind == Kind.INVENTORY || modal.kind == Kind.SAMPLE) {
            dialog.action(Component.translatable(requestMode
                    ? "market.geg.confirm_request" : "market.geg.confirm_list"), this::primaryModalAction);
        } else if (modal.kind == Kind.DELIVERY || modal.kind == Kind.COIN) {
            dialog.action(Component.translatable("market.geg.claim"), this::primaryModalAction);
        }
        return dialog;
    }

    private void drawModalContent(GuiGraphics graphics, Font font, Modal.Rect content) {
        int contentX = content.x();
        int contentY = content.y();

        if (modal.kind == Kind.GROUP || modal.kind == Kind.FAVORITE) {
            graphics.fill(contentX + 2, contentY + 5, content.right() - 2, contentY + 22, BACKGROUND);
            graphics.drawString(font, Component.translatable("market.geg.status"), contentX + 6, contentY + 9, SECONDARY_TEXT, false);
            graphics.drawString(font, Component.translatable("market.geg.unit_price"), contentX + 99, contentY + 9, SECONDARY_TEXT, false);
            graphics.drawString(font, Component.translatable("market.geg.operation"), content.right() - 103, contentY + 9, SECONDARY_TEXT, false);
            if (modal.offers.isEmpty()) graphics.drawString(font,
                    Component.translatable(modal.quantity > 0 || modal.buyQuantity > 0
                            ? "market.geg.loading" : "market.geg.out_of_stock"),
                    contentX + 4, contentY + 32, SECONDARY_TEXT, false);
            for (int index = selectedOffer; index < Math.min(selectedOffer + 5, modal.offers.size()); index++) {
                MarketDetail offer = modal.offers.get(index);
                int rowY = contentY + 24 + (index - selectedOffer) * 25;
                graphics.fill(contentX + 2, rowY, content.right() - 2, rowY + 23,
                        selectedDetailId == offer.id() ? SURFACE_HOVER : BACKGROUND);
                String status = Component.translatable(offer.buyOrder()
                        ? "market.geg.buying" : "market.geg.selling").getString();
                graphics.drawString(font, font.plainSubstrByWidth(status + " x" + offer.quantity(), 88),
                        contentX + 6, rowY + 3, offer.buyOrder() ? ACCENT : TEXT, false);
                graphics.drawString(font, font.plainSubstrByWidth(offer.ownerName(), 88),
                        contentX + 6, rowY + 13, SECONDARY_TEXT, false);
                graphics.drawString(font, font.plainSubstrByWidth(MoneyCurrency.COIN.format(offer.unitPrice()), 96),
                        contentX + 99, rowY + 8, TEXT, false);
                int actionX = content.right() - 104;
                drawButton(graphics, font, actionX, rowY + 2, 50, 19,
                        Component.translatable(offer.buyOrder()
                                ? "market.geg.fulfill" : "market.geg.buy"));
                boolean favorite = ClientMarketState.favorites().stream()
                        .anyMatch(value -> value.orderId() == offer.id());
                drawToggleButton(graphics, font, actionX + 53, rowY + 2, 51, 19,
                        Component.translatable(favorite
                                ? "market.geg.favorited" : "market.geg.favorite"), favorite);
            }
        } else {
            Component info = switch (modal.kind) {
                case OWN -> Component.translatable("market.geg.offer", modal.listing.sellerName(),
                        modal.listing.quantity(), MoneyCurrency.COIN.format(modal.listing.unitPrice()));
                case OWN_REQUEST -> Component.translatable("market.geg.request_info", modal.order.quantity(),
                        MoneyCurrency.COIN.format(modal.order.unitPrice()),
                        MoneyCurrency.COIN.format(modal.order.escrow()));
                case INVENTORY, SAMPLE -> Component.translatable("market.geg.inventory_count", modal.quantity);
                case DELIVERY -> Component.translatable("market.geg.claim_count", modal.quantity);
                case HISTORY -> Component.translatable("market.geg.history_entry", modal.purchase.sellerName(),
                        modal.purchase.quantity(), MoneyCurrency.COIN.format(modal.purchase.unitPrice()));
                case COIN -> Component.translatable("market.geg.pending_amount",
                        MoneyCurrency.COIN.format(modal.credit.amount()));
                default -> Component.empty();
            };
            graphics.drawString(font, font.plainSubstrByWidth(info.getString(), content.width() - 8),
                    contentX + 4, contentY + 22, SECONDARY_TEXT, false);
        }

        if (modal.kind == Kind.INVENTORY || modal.kind == Kind.SAMPLE) {
            graphics.drawString(font, Component.translatable("market.geg.quantity"), contentX + 4, contentY + 113, SECONDARY_TEXT, false);
        }
        if (modal.kind == Kind.OWN || modal.kind == Kind.OWN_REQUEST
                || modal.kind == Kind.INVENTORY || modal.kind == Kind.SAMPLE) {
            graphics.drawString(font, Component.translatable("market.geg.unit_price"), contentX + 104, contentY + 113, SECONDARY_TEXT, false);
        }
    }

    private void drawQuantityMenu(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        quantityDialog().draw(getContext(), new Modal.Rect(x, y, width, height), content -> {
            String[] labels = {"-16", "-8", "+8", "+16", Component.translatable("market.geg.max").getString()};
            for (int index = 0; index < labels.length; index++) {
                int buttonX = content.x() + 4 + index * 40;
                drawButton(graphics, font, buttonX, content.y() + 32, 38, 20,
                        Component.literal(labels[index]));
            }
        });
    }

    private Modal quantityDialog() {
        MarketDetail offer = selectedDetail();
        return new Modal(224, 167, 8, Component.translatable("market.geg.quantity"))
                .action(Component.translatable("market.geg.cancel"), this::closeQuantityMenu)
                .action(Component.translatable(offer != null && offer.buyOrder()
                        ? "market.geg.confirm_fulfill" : "market.geg.confirm_buy"), this::confirmQuantity);
    }

    private void closeQuantityMenu() {
        quantityMenu = false;
        ClientMarketScreen.showTradeQuantity(false);
    }

    private void drawButton(GuiGraphics graphics, Font font, int x, int y,
                            int width, int height, Component text) {
        ModularGuiContext context = getContext();
        WidgetTheme style = context.getTheme().getButtonTheme().getTheme(isHovered(x, y, width, height));
        drawThemedControl(graphics, font, context, style, x, y, width, height, text);
    }

    private void drawToggleButton(GuiGraphics graphics, Font font, int x, int y,
                                  int width, int height, Component text, boolean selected) {
        ModularGuiContext context = getContext();
        SelectableTheme toggle = context.getTheme().getToggleButtonTheme()
                .getTheme(isHovered(x, y, width, height));
        drawThemedControl(graphics, font, context, selected ? toggle.getSelected() : toggle,
                x, y, width, height, text);
    }

    private void drawThemedControl(GuiGraphics graphics, Font font, ModularGuiContext context,
                                   WidgetTheme style, int x, int y, int width, int height, Component text) {
        IDrawable background = style.getBackground();
        if (background != null) {
            background.draw(context, x, y, width, height, style);
        }
        String label = font.plainSubstrByWidth(text.getString(), Math.max(0, width - 8));
        graphics.drawString(font, label, x + (width - font.width(label)) / 2,
                y + (height - font.lineHeight) / 2, style.getTextColor(), style.isTextShadow());
    }

    private void drawPanelBackground(int x, int y, int width, int height,
                                     WidgetThemeEntry<WidgetTheme> entry) {
        if (width <= 0 || height <= 0) {
            return;
        }
        ModularGuiContext context = getContext();
        WidgetTheme style = entry != null
                ? entry.theme()
                : context.getTheme().getPanelTheme().theme();
        IDrawable background = style.getBackground();
        if (background != null) {
            background.draw(context, x, y, width, height, style);
        }
    }

    private boolean isHovered(int x, int y, int width, int height) {
        ModularGuiContext context = getContext();
        int mouseX = context.getAbsMouseX();
        int mouseY = context.getAbsMouseY();
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public Interactable.Result onMousePressed(int button) {
        if (button != 0) {
            return Interactable.Result.IGNORE;
        }
        int x = getArea().x;
        int y = getArea().y;
        int width = getArea().width;
        int height = getArea().height;
        int mouseX = getContext().getAbsMouseX();
        int mouseY = getContext().getAbsMouseY();

        if (modal != null) {
            if (quantityMenu) handleQuantityClick(mouseX - x, mouseY - y, width, height);
            else handleModalClick(mouseX - x, mouseY - y, width, height);
            return Interactable.Result.STOP;
        }

        int footerTop = height - FOOTER_HEIGHT;
        if (mouseY - y >= footerTop) {
            int columns = Tab.values().length;
            int index = Math.min(columns - 1, Math.max(0,
                    (mouseX - x) * columns / Math.max(width, 1)));
            if (index >= 0 && index < Tab.values().length) {
                Tab nextTab = Tab.values()[index];
                if (nextTab != tab) {
                    tab = nextTab;
                    scroll = 0;
                    filter = "all";
                    filterOpen = false;
                    dirty = true;
                }
            }
            return Interactable.Result.STOP;
        }

        if (mouseX - x >= width - FILTER_WIDTH && mouseX - x < width
                && mouseY - y >= 0 && mouseY - y < HEADER_HEIGHT) {
            filterOpen = !filterOpen;
            return Interactable.Result.STOP;
        }
        if (filterOpen) {
            int row = (mouseY - y - HEADER_HEIGHT) / FILTER_ROW_HEIGHT;
            int index = filterOffset + row;
            if (mouseX - x >= width - FILTER_WIDTH && mouseX - x < width
                    && mouseY - y >= HEADER_HEIGHT && row >= 0 && row < 8 && index < modIds.size()) {
                filter = modIds.get(index);
                scroll = 0;
                dirty = true;
            }
            filterOpen = false;
            return Interactable.Result.STOP;
        }

        if (tab == Tab.SELL && mouseX - x >= 6 && mouseX - x < 140
                && mouseY - y >= 32 && mouseY - y < 50) {
            requestMode = mouseX - x >= 73;
            return Interactable.Result.STOP;
        }

        int bodyTop = y + 52;
        int bodyEnd = y + bodyBottom(height);
        if (mouseY >= bodyTop && mouseY < bodyEnd) {
            for (Card card : cards) {
                int cardX = x + card.x;
                int cardY = bodyTop + card.y - scroll;
                if (mouseX >= cardX && mouseX < cardX + card.width
                        && mouseY >= cardY && mouseY < cardY + card.height) {
                    openModal(card);
                    return Interactable.Result.STOP;
                }
            }
        }
        return Interactable.Result.IGNORE;
    }

    private void handleModalClick(int mouseX, int mouseY, int width, int height) {
        Modal dialog = detailDialog();
        Modal.Rect bounds = dialog.bounds(0, 0, width, height);
        if (dialog.click(bounds, mouseX, mouseY, this::closeModal)) {
            return;
        }
        Modal.Rect content = dialog.content(bounds);
        if ((modal.kind == Kind.GROUP || modal.kind == Kind.FAVORITE)
                && mouseY >= content.y() + 24 && mouseY < content.y() + 149) {
            int index = selectedOffer + (mouseY - content.y() - 24) / 25;
            if (index >= modal.offers.size()) return;
            MarketDetail offer = modal.offers.get(index);
            int actionX = content.right() - 104;
            if (mouseX >= actionX && mouseX < actionX + 51) {
                selectedDetailId = offer.id();
                quantityMenu = true;
                ClientMarketScreen.showTradeQuantity(true);
            } else if (mouseX >= actionX + 53 && mouseX < actionX + 105) {
                int favoriteIndex = -1;
                for (int i = 0; i < ClientMarketState.favorites().size(); i++) {
                    if (ClientMarketState.favorites().get(i).orderId() == offer.id()) {
                        favoriteIndex = i;
                        break;
                    }
                }
                if (favoriteIndex >= 0) send(MarketActionPacket.Action.FAVORITE_REMOVE, 0L, 0L, 0L, 0, favoriteIndex);
                else send(MarketActionPacket.Action.FAVORITE_ADD, offer.id(), 0L, 0L, 0, 0);
            } else {
                selectedDetailId = offer.id();
            }
            return;
        }
    }

    private MarketDetail selectedDetail() {
        if (modal == null) return null;
        return modal.offers.stream().filter(offer -> offer.id() == selectedDetailId).findFirst().orElse(null);
    }

    private int maxTradeQuantity(MarketDetail offer) {
        if (offer == null) return 0;
        if (!offer.buyOrder()) {
            return (int) Math.min(offer.quantity(),
                    ClientMoneyState.balance().coinMinorUnits() / offer.unitPrice());
        }
        int count = 0;
        for (ClientMarketState.InventoryEntry entry : ClientMarketState.inventory()) {
            if (MarketKey.of(entry.item()).equals(MarketKey.of(offer.item()))) count += entry.item().getCount();
        }
        return Math.min(offer.quantity(), count);
    }

    private void handleQuantityClick(int mouseX, int mouseY, int width, int height) {
        Modal dialog = quantityDialog();
        Modal.Rect bounds = dialog.bounds(0, 0, width, height);
        if (dialog.click(bounds, mouseX, mouseY, this::closeQuantityMenu)) {
            return;
        }
        MarketDetail offer = selectedDetail();
        if (offer == null) {
            closeQuantityMenu();
            return;
        }
        Modal.Rect content = dialog.content(bounds);
        int quickX = content.x() + 4;
        if (mouseY >= content.y() + 32 && mouseY < content.y() + 52) {
            int index = (mouseX - quickX) / 40;
            if (mouseX >= quickX && index >= 0 && index < 5
                    && mouseX < quickX + index * 40 + 38) {
                int current;
                try { current = Integer.parseInt(ClientMarketScreen.tradeQuantityText()); }
                catch (NumberFormatException exception) { current = 1; }
                int[] deltas = {-16, -8, 8, 16};
                int max = maxTradeQuantity(offer);
                int next = index == 4 ? max : Math.max(1, Math.min(max,
                        (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, (long) current + deltas[index]))));
                ClientMarketScreen.setTradeQuantity(Math.max(1, next));
            }
        }
    }

    private void confirmQuantity() {
        MarketDetail offer = selectedDetail();
        if (offer == null) {
            closeQuantityMenu();
            return;
        }
        try {
            int amount = Integer.parseInt(ClientMarketScreen.tradeQuantityText());
            if (amount <= 0 || amount > maxTradeQuantity(offer)) throw new IllegalArgumentException();
            send(offer.buyOrder() ? MarketActionPacket.Action.FULFILL_REQUEST : MarketActionPacket.Action.BUY,
                    offer.id(), offer.version(), offer.unitPrice(), amount, 0);
            closeQuantityMenu();
            ClientMarketState.requestDetails(modal.detailId, modal.item);
        } catch (IllegalArgumentException exception) {
            if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("market.geg.result.invalid"), false);
        }
    }

    private void openModal(Card card) {
        modal = card;
        selectedOffer = 0;
        selectedDetailId = 0L;
        quantityMenu = false;
        ClientMarketScreen.setSearchEnabled(false);
        switch (card.kind) {
            case GROUP, FAVORITE -> {
                ClientMarketScreen.showFields(false, false, 0, 0L);
                if (card.detailId != 0L) {
                    card.offers.clear();
                    ClientMarketState.requestDetails(card.detailId, card.item);
                }
            }
            case INVENTORY, SAMPLE -> ClientMarketScreen.showFields(true, true, 1, 1L);
            case OWN, OWN_REQUEST -> ClientMarketScreen.showFields(false, true, 0, card.price);
            default -> ClientMarketScreen.showFields(false, false, 0, 0L);
        }
    }

    private void closeModal() {
        modal = null;
        quantityMenu = false;
        ClientMarketScreen.showTradeQuantity(false);
        ClientMarketScreen.setSearchEnabled(true);
        ClientMarketScreen.showFields(false, false, 0, 0L);
    }

    private void secondaryModalAction() {
        if (modal.kind == Kind.FAVORITE && modal.favorite.ended()) {
            send(MarketActionPacket.Action.FAVORITE_REMOVE, 0L, 0L, 0L, 0, modal.favoriteIndex);
            closeModal();
        } else if (modal.kind == Kind.OWN) {
            send(MarketActionPacket.Action.CANCEL, modal.listing.id(), modal.listing.version(), 0L, 0, 0);
            closeModal();
        } else if (modal.kind == Kind.OWN_REQUEST) {
            send(MarketActionPacket.Action.CANCEL_REQUEST, modal.order.id(), modal.order.version(), 0L, 0, 0);
            closeModal();
        }
    }

    private void primaryModalAction() {
        try {
            switch (modal.kind) {
                case GROUP, FAVORITE -> {
                    requestSample = new Card(Kind.SAMPLE, modal.item);
                    requestSample.quantity = 1;
                    requestSample.slot = modal.kind == Kind.FAVORITE ? -2 : -1;
                    requestSample.detailId = modal.kind == Kind.FAVORITE
                            ? modal.favoriteIndex : selectedDetailId != 0L ? selectedDetailId : modal.detailId;
                    requestMode = true;
                    tab = Tab.SELL;
                    dirty = true;
                }
                case INVENTORY -> {
                    int quantity = parseQuantity(requestMode ? Integer.MAX_VALUE : (int) modal.quantity);
                    long price = MoneyCurrency.COIN.parseAmount(ClientMarketScreen.priceText(), false);
                    MarketNetwork.sendAction(new MarketActionPacket(requestMode
                            ? MarketActionPacket.Action.CREATE_REQUEST : MarketActionPacket.Action.LIST,
                            0L, 0L, price, quantity, modal.slot, modal.item));
                }
                case SAMPLE -> {
                    int quantity = parseQuantity(Integer.MAX_VALUE);
                    long price = MoneyCurrency.COIN.parseAmount(ClientMarketScreen.priceText(), false);
                    MarketNetwork.sendAction(new MarketActionPacket(MarketActionPacket.Action.CREATE_REQUEST,
                            modal.detailId, 0L, price, quantity, modal.slot, modal.item));
                }
                case OWN -> {
                    long price = MoneyCurrency.COIN.parseAmount(ClientMarketScreen.priceText(), false);
                    send(MarketActionPacket.Action.REPRICE, modal.listing.id(), modal.listing.version(), price, 0, 0);
                }
                case OWN_REQUEST -> {
                    long price = MoneyCurrency.COIN.parseAmount(ClientMarketScreen.priceText(), false);
                    send(MarketActionPacket.Action.REPRICE_REQUEST, modal.order.id(), modal.order.version(), price, 0, 0);
                }
                case DELIVERY -> send(MarketActionPacket.Action.CLAIM_ITEM, modal.delivery.id(), 0L, 0L, 0, 0);
                case COIN -> send(MarketActionPacket.Action.CLAIM_COIN, modal.credit.id(), 0L, 0L, 0, 0);
                default -> {
                }
            }
            closeModal();
        } catch (IllegalArgumentException exception) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.translatable("market.geg.result.invalid"), false);
            }
        }
    }

    @Override
    public boolean onMouseScrolled(double delta) {
        if (filterOpen) {
            filterOffset = Math.max(0, Math.min(Math.max(0, modIds.size() - 8),
                    filterOffset - (int) Math.signum(delta)));
            return true;
        }
        if (modal != null) {
            if (quantityMenu) return true;
            if (!modal.offers.isEmpty()) {
                selectedOffer = Math.max(0, Math.min(Math.max(0, modal.offers.size() - 5),
                        selectedOffer - (int) Math.signum(delta)));
            }
            return true;
        }
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta) * 28));
        return true;
    }


    @Override
    public Interactable.Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && modal != null) {
            if (quantityMenu) {
                quantityMenu = false;
                ClientMarketScreen.showTradeQuantity(false);
            } else closeModal();
            return Interactable.Result.STOP;
        }
        return Interactable.Result.IGNORE;
    }

    private enum Tab {
        MARKET("market"),
        MINE("mine"),
        SELL("sell"),
        FAVORITES("favorites"),
        HISTORY("history"),
        CLAIM("claim"),
        ;

        private final String id;

        Tab(String id) {
            this.id = id;
        }

        Component title() {
            return Component.translatable("market.geg.tab." + id);
        }
    }

    private enum Kind {
        GROUP,
        SAMPLE,
        OWN,
        OWN_REQUEST,
        INVENTORY,
        FAVORITE,
        HISTORY,
        DELIVERY,
        COIN,
    }

    private static final class Card {
        private final Kind kind;
        private final ItemStack item;
        private final List<MarketDetail> offers = new ArrayList<>();
        private MarketListing listing;
        private MarketBuyOrder order;
        private MarketCoinCredit credit;
        private MarketFavorite favorite;
        private MarketDelivery delivery;
        private MarketPurchase purchase;
        private long detailId;
        private int slot = -1;
        private int favoriteIndex = -1;
        private long quantity;
        private long price;
        private long buyQuantity;
        private int x;
        private int y;
        private int width;
        private int height;

        private Card(Kind kind, ItemStack item) {
            this.kind = kind;
            this.item = item.copy();
        }

        private Component title() {
            return switch (kind) {
                case COIN -> Component.translatable("market.geg.pending_coin");
                case OWN_REQUEST -> Component.translatable("market.geg.buying_item", item.getHoverName());
                case OWN -> Component.translatable("market.geg.selling_item", item.getHoverName());
                default -> item.getHoverName();
            };
        }
    }
}
