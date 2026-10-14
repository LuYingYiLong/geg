package io.github.luyingyilong.geg.market.client;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.SelectableTheme;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import io.github.luyingyilong.geg.client.ui.Button;
import io.github.luyingyilong.geg.client.ui.Dropdown;
import io.github.luyingyilong.geg.client.ui.Modal;
import io.github.luyingyilong.geg.client.ui.OreTheme;
import io.github.luyingyilong.geg.client.ui.Tabs;
import io.github.luyingyilong.geg.client.ui.Label;
import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Grid;
import io.github.luyingyilong.geg.client.ui.layout.Overlay;
import io.github.luyingyilong.geg.client.ui.layout.Padding;
import io.github.luyingyilong.geg.client.ui.layout.Panel;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Window;
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
import org.jetbrains.annotations.NotNull;

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
    private static final int FOOTER_HEIGHT = Button.Size.MEDIUM.height();
    private static final int FAVORITE_BUTTON_WIDTH = 51;
    private static final int FAVORITE_BUTTON_HEIGHT = 19;
    private static final Tabs FOOTER_TABS = new Tabs(Arrays.stream(Tab.values())
            .map(Tab::title).toList());
    private static final Tabs PUBLISH_TABS = new Tabs(List.of(
            Component.translatable("market.geg.selling"),
            Component.translatable("market.geg.buying")), Button.Size.SMALL);
    private final List<Card> cards = new ArrayList<>();
    private final Dropdown<String> filterDropdown = new Dropdown<>("all", 8);
    private final Window window = new Window(true);
    private final Grid<Card> grid = new Grid<>();
    private final Panel offerRowPanel = new Panel(OreTheme.LIST_ITEM);
    private final Control searchSlot = new Control();
    private final Control filterSlot = new Control() {
        @Override
        public void draw(ModularGuiContext context, int screenX, int screenY) {
            Rect area = screenBounds(screenX, screenY);
            filterDropdown.drawTrigger(context, area.x(), area.y(), area.width());
        }
    };
    private final Control footerSlot = new Control() {
        @Override
        public void draw(ModularGuiContext context, int screenX, int screenY) {
            Rect area = screenBounds(screenX, screenY);
            FOOTER_TABS.draw(context, area.x(), area.y() + 4, area.width(), tab.ordinal());
        }
    };
    private final Control publishSlot = new Control() {
        @Override
        public void draw(ModularGuiContext context, int screenX, int screenY) {
            if (tab == Tab.SELL) {
                Rect area = screenBounds(screenX, screenY);
                PUBLISH_TABS.draw(context, area.x() + 6, area.y() + 7, Math.min(134, area.width() - 12),
                        requestMode ? 1 : 0);
            }
        }
    };
    private final Overlay overlay = new Overlay();
    private Tab tab = Tab.MARKET;
    private Card modal;
    private String lastSearch = "";
    private String lastLanguage = "";
    private int lastWidth = -1;
    private int lastHeight = -1;
    private int selectedOffer;
    private long selectedDetailId;
    private boolean quantityMenu;
    private boolean requestMode;
    private Card requestSample;
    private boolean dirty = true;

    MarketCanvasWidget() {
        Label title = new Label(Component.translatable("market.geg.title"))
                .color(HEADER_TEXT).overflow(Label.Overflow.CLIP)
                .align(Label.Horizontal.LEFT, Label.Vertical.CENTER);
        title.policy(Control.SizePolicy.EXPAND, Control.SizePolicy.FILL);
        searchSlot.minimum(16, Button.Size.SMALL.height()).preferred(138, Button.Size.SMALL.height());
        filterSlot.minimum(FILTER_WIDTH, Button.Size.MEDIUM.height())
                .preferred(FILTER_WIDTH, Button.Size.MEDIUM.height());
        Box headerRow = new Box().orientation(Box.Orientation.HORIZONTAL).gap(6)
                .add(title).add(searchSlot).add(filterSlot);
        Panel header = new Panel(OreTheme.HEADER_PANEL)
                .child(new Padding(new Padding.Insets(0, 0, 0, 10)).child(headerRow));
        header.minimum(0, HEADER_HEIGHT).preferred(0, HEADER_HEIGHT)
                .policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        publishSlot.minimum(0, 27).preferred(0, 27)
                .policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        footerSlot.minimum(0, FOOTER_HEIGHT + 4).preferred(0, FOOTER_HEIGHT + 4)
                .policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        Control body = new Control() {
            private final Padding padding = new Padding(new Padding.Insets(4, 8, 0, 8)).child(grid);
            {
                attach(padding);
            }

            @Override
            public void layout(Rect bounds, Font font) {
                super.layout(bounds, font);
                padding.layout(new Rect(0, 0, bounds.width(), bounds.height()), font);
            }

            @Override
            public void draw(ModularGuiContext context, int screenX, int screenY) {
                padding.draw(context, screenX, screenY);
                Rect area = screenBounds(screenX, screenY);
                if (cards.isEmpty()) {
                    Label.draw(context, Component.translatable(ClientMarketState.ready()
                                    ? "market.geg.empty" : "market.geg.loading"),
                            new Rect(area.x(), area.y() + 18, area.width(), 20), SECONDARY_TEXT,
                            Label.Overflow.CLIP, Label.Horizontal.CENTER, Label.Vertical.CENTER);
                }
            }
        };
        body.policy(Control.SizePolicy.FILL, Control.SizePolicy.EXPAND);
        Box column = new Box().add(header).add(publishSlot).add(body).add(footerSlot);
        window.content(column).overlay(overlay);
        grid.columns(MIN_CARD_WIDTH, 4, CARD_GAP).mode(Grid.LayoutMode.MASONRY)
                .adapter(new Grid.Adapter<>() {
                    @Override public int size() { return cards.size(); }
                    @Override public Card item(int index) { return cards.get(index); }
                    @Override public Object key(Card card) { return cardKey(card); }
                    @Override public int measureHeight(Card card, int cardWidth, Font font) {
                        int titleWidth = cardWidth - (card.item.isEmpty() ? 12 : 31);
                        boolean twoLines = new Label(card.title()).wrap(Label.Wrap.CHARACTER)
                                .maxLines(2).measureHeight(font, titleWidth) > 10;
                        boolean hasPrice = card.kind == Kind.GROUP || card.kind == Kind.OWN
                                || card.kind == Kind.OWN_REQUEST || card.kind == Kind.FAVORITE
                                || card.kind == Kind.HISTORY || card.kind == Kind.COIN;
                        return (hasPrice && card.kind != Kind.COIN ? 42 : 32) + (twoLines ? 10 : 0);
                    }
                    @Override public Grid.View<Card> createView() { return new CardView(); }
                });
        overlay.add(new Control() {
            @Override public void draw(ModularGuiContext context, int screenX, int screenY) {
                if (modal != null || filterDropdown.isOpen()) return;
                Grid.Entry<Card> hovered = grid.hitItem(context.getAbsMouseX() - screenX,
                        context.getAbsMouseY() - screenY);
                if (hovered == null || hovered.item().item.isEmpty()) return;
                Rect viewport = grid.screenBounds(screenX, screenY);
                Rect card = hovered.bounds().translated(viewport.x(), viewport.y() - grid.scroll());
                if (context.getAbsMouseX() >= card.x() + 24) return;
                withOverlayDepth(context, 200.0F, () -> context.getGraphics().renderTooltip(
                        context.getFont(), hovered.item().item,
                        context.getAbsMouseX(), context.getAbsMouseY()));
            }
        });
        overlay.add(new Control() {
            @Override public void draw(ModularGuiContext context, int screenX, int screenY) {
                if (!filterDropdown.isOpen()) return;
                Rect area = filterSlot.screenBounds(screenX, screenY);
                withOverlayDepth(context, 200.0F, () -> filterDropdown.drawMenu(context,
                        area.x(), area.y(), area.width(), area.height()));
            }
        });
        overlay.add(new Control() {
            @Override public void draw(ModularGuiContext context, int screenX, int screenY) {
                if (modal == null) return;
                Rect area = window.screenBounds(screenX, screenY);
                withOverlayDepth(context, 200.0F, () -> {
                    drawModal(area.x(), area.y(),
                            area.width(), area.height());
                    if (quantityMenu) withOverlayDepth(context, 100.0F, () ->
                            drawQuantityMenu(area.x(), area.y(), area.width(), area.height()));
                });
            }
        });
    }

    private final class CardView implements Grid.View<Card> {
        private Card card;
        private final Panel background = new Panel(null);
        @Override public void bind(Card card) { this.card = card; }
        @Override public void draw(ModularGuiContext context, Rect area, boolean hovered) {
            card.width = area.width();
            card.height = area.height();
            drawCard(context.getGraphics(), context.getFont(), card, area.x(), area.y(), hovered, background);
        }
    }

    private static void withOverlayDepth(ModularGuiContext context, float depth, Runnable render) {
        GuiGraphics graphics = context.getGraphics();
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, depth);
        try { render.run(); graphics.flush(); }
        finally { graphics.pose().popPose(); }
    }

    private static Object cardKey(Card card) {
        return switch (card.kind) {
            case GROUP, SAMPLE -> List.of(card.kind, MarketKey.of(card.item));
            case INVENTORY -> List.of(card.kind, card.slot, MarketKey.of(card.item));
            case OWN -> List.of(card.kind, card.listing.id());
            case OWN_REQUEST -> List.of(card.kind, card.order.id());
            case FAVORITE -> List.of(card.kind, card.favorite.orderId(), card.favoriteIndex);
            case HISTORY -> List.of(card.kind, card.purchase.purchasedAt(), MarketKey.of(card.item));
            case DELIVERY -> List.of(card.kind, card.delivery.id());
            case COIN -> List.of(card.kind, card.credit.id());
        };
    }

    void prepareLayout(int width, int height, Font font) {
        publishSlot.visible(tab == Tab.SELL);
        window.layoutViewport(width, height, font);
        ClientMarketScreen.placeSearch(searchSlot.absoluteBounds());
    }

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
        Font font = context.getFont();
        int x = getArea().x;
        int y = getArea().y;
        int width = getArea().width;
        int height = getArea().height;
        if (width <= 0 || height <= 0) {
            return;
        }

        String search = ClientMarketScreen.searchText();
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (dirty || lastWidth != width || lastHeight != height
                || !Objects.equals(lastSearch, search) || !Objects.equals(lastLanguage, language)) {
            lastLanguage = language;
            rebuild(width, height, search);
        }
        prepareLayout(width, height, font);
        window.draw(context, x, y);
    }

    private void rebuild(int width, int height, String search) {
        lastWidth = width;
        lastHeight = height;
        lastSearch = search;
        dirty = false;

        List<Card> source = sourceCards();
        List<Dropdown.Option<String>> modOptions = new ArrayList<>();
        modOptions.add(new Dropdown.Option<>("all", Component.translatable("market.geg.all_mods")));
        ClientMarketState.summaries().stream().map(summary -> namespace(summary.item()))
                .filter(value -> !value.isEmpty())
                .distinct().sorted().forEach(value ->
                        modOptions.add(new Dropdown.Option<>(value, Component.literal(value))));
        filterDropdown.setOptions(modOptions);
        String filter = filterDropdown.selected();

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

        grid.invalidate();
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

    private void drawCard(GuiGraphics graphics, Font font, Card card, int x, int y,
                          boolean hover, Panel background) {
        boolean ended = card.kind == Kind.FAVORITE && card.favorite != null && card.favorite.ended();
        background.drawAt(getContext(), new Rect(x, y, card.width, card.height));
        if (ended) {
            graphics.fill(x, y, x + card.width, y + card.height, 0xA032383D);
        } else if (hover) {
            graphics.fill(x, y, x + card.width, y + card.height, 0x24FFFFFF);
        }
        if (!card.item.isEmpty()) {
            graphics.renderItem(card.item, x + 5, y + 6);
        }

        int textX = x + (card.item.isEmpty() ? 6 : 26);
        int textWidth = x + card.width - 5 - textX;
        Label nameLabel = new Label(card.title()).color(TEXT).wrap(Label.Wrap.CHARACTER)
                .overflow(Label.Overflow.CLIP).maxLines(2);
        boolean twoLines = nameLabel.measureHeight(font, textWidth) > 10;
        nameLabel.drawAt(getContext(), new Rect(textX, y + 5, textWidth, twoLines ? 20 : 10));
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
            drawText(count, textX, nextLineY, textWidth, 10, SECONDARY_TEXT);
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
            drawText(price, textX, nextLineY, textWidth, 10, ended ? SECONDARY_TEXT : ACCENT);
        }
    }

    private void drawText(Component text, int x, int y, int width, int height, int color) {
        Label.draw(getContext(), text, new Rect(x, y, Math.max(0, width), Math.max(0, height)),
                color, Label.Overflow.CLIP, Label.Horizontal.LEFT, Label.Vertical.TOP);
    }

    private void drawModal(int x, int y, int width, int height) {
        detailDialog().draw(getContext(), new Modal.Rect(x, y, width, height),
                content -> drawModalContent(getContext().getGraphics(), content));
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

    private void drawModalContent(GuiGraphics graphics, Modal.Rect content) {
        int contentX = content.x();
        int contentY = content.y();

        if (modal.kind == Kind.GROUP || modal.kind == Kind.FAVORITE) {
            graphics.fill(contentX + 2, contentY + 5, content.right() - 2, contentY + 22, BACKGROUND);
            drawText(Component.translatable("market.geg.status"), contentX + 6, contentY + 9, 88, 10, SECONDARY_TEXT);
            drawText(Component.translatable("market.geg.unit_price"), contentX + 99, contentY + 9, 96, 10, SECONDARY_TEXT);
            drawText(Component.translatable("market.geg.operation"), content.right() - 103, contentY + 9, 100, 10, SECONDARY_TEXT);
            if (modal.offers.isEmpty()) drawText(
                    Component.translatable(modal.quantity > 0 || modal.buyQuantity > 0
                            ? "market.geg.loading" : "market.geg.out_of_stock"),
                    contentX + 4, contentY + 32, content.width() - 8, 10, SECONDARY_TEXT);
            for (int index = selectedOffer; index < Math.min(selectedOffer + 5, modal.offers.size()); index++) {
                MarketDetail offer = modal.offers.get(index);
                int rowY = contentY + 24 + (index - selectedOffer) * 25;
                offerRowPanel.drawAt(getContext(), new Rect(contentX + 2, rowY,
                        content.width() - 4, 23));
                if (selectedDetailId == offer.id()) graphics.fill(contentX + 2, rowY,
                        content.right() - 2, rowY + 23, 0x55344F5E);
                Component status = Component.translatable(offer.buyOrder()
                        ? "market.geg.buying" : "market.geg.selling")
                        .copy().append(Component.literal(" x" + offer.quantity()));
                drawText(status, contentX + 6, rowY + 3, 88, 10, offer.buyOrder() ? ACCENT : TEXT);
                drawText(Component.literal(offer.ownerName()), contentX + 6, rowY + 13, 88, 10, SECONDARY_TEXT);
                drawText(Component.literal(MoneyCurrency.COIN.format(offer.unitPrice())),
                        contentX + 99, rowY + 8, 96, 10, TEXT);
                int actionX = content.right() - 104;
                Button.draw(getContext(), actionX, rowY + 2, 50, Button.Size.SMALL,
                        Component.translatable(offer.buyOrder()
                                ? "market.geg.fulfill" : "market.geg.buy"));
                boolean favorite = ClientMarketState.favorites().stream()
                        .anyMatch(value -> value.orderId() == offer.id());
                drawFavoriteToggleButton(actionX + 53, rowY + 2,
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
            drawText(info, contentX + 4, contentY + 22, content.width() - 8, 10, SECONDARY_TEXT);
        }

        ClientMarketScreen.drawDetailForm(getContext(), content, getArea().width);
    }

    private void drawQuantityMenu(int x, int y, int width, int height) {
        quantityDialog().draw(getContext(), new Modal.Rect(x, y, width, height), content -> {
            int quickTop = ClientMarketScreen.drawTradeForm(getContext(), content, width);
            String[] labels = {"-16", "-8", "+8", "+16", Component.translatable("market.geg.max").getString()};
            for (int index = 0; index < labels.length; index++) {
                Modal.Rect button = quickButton(content, quickTop, index);
                Button.draw(getContext(), button.x(), button.y(), button.width(), Button.Size.MEDIUM,
                        Component.literal(labels[index]));
            }
        });
    }

    private static Modal.Rect quickButton(Modal.Rect content, int top, int index) {
        int available = Math.max(1, content.width() - 8);
        int columns = Math.min(5, Math.max(1, (available + 2) / 30));
        int buttonWidth = Math.min(38, Math.max(1, (available - (columns - 1) * 2) / columns));
        int row = index / columns;
        int column = index % columns;
        return new Modal.Rect(content.x() + 4 + column * (buttonWidth + 2),
                top + row * (Button.Size.MEDIUM.height() + 2),
                buttonWidth, Button.Size.MEDIUM.height());
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

    private void drawFavoriteToggleButton(int x, int y,
                                          Component text, boolean selected) {
        ModularGuiContext context = getContext();
        SelectableTheme toggle = context.getTheme().getToggleButtonTheme()
                .getTheme(isHovered(x, y, FAVORITE_BUTTON_WIDTH, FAVORITE_BUTTON_HEIGHT));
        WidgetTheme style = selected ? toggle.getSelected() : toggle;
        IDrawable background = style.getBackground();
        if (background != null) {
            background.draw(context, x, y, FAVORITE_BUTTON_WIDTH, FAVORITE_BUTTON_HEIGHT, style);
        }
        Label.draw(context, text, new Rect(x + 4, y, FAVORITE_BUTTON_WIDTH - 8,
                        FAVORITE_BUTTON_HEIGHT), style.getTextColor(), style.isTextShadow(), Label.Overflow.CLIP,
                Label.Horizontal.CENTER, Label.Vertical.CENTER);
    }

    private boolean isHovered(int x, int y, int width, int height) {
        ModularGuiContext context = getContext();
        int mouseX = context.getAbsMouseX();
        int mouseY = context.getAbsMouseY();
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public @NotNull Interactable.Result onMousePressed(int button) {
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

        Rect filterBounds = filterSlot.screenBounds(x, y);
        String previousFilter = filterDropdown.selected();
        if (filterDropdown.click(filterBounds.x(), filterBounds.y(), filterBounds.width(),
                filterBounds.height(), mouseX, mouseY)) {
            if (!Objects.equals(previousFilter, filterDropdown.selected())) {
                grid.scrollToTop();
                dirty = true;
            }
            return Interactable.Result.STOP;
        }

        Rect footerBounds = footerSlot.screenBounds(x, y);
        int footerIndex = FOOTER_TABS.hitIndex(footerBounds.x(), footerBounds.y() + 4,
                footerBounds.width(), mouseX, mouseY);
        if (footerIndex >= 0) {
            Tab nextTab = Tab.values()[footerIndex];
            if (nextTab != tab) {
                tab = nextTab;
                grid.scrollToTop();
                filterDropdown.select("all");
                dirty = true;
            }
            return Interactable.Result.STOP;
        }

        if (tab == Tab.SELL) {
            Rect publishBounds = publishSlot.screenBounds(x, y);
            int publishIndex = PUBLISH_TABS.hitIndex(publishBounds.x() + 6, publishBounds.y() + 7,
                    Math.min(134, publishBounds.width() - 12), mouseX, mouseY);
            if (publishIndex >= 0) {
                requestMode = publishIndex == 1;
                return Interactable.Result.STOP;
            }
        }

        Grid.Entry<Card> selected = grid.hitItem(mouseX - x, mouseY - y);
        if (selected != null) {
            openModal(selected.item());
            return Interactable.Result.STOP;
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
            } else if (mouseX >= actionX + 53
                    && mouseX < actionX + 53 + FAVORITE_BUTTON_WIDTH) {
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
        int quickTop = ClientMarketScreen.tradeFormBottom();
        for (int index = 0; index < 5; index++) {
            if (quickButton(content, quickTop, index).contains(mouseX, mouseY)) {
                int current;
                try {
                    String raw = ClientMarketScreen.tradeQuantityText();
                    current = io.github.luyingyilong.geg.client.ui.DecimalExpression.evaluate(raw).intValueExact();
                } catch (IllegalArgumentException | ArithmeticException exception) { current = 1; }
                int[] deltas = {-16, -8, 8, 16};
                int max = maxTradeQuantity(offer);
                int next = index == 4 ? max : Math.max(1, Math.min(max,
                        (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, (long) current + deltas[index]))));
                ClientMarketScreen.setTradeQuantity(Math.max(1, next));
                return;
            }
        }
    }

    private void confirmQuantity() {
        MarketDetail offer = selectedDetail();
        if (offer == null) {
            closeQuantityMenu();
            return;
        }
        ClientMarketScreen.setTradeQuantityMaximum(maxTradeQuantity(offer));
        if (!ClientMarketScreen.validateTradeForm()) return;
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
        if (modal.kind == Kind.INVENTORY) {
            ClientMarketScreen.setDetailQuantityMaximum(requestMode
                    ? Integer.MAX_VALUE : (int) modal.quantity);
        } else if (modal.kind == Kind.SAMPLE) {
            ClientMarketScreen.setDetailQuantityMaximum(Integer.MAX_VALUE);
        }
        if ((modal.kind == Kind.INVENTORY || modal.kind == Kind.SAMPLE
                || modal.kind == Kind.OWN || modal.kind == Kind.OWN_REQUEST)
                && !ClientMarketScreen.validateDetailForm()) return;
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
        if (filterDropdown.scroll(delta)) {
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
        grid.scrollBy(-(int) Math.signum(delta) * 28);
        return true;
    }


    @Override
    public @NotNull Interactable.Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && modal != null) {
            if (quantityMenu) {
                quantityMenu = false;
                ClientMarketScreen.showTradeQuantity(false);
            } else closeModal();
            return Interactable.Result.STOP;
        }
        if (keyCode == 256 && filterDropdown.isOpen()) {
            filterDropdown.close();
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
