package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.debug.DebugBridge;

import brachy.modularui.api.widget.Interactable;
import io.github.luyingyilong.ore.render.DrawContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Dropdown;
import io.github.luyingyilong.ore.Form;
import io.github.luyingyilong.ore.Navbar;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.Modal;
import io.github.luyingyilong.ore.Segmented;
import io.github.luyingyilong.ore.Spin;
import io.github.luyingyilong.ore.Table;
import io.github.luyingyilong.ore.input.DecimalExpression;
import io.github.luyingyilong.ore.input.Input;
import io.github.luyingyilong.ore.input.InputControl;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Grid;
import io.github.luyingyilong.ore.layout.Overlay;
import io.github.luyingyilong.ore.layout.Padding;
import io.github.luyingyilong.ore.layout.Panel;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Scroll;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.theme.OreIcons;
import io.github.luyingyilong.ore.theme.OreTextMetrics;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.geg.market.*;
import io.github.luyingyilong.geg.market.MarketActionPacket;
import io.github.luyingyilong.geg.market.MarketBuyOrder;
import io.github.luyingyilong.geg.market.MarketDetail;
import io.github.luyingyilong.geg.market.MarketFavorite;
import io.github.luyingyilong.geg.market.MarketKey;
import io.github.luyingyilong.geg.market.MarketListing;
import io.github.luyingyilong.geg.market.MarketNetwork;
import io.github.luyingyilong.geg.market.MarketPurchase;
import io.github.luyingyilong.geg.market.MarketSummary;
import io.github.luyingyilong.geg.money.MoneyCurrency;
import io.github.luyingyilong.geg.money.client.ClientMoneyState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.IntConsumer;

final class MarketCanvasWidget extends Widget<MarketCanvasWidget> implements Interactable {
    private static final int ACCENT = OreTheme.TEXT_ACCENT;
    private static final int TEXT = OreTheme.TEXT_WHITE;
    private static final int HEADER_TEXT = OreTheme.TEXT_DARK;
    private static final int SECONDARY_TEXT = OreTheme.TEXT_SECONDARY;
    private static final int CARD_GAP = 4;
    private static final int MIN_CARD_WIDTH = 96;
    private static final int FOOTER_HEIGHT = Button.Size.MEDIUM.getHeight();
    private static final int FAVORITE_BUTTON_WIDTH = 51;
    private final Segmented footerOptions = new Segmented(Arrays.stream(Tab.values())
            .map(Tab::title).toList());
    private final Segmented publishOptions = new Segmented(List.of(
            Component.translatable("market.geg.selling"),
            Component.translatable("market.geg.buying")), Button.Size.SMALL);
    private final List<Card> cards = new ArrayList<>();
    private final Dropdown<String> filterDropdown = new Dropdown<>("all", 8)
            .setTriggerLabel(Component.empty()).setIcon(OreIcons.FILTER, 16, 16)
            .setShowArrow(false).setType(Button.Type.TEXT);
    private final Window window = (Window) new Window(true).setDebugName("Market Window");
    private final Grid<Card> grid = new Grid<>();
    private final Scroll gridScroll = new Scroll().setContent(grid);
    private final BodyContent body = new BodyContent();
    private final Table<MarketDetail> offerTable = createOfferTable();
    private final DetailContent detailContent = new DetailContent();
    private final QuantityContent quantityContent = new QuantityContent(this::adjustTradeQuantity);
    private final Modal detailModal = Signals.configure(new Modal(Component.empty()).setSize(340, 224).centered()
            .setContent(detailContent), connected -> connected.closeRequested().connect(connected, this::closeModal));
    private final Modal quantityModal = Signals.configure(new Modal(Component.empty()).setSize(224, 0).centered()
            .setContent(quantityContent), connected -> connected.closeRequested().connect(connected, this::closeQuantityMenu)).setDepth(300.0F);
    private final Navbar navbar = new Navbar(Component.translatable("market.geg.title"))
            .setTitleColor(HEADER_TEXT);
    private final InputControl searchSlot;
    private final Label coinBalance = new Label().setColor(HEADER_TEXT)
            .setOverflow(Label.Overflow.ELLIPSIS).setMaximumWidth(128)
            .setAlignment(Label.Horizontal.RIGHT, Label.Vertical.CENTER);
    private final Padding footerSlot = new Padding(new Padding.Insets(4, 0, 0, 0)).setChild(footerOptions);
    private final Label publishFee = new Label().setColor(SECONDARY_TEXT)
            .setOverflow(Label.Overflow.CLIP).setAlignment(Label.Horizontal.RIGHT, Label.Vertical.CENTER);
    private final Padding publishSlot;
    {
        var publishRow = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(6);
        publishRow.addChild(publishOptions);
        publishRow.addChild(publishFee);
        publishSlot = new Padding(new Padding.Insets(7, 6, 2, 6)).setChild(publishRow);
    }
    private final Overlay overlay = new Overlay();
    private Tab tab = Tab.MARKET;
    private Card modal;
    private String lastSearch = "";
    private String lastLanguage = "";
    private int lastWidth = -1;
    private int lastHeight = -1;
    private long selectedDetailId;
    private boolean quantityMenu;
    private boolean requestMode;
    private Card requestSample;
    private boolean dirty = true;

    MarketCanvasWidget(Input search) {
        searchSlot = new InputControl(search);
        coinBalance.setMinimum(32, Button.Size.MEDIUM.getHeight());
        searchSlot.setMinimum(64, Button.Size.SMALL.getHeight()).setPreferred(138, Button.Size.SMALL.getHeight())
                .setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        filterDropdown.setMinimum(Button.Size.MEDIUM.getHeight(), Button.Size.MEDIUM.getHeight());
        Box spaceCompactBox = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(0);
        spaceCompactBox.addChild(searchSlot);
        spaceCompactBox.addChild(filterDropdown);
        navbar.setExtra(coinBalance, spaceCompactBox);
        publishSlot.setMinimum(0, 27).setPreferred(0, 27)
                .setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        publishOptions.setMinimum(96, Button.Size.SMALL.getHeight()).setPreferred(134, Button.Size.SMALL.getHeight())
                .setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        publishFee.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
        footerSlot.setMinimum(0, FOOTER_HEIGHT + 4).setPreferred(0, FOOTER_HEIGHT + 4)
                .setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        footerOptions.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
        footerOptions.selectionChanged().connect(footerOptions, index -> {
            Tab next = Tab.values()[index];
            if (next != tab) {
                tab = next;
                gridScroll.scrollToTop();
                filterDropdown.select("all");
                dirty = true;
            }
        });
        publishOptions.selectionChanged().connect(publishOptions, index -> {
            requestMode = index == 1;
            dirty = true;
        });
        filterDropdown.itemActivated().connect(filterDropdown, value -> {
            gridScroll.scrollToTop();
            dirty = true;
        });
        grid.itemActivated().connect(grid, this::openModal);
        body.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Box column = new Box();
        column.addChild(navbar);
        column.addChild(publishSlot);
        column.addChild(body);
        column.addChild(footerSlot);
        window.setContent(column).setOverlay(overlay);
        grid.setColumns(MIN_CARD_WIDTH, 4, CARD_GAP).setMode(Grid.LayoutMode.MASONRY)
                .setAdapter(new Grid.Adapter<>() {
                    @Override
                    public int getSize() {
                        return cards.size();
                    }

                    @Override
                    public Card getItem(int index) {
                        return cards.get(index);
                    }

                    @Override
                    public Object getKey(Card card) {
                        return cardKey(card);
                    }

                    @Override
                    public int measureHeight(Card card, int cardWidth, Font font) {
                        boolean hasPrice = card.kind == Kind.GROUP || card.kind == Kind.OWN
                                || card.kind == Kind.OWN_REQUEST || card.kind == Kind.FAVORITE
                                || card.kind == Kind.HISTORY;
                        int lineHeight = OreTextMetrics.body().lineHeight(1);
                        return (hasPrice ? 3 : 2) * (lineHeight + 1) + 9;
                    }

                    @Override
                    public Grid.View<Card> createView() {
                        return new CardView();
                    }
                });
        overlay.addChild(detailModal);
        overlay.addChild(quantityModal);
        detailModal.setVisible(false);
        quantityModal.setVisible(false);
    }

    private static Label scrollingCardLabel(int color) {
        return new Label(Component.empty()).setColor(color).setOverflow(Label.Overflow.MARQUEE)
                .setScrollTrigger(Label.ScrollTrigger.HOVER).setScrollSpeed(20.0F)
                .setScrollEndPauseMs(700L);
    }

    private static void withOverlayDepth(DrawContext context, float depth, Runnable render) {
        GuiGraphics graphics = context.getGraphics();
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, depth);
        try {
            render.run();
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private static Object cardKey(Card card) {
        return switch (card.kind) {
            case GROUP, SAMPLE -> List.of(card.kind, MarketKey.of(card.item));
            case INVENTORY -> List.of(card.kind, card.slot, MarketKey.of(card.item));
            case OWN -> List.of(card.kind, card.listing.id());
            case OWN_REQUEST -> List.of(card.kind, card.order.id());
            case FAVORITE -> List.of(card.kind, card.favorite.orderId(), card.favoriteIndex);
            case HISTORY -> List.of(card.kind, card.purchase.purchasedAt(), MarketKey.of(card.item));
        };
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

    void bindForms(Form detailForm, Form tradeForm) {
        detailContent.bindForm(detailForm);
        quantityContent.bindForm(tradeForm);
    }

    void prepareLayout(int width, int height, Font font) {
        publishSlot.setVisible(tab == Tab.SELL);
        publishOptions.setSelectedNoSignal(requestMode ? 1 : 0);
        publishFee.setVisible(width >= 230);
        publishFee.setText(Component.translatable("market.geg.seller_fee",
                MoneyCurrency.COIN.format(ClientMarketState.feeBasisPoints())));
        navbar.setTitle(Component.translatable(ClientMarketState.marketEnabled()
                ? "market.geg.title" : "market.geg.title_closed"));
        coinBalance.setText(Component.translatable("market.geg.header_balance",
                MoneyCurrency.COIN.format(ClientMoneyState.balance().coinMinorUnits())));
        body.refresh();
        detailModal.setVisible(modal != null);
        quantityModal.setVisible(modal != null && quantityMenu);
        if (modal != null) {
            updatePublishQuantityLimit();
            detailModal.setTitle(modal.title());
            detailContent.refresh();
            configureDetailActions();
        }
        if (quantityMenu) {
            MarketDetail offer = selectedDetail();
            ClientMarketScreen.setTradeQuantityMaximum(maxTradeQuantity(offer));
            quantityModal.setTitle(offer != null && offer.buyOrder()
                    ? Component.translatable("market.geg.fulfill_title_fee",
                    MoneyCurrency.COIN.format(ClientMarketState.feeBasisPoints()))
                    : Component.translatable("market.geg.quantity"));
        }
        window.layoutViewport(width, height, font);
        footerOptions.setSelectedNoSignal(tab.ordinal());
    }

    void invalidate() {
        dirty = true;
        if (modal == null) {
            return;
        }
        Card previous = modal;
        modal = sourceCards().stream().filter(card -> sameCard(previous, card)).findFirst().orElse(null);
        if (modal == null) {
            closeModal();
        } else if (!modal.offers.isEmpty()) {
            if (selectedDetailId != 0L && modal.offers.stream().noneMatch(offer -> offer.id() == selectedDetailId)) {
                quantityMenu = false;
                ClientMarketScreen.showTradeQuantity(false);
            }
        } else if (modal.kind == Kind.GROUP || modal.kind == Kind.FAVORITE) {
            quantityMenu = false;
            ClientMarketScreen.showFields(false, false, 0, 0L);
        }
        if (modal != null) offerTable.setRows(modal.offers);
    }

    @Override
    public void draw(brachy.modularui.screen.viewport.ModularGuiContext mui, WidgetThemeEntry<?> theme) {
        try (DrawContext frame = io.github.luyingyilong.ore.window.MuiDrawing.begin(mui, window.getContext())) {
            drawFrame(frame, theme);
        }
    }

    private void drawFrame(DrawContext context, WidgetThemeEntry<?> theme) {
        if (!window.isAlive()) return;
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
        window.prepareTree();
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
        String filter = filterDropdown.getSelected();

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
        }
        return result;
    }

    private void drawText(DrawContext context, Component text, int x, int y, int width, int height, int color) {
        Label.draw(context, text, new Rect(x, y, Math.max(0, width), Math.max(0, height)),
                color, Label.Overflow.CLIP, Label.Horizontal.LEFT, Label.Vertical.TOP);
    }

    private Table<MarketDetail> createOfferTable() {
        Table<MarketDetail> table = new Table<>();
        Signals.configure(table.setRowKey(offer -> offer.id()).setHeaderColor(SECONDARY_TEXT), connected -> connected.rowActivated().connect(connected, offer -> selectedDetailId = offer.id()));
        table.addColumn(new Table.Column<>("status", Component.translatable("market.geg.status"),
                110, 110, 1, width -> 20, new Table.Cell<>() {
            @Override
            public void draw(DrawContext context, MarketDetail offer, Rect area) {
                int top = area.y() + (area.height() >= 23 ? 3 : 0);
                Component status = Component.translatable(offer.buyOrder()
                                ? "market.geg.buying" : "market.geg.selling")
                        .copy().append(Component.literal(" x" + offer.quantity()));
                drawText(context, status, area.x() + 4, top, area.width() - 8, 10,
                        offer.buyOrder() ? ACCENT : TEXT);
                drawText(context, Component.literal(offer.ownerName()), area.x() + 4, top + 10,
                        area.width() - 8, 10, SECONDARY_TEXT);
            }
        }));
        table.addColumn(new Table.Column<>("price", Component.translatable("market.geg.unit_price"),
                96, 96, 0, width -> 10, new Table.Cell<>() {
            @Override
            public void draw(DrawContext context, MarketDetail offer, Rect area) {
                drawText(context, Component.literal(MoneyCurrency.COIN.format(offer.unitPrice())),
                        area.x() + 4, area.y() + Math.max(0, (area.height() - 10) / 2),
                        area.width() - 8, 10, TEXT);
            }
        }));
        table.addColumn(new Table.Column<>("action", Component.translatable("market.geg.operation"),
                104, 104, 0, width -> width >= 104 ? Button.Size.SMALL.getHeight()
                        : Button.Size.SMALL.getHeight() * 2 + 2, new Table.ControlCell<>(OfferActions::new, OfferActions::bind)));

        return table;
    }

    private void toggleOfferFavorite(MarketDetail offer) {
        int favoriteIndex = -1;
        for (int index = 0; index < ClientMarketState.favorites().size(); index++) {
            if (ClientMarketState.favorites().get(index).orderId() == offer.id()) {
                favoriteIndex = index;
                break;
            }
        }
        if (favoriteIndex >= 0) send(MarketActionPacket.Action.FAVORITE_REMOVE,
                0L, 0L, 0L, 0, favoriteIndex);
        else send(MarketActionPacket.Action.FAVORITE_ADD,
                offer.id(), 0L, 0L, 0, 0);
    }

    private String actionSignature = "";

    private void configureDetailActions() {
        String signature = modal.kind + ":" + requestMode + ":"
                + (modal.favorite != null && modal.favorite.ended());
        if (signature.equals(actionSignature)) return;
        actionSignature = signature;
        detailModal.clearActions();
        if (modal.kind == Kind.GROUP || modal.kind == Kind.FAVORITE) {
            if (modal.kind == Kind.FAVORITE && modal.favorite.ended())
                detailModal.addAction(Signals.configure(new Button(Component.translatable("market.geg.unfavorite")).setType(Button.Type.SECONDARY), actionButton -> actionButton.pressed().connect(actionButton, this::secondaryModalAction)));
            detailModal.addAction(Signals.configure(new Button(Component.translatable("market.geg.request_this")).setType(Button.Type.PRIMARY), actionButton -> actionButton.pressed().connect(actionButton, this::primaryModalAction)));
        } else if (modal.kind == Kind.OWN || modal.kind == Kind.OWN_REQUEST) {
            detailModal.addAction(Signals.configure(new Button(Component.translatable("market.geg.cancel_listing")).setType(Button.Type.DESTRUCTIVE), actionButton -> actionButton.pressed().connect(actionButton, this::secondaryModalAction)));
            detailModal.addAction(Signals.configure(new Button(Component.translatable("market.geg.reprice")).setType(Button.Type.PRIMARY), actionButton -> actionButton.pressed().connect(actionButton, this::primaryModalAction)));
        } else if (modal.kind == Kind.INVENTORY || modal.kind == Kind.SAMPLE) {
            detailModal.addAction(Signals.configure(new Button(Component.translatable(requestMode
                    ? "market.geg.confirm_request" : "market.geg.confirm_list")).setType(Button.Type.PRIMARY), actionButton -> actionButton.pressed().connect(actionButton, this::primaryModalAction)));
        }
    }

    private void configureQuantityActions() {
        MarketDetail offer = selectedDetail();
        quantityModal.clearActions().addAction(Signals.configure(new Button(Component.translatable("market.geg.cancel")).setType(Button.Type.SECONDARY), actionButton -> actionButton.pressed().connect(actionButton, this::closeQuantityMenu)))
                .addAction(Signals.configure(new Button(Component.translatable(offer != null && offer.buyOrder()
                        ? "market.geg.confirm_fulfill" : "market.geg.confirm_buy")).setType(Button.Type.PRIMARY), actionButton -> actionButton.pressed().connect(actionButton, this::confirmQuantity)));
    }

    private void closeQuantityMenu() {
        quantityMenu = false;
        ClientMarketScreen.showTradeQuantity(false);
    }

    /** Each virtual row retains two real Buttons, with callbacks rebound to the current order. */
    private final class OfferActions extends Container {
        private MarketDetail offer;
        private final Button trade = Signals.configure(new Button().setSize(Button.Size.SMALL), connected -> connected.pressed().connect(connected, () -> {
            selectedDetailId = offer.id();
            quantityMenu = true;
            quantityContent.scroll = 0;
            ClientMarketScreen.showTradeQuantity(true);
            ClientMarketScreen.setTradeQuantityMaximum(maxTradeQuantity(offer));
            configureQuantityActions();
        }));
        private final Button favorite = Signals.configure(new Button().setSize(Button.Size.SMALL).setToggleButton(true), connected -> connected.pressed().connect(connected, () -> toggleOfferFavorite(offer)));

        private OfferActions() { this.addChild(trade); this.addChild(favorite); }

        private void bind(MarketDetail offer) {
            this.offer = offer;
            trade.setLabel(Component.translatable(offer.buyOrder() ? "market.geg.fulfill" : "market.geg.buy"));
            syncFavorite();
        }

        private void syncFavorite() {
            boolean selected = ClientMarketState.favorites().stream()
                    .anyMatch(value -> value.orderId() == offer.id());
            favorite.setButtonPressedNoSignal(selected).setLabel(Component.translatable(selected
                    ? "market.geg.favorited" : "market.geg.favorite"));
        }

        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            Rect[] areas = OfferActionLayout.bounds(new Rect(0, 0, bounds.width(), bounds.height()),
                    50, FAVORITE_BUTTON_WIDTH);
            fitChild(trade, areas[0], font);
            fitChild(favorite, areas[1], font);
        }

        @Override protected void render(DrawContext context, int screenX, int screenY) {
            syncFavorite();
        }
    }

    @Override public @NotNull Interactable.Result onMousePressed(int button) {
        return io.github.luyingyilong.ore.event.WindowInputs.press(window,getContext().getAbsMouseX() - getArea().x,getContext().getAbsMouseY() - getArea().y,getContext(),button)
                ?Interactable.Result.SUCCESS:Interactable.Result.IGNORE;
    }

    private MarketDetail selectedDetail() {
        if (modal == null) return null;
        return modal.offers.stream().filter(offer -> offer.id() == selectedDetailId).findFirst().orElse(null);
    }

    private int maxTradeQuantity(MarketDetail offer) {
        if (offer == null || offer.quantity() <= 0 || offer.unitPrice() <= 0L) return 0;
        if (!offer.buyOrder()) {
            return (int) Math.min(offer.quantity(),
                    Math.max(0L, ClientMoneyState.balance().coinMinorUnits()) / offer.unitPrice());
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        MarketKey key = MarketKey.of(offer.item());
        long count = 0L;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!item.isEmpty() && MarketKey.of(item).equals(key)) count += item.getCount();
            if (count >= offer.quantity()) return offer.quantity();
        }
        return (int) Math.min(offer.quantity(), count);
    }

    private void adjustTradeQuantity(int index) {
        MarketDetail offer = selectedDetail();
        if (offer == null) {
            closeQuantityMenu();
            return;
        }
        int current;
        try {
            String raw = ClientMarketScreen.tradeQuantityText();
            current = DecimalExpression.evaluate(raw).intValueExact();
        } catch (IllegalArgumentException | ArithmeticException exception) {
            current = 1;
        }
        int[] deltas = {-16, -8, 8, 16};
        int max = maxTradeQuantity(offer);
        int next = index == 4 ? max : Math.max(1, Math.min(max,
                (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, (long) current + deltas[index]))));
        ClientMarketScreen.setTradeQuantity(Math.max(1, next));
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
            MarketFeedback.show(MarketResult.INVALID);
        }
    }

    private void openModal(Card card) {
        // Overlay focus scopes block the navbar input; disabling it would also hide its MUI rendering.
        modal = card;
        detailContent.scroll = 0;
        offerTable.scrollToTop();
        selectedDetailId = 0L;
        quantityMenu = false;
        switch (card.kind) {
            case GROUP, FAVORITE -> {
                ClientMarketScreen.showFields(false, false, 0, 0L);
                if (card.detailId != 0L) {
                    card.offers.clear();
                    ClientMarketState.requestDetails(card.detailId, card.item);
                }
            }
            case INVENTORY, SAMPLE -> {
                ClientMarketScreen.showFields(true, true, 1,
                        MarketPrices.suggestedUnitPrice(card.item, ClientMarketState.summaries()));
                updatePublishQuantityLimit();
            }
            case OWN, OWN_REQUEST -> ClientMarketScreen.showFields(false, true, 0, card.price);
            default -> ClientMarketScreen.showFields(false, false, 0, 0L);
        }
        offerTable.setRows(card.offers);
    }

    private void closeModal() {
        modal = null;
        offerTable.setRows(List.of());
        offerTable.scrollToTop();
        quantityMenu = false;
        ClientMarketScreen.showTradeQuantity(false);
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

    private void updatePublishQuantityLimit() {
        if (modal == null) return;
        if (modal.kind == Kind.INVENTORY) {
            ClientMarketScreen.setDetailQuantityMaximum(requestMode
                    ? Integer.MAX_VALUE : (int) modal.quantity);
        } else if (modal.kind == Kind.SAMPLE) {
            ClientMarketScreen.setDetailQuantityMaximum(Integer.MAX_VALUE);
        }
    }

    private void primaryModalAction() {
        updatePublishQuantityLimit();
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
                default -> {
                }
            }
            closeModal();
        } catch (IllegalArgumentException exception) {
            MarketFeedback.show(MarketResult.INVALID);
        }
    }

    @Override public @NotNull Interactable.Result onCharTyped(char character,int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.text(window,String.valueOf(character),modifiers,getContext())
                ?Interactable.Result.SUCCESS:Interactable.Result.IGNORE;
    }
    @Override public boolean onMouseScrolled(double delta) {
        return io.github.luyingyilong.ore.event.WindowInputs.scroll(window,getContext().getAbsMouseX() - getArea().x,getContext().getAbsMouseY() - getArea().y,delta,net.minecraft.client.gui.screens.Screen.hasShiftDown());
    }

    @Override public void onMouseDrag(int button,double x,double y) {
        io.github.luyingyilong.ore.event.WindowInputs.pointer(window,io.github.luyingyilong.ore.event.PointerEvent.Action.MOVE,getContext().getAbsMouseX() - getArea().x,getContext().getAbsMouseY() - getArea().y,button);
    }

    @Override public boolean onMouseReleased(int button) {
        return io.github.luyingyilong.ore.event.WindowInputs.pointer(window,io.github.luyingyilong.ore.event.PointerEvent.Action.RELEASE,getContext().getAbsMouseX() - getArea().x,getContext().getAbsMouseY() - getArea().y,button);
    }

    @Override public boolean onKeyReleased(int keyCode,int scanCode,int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.key(window,io.github.luyingyilong.ore.event.KeyEvent.Action.RELEASE,keyCode,scanCode,modifiers,getContext());
    }

    @Override public void dispose() {
        window.hostDetached();
        window.cancelInteractions();
        super.dispose();
    }

    @Override public @NotNull Interactable.Result onKeyPressed(int keyCode,int scanCode,int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.key(window,io.github.luyingyilong.ore.event.KeyEvent.Action.PRESS,keyCode,scanCode,modifiers,getContext())
                ?Interactable.Result.SUCCESS:Interactable.Result.IGNORE;
    }

    private final class BodyContent extends Container {
        private final Padding inset = new Padding(new Padding.Insets(4, 4, 0, 4)).setChild(gridScroll);
        private final Spin loadingSpin = new Spin();
        private final Label empty = new Label().setColor(SECONDARY_TEXT)
                .setOverflow(Label.Overflow.CLIP)
                .setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER);
        private final Box status = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
        {
            status.addChild(loadingSpin);
            status.addChild(empty);
        }

        private BodyContent() {
            this.addChild(inset);
            this.addChild(status);
        }

        private void refresh() {
            status.setVisible(cards.isEmpty());
            loadingSpin.setVisible(!ClientMarketState.ready());
            empty.setText(Component.translatable(ClientMarketState.ready()
                    ? "market.geg.empty" : "market.geg.loading"));
        }

        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            fitChild(inset, new Rect(0, 0, bounds.width(), bounds.height()), font);
            int statusWidth = Math.min(bounds.width(), status.measureWidth(font, bounds.width()));
            fitChild(status, new Rect(Math.max(0, (bounds.width() - statusWidth) / 2),
                    18, statusWidth, 20), font);
        }
    }

    private abstract static class ScrollableContent extends Container {
        protected int scroll;
        protected int contentHeight;

        public void scrollBy(int amount) {
            scroll = Math.max(0, Math.min(Math.max(0, contentHeight - getBounds().height()), scroll + amount));
        }

        @Override public Control hitTop(int x,int y) {
            return getAbsoluteBounds().contains(x,y)?super.hitTop(x,y):null;
        }
        @Override protected boolean wheel(int x,int y,double delta,boolean horizontal) {
            if(horizontal)return false;int before=scroll;scrollBy(-(int)Math.signum(delta)*24);return before!=scroll;
        }

        @Override public void draw(DrawContext context, int screenX, int screenY) {
            Rect area = getScreenBounds(screenX, screenY);
            if (area.width() <= 0 || area.height() <= 0) return;
            context.getGraphics().enableScissor(area.x(), area.y(), area.right(), area.bottom());
            super.draw(context, screenX, screenY);
            context.getGraphics().disableScissor();
        }
    }

    private final class DetailContent extends ScrollableContent {
        private final MarketDetailDescriptions info = new MarketDetailDescriptions();
        private final Spin loadingSpin = new Spin();
        private final Label loadingLabel = new Label(Component.translatable("market.geg.loading"))
                .setColor(SECONDARY_TEXT).setOverflow(Label.Overflow.CLIP)
                .setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER);
        private final Box loadingRow = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
        {
            loadingRow.addChild(loadingSpin);
            loadingRow.addChild(loadingLabel);
        }
        private Form form;

        private DetailContent() {
            this.addChild(offerTable);
            this.addChild(loadingRow);
            this.addChild(info);
        }

        private void bindForm(Form form) {
            this.form = form;
            this.addChild(form);
        }

        private void refresh() {
            boolean offers = modal.kind == Kind.GROUP || modal.kind == Kind.FAVORITE;
            offerTable.setVisible(offers);
            info.setVisible(!offers);
            boolean loading = offers && modal.offers.isEmpty()
                    && ClientMarketState.isDetailLoading(modal.item);
            loadingRow.setVisible(loading);
            if (offers) {
                offerTable.setEmpty(loading ? Component.empty()
                        : Component.translatable("market.geg.out_of_stock"));
                return;
            }
            Map<MarketDetailDescriptions.Field, Component> values = new EnumMap<>(MarketDetailDescriptions.Field.class);
            switch (modal.kind) {
                case OWN -> {
                    values.put(MarketDetailDescriptions.Field.STATUS, Component.translatable("market.geg.selling"));
                    values.put(MarketDetailDescriptions.Field.SELLER, Component.literal(modal.listing.sellerName()));
                    values.put(MarketDetailDescriptions.Field.QUANTITY, Component.literal(Long.toString(modal.quantity)));
                    values.put(MarketDetailDescriptions.Field.PRICE, Component.literal(MoneyCurrency.COIN.format(modal.price)));
                }
                case OWN_REQUEST -> {
                    values.put(MarketDetailDescriptions.Field.STATUS, Component.translatable("market.geg.buying"));
                    values.put(MarketDetailDescriptions.Field.QUANTITY, Component.literal(Long.toString(modal.quantity)));
                    values.put(MarketDetailDescriptions.Field.PRICE, Component.literal(MoneyCurrency.COIN.format(modal.price)));
                    values.put(MarketDetailDescriptions.Field.ESCROW, Component.literal(MoneyCurrency.COIN.format(modal.order.escrow())));
                }
                case INVENTORY, SAMPLE -> values.put(MarketDetailDescriptions.Field.INVENTORY, Component.literal(Long.toString(modal.quantity)));
                case HISTORY -> {
                    values.put(MarketDetailDescriptions.Field.SELLER, Component.literal(modal.purchase.sellerName()));
                    values.put(MarketDetailDescriptions.Field.QUANTITY, Component.literal(Long.toString(modal.quantity)));
                    values.put(MarketDetailDescriptions.Field.PRICE, Component.literal(MoneyCurrency.COIN.format(modal.price)));
                }
                default -> { }
            }
            if (modal.kind == Kind.OWN || modal.kind == Kind.INVENTORY && !requestMode) {
                values.put(MarketDetailDescriptions.Field.FEE, Component.literal(
                        MoneyCurrency.COIN.format(ClientMarketState.feeBasisPoints()) + "%"));
            }
            info.show(values);
        }

        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            int width = bounds.width();
            int height = bounds.height();
            if (offerTable.getVisible()) {
                scroll = 0;
                contentHeight = height;
                fitChild(offerTable, new Rect(0, 0, width, height), font);
                int loadingWidth = Math.min(Math.max(0, width - 4),
                        loadingRow.measureWidth(font, width));
                fitChild(loadingRow, new Rect(2, offerTable.getHeaderHeight() + 8,
                        loadingWidth, 12), font);
                return;
            }
            int infoHeight = info.measureSize(font, new Size(width, height)).height();
            int formTop = infoHeight + 6;
            int formHeight = form != null && form.getVisible()
                    ? form.measureSize(font, new Size(width, height)).height() : 0;
            contentHeight = formHeight > 0 ? formTop + formHeight : infoHeight;
            scroll = Math.min(scroll, Math.max(0, contentHeight - height));
            fitChild(info, new Rect(0, -scroll, width, infoHeight), font);
            if (form != null && form.getVisible())
                fitChild(form, new Rect(0, formTop - scroll, width, formHeight), font);
            if (form != null && form.getVisible()) form.clipInputs(getAbsoluteBounds());
        }
    }

    static final class QuantityContent extends ScrollableContent {
        private static final int QUICK_BUTTON_GAP = 2;
        private static final int QUICK_BUTTON_MIN_WIDTH = 30;
        private static final int QUICK_BUTTON_ROW_HEIGHT = Button.Size.MEDIUM.getHeight() + QUICK_BUTTON_GAP;
        private final List<Button> quickButtons = new ArrayList<>();
        private Form form;

        QuantityContent(IntConsumer adjustQuantity) {
            String[] labels = {"-16", "-8", "+8", "+16"};
            for (int index = 0; index < 5; index++) {
                final int buttonIndex = index;
                Button button = Signals.configure(new Button(index == 4
                        ? Component.translatable("market.geg.max") : Component.literal(labels[index])), connected -> connected.pressed().connect(connected, () -> adjustQuantity.accept(buttonIndex)));
                quickButtons.add(button);
                this.addChild(button);
            }
        }

        void bindForm(Form form) {
            this.form = form;
            this.addChild(form);
        }

        private int columns(int width) {
            return Math.min(quickButtons.size(), Math.max(1,
                    (Math.max(1, width) + QUICK_BUTTON_GAP) / (QUICK_BUTTON_MIN_WIDTH + QUICK_BUTTON_GAP)));
        }

        private int contentHeight(int formHeight, int columns) {
            int rows = (quickButtons.size() + columns - 1) / columns;
            return formHeight + 4 + rows * QUICK_BUTTON_ROW_HEIGHT - QUICK_BUTTON_GAP;
        }

        @Override public Size measureSize(Font font, Size available) {
            int width = Math.max(getMinimumWidth(), available.width());
            int formHeight = form == null ? 0 : form.measureSize(font, new Size(width, available.height())).height();
            return new Size(width, Math.max(getMinimumHeight(), contentHeight(formHeight, columns(width))));
        }

        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            int width = bounds.width();
            int height = bounds.height();
            int formHeight = form == null ? 0
                    : form.measureSize(font, new Size(width, height)).height();
            int available = Math.max(1, width);
            int columns = columns(width);
            int buttonTop = formHeight + 4;
            contentHeight = contentHeight(formHeight, columns);
            scroll = Math.min(scroll, Math.max(0, contentHeight - height));
            if (form != null) fitChild(form, new Rect(0, -scroll, width, formHeight), font);
            if (form != null) form.clipInputs(getAbsoluteBounds());
            for (int index = 0; index < quickButtons.size(); index++) {
                int row = index / columns;
                int column = index % columns;
                int buttonsInRow = Math.min(columns, quickButtons.size() - row * columns);
                int rowWidth = Math.max(buttonsInRow, available - (buttonsInRow - 1) * QUICK_BUTTON_GAP);
                int left = column * rowWidth / buttonsInRow + column * QUICK_BUTTON_GAP;
                int right = (column + 1) * rowWidth / buttonsInRow + column * QUICK_BUTTON_GAP;
                fitChild(quickButtons.get(index), new Rect(left,
                        buttonTop + row * QUICK_BUTTON_ROW_HEIGHT - scroll,
                        right - left, Button.Size.MEDIUM.getHeight()), font);
            }
        }

    }


    private enum Tab {
        MARKET("market"),
        MINE("mine"),
        SELL("sell"),
        FAVORITES("favorites"),
        HISTORY("history"),
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
    }

    private static final class Card {
        private final Kind kind;
        private final ItemStack item;
        private final List<MarketDetail> offers = new ArrayList<>();
        private MarketListing listing;
        private MarketBuyOrder order;
        private MarketFavorite favorite;
        private MarketPurchase purchase;
        private long detailId;
        private int slot = -1;
        private int favoriteIndex = -1;
        private long quantity;
        private long price;
        private long buyQuantity;

        private Card(Kind kind, ItemStack item) {
            this.kind = kind;
            this.item = item.copy();
        }

        private Component title() {
            return switch (kind) {
                case OWN_REQUEST -> Component.translatable("market.geg.buying_item", item.getHoverName());
                case OWN -> Component.translatable("market.geg.selling_item", item.getHoverName());
                default -> item.getHoverName();
            };
        }
    }

    private final class CardView extends Container implements Grid.View<Card> {
        private final Panel background = new Panel(null);
        private final Label name = scrollingCardLabel(TEXT);
        private final Label count = scrollingCardLabel(SECONDARY_TEXT);
        private final Label price = scrollingCardLabel(ACCENT);
        private final Slot icon = new Slot().setBackgroundVisible(false);
        private final Box textColumn = new Box().setGap(1);
        private final Box row = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(5);
        private Card card;

        private CardView() {
            name.setMinimum(0, 10).setPreferred(0, 10).setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
            count.setMinimum(0, 10).setPreferred(0, 10).setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
            price.setMinimum(0, 10).setPreferred(0, 10).setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
            icon.setMinimum(16, 20).setPreferred(16, 20).setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_CENTER).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
            textColumn.addChild(name);
            textColumn.addChild(count);
            textColumn.addChild(price);
            textColumn.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
            row.addChild(icon);
            row.addChild(textColumn);
            this.addChild(row);
        }

        @Override
        public void bind(Card card) {
            this.card = card;
            name.setText(card.title());
            icon.setItem(card.item, 1);
            icon.setVisible(!card.item.isEmpty());
            count.setText(switch (card.kind) {
                case GROUP -> Component.translatable("market.geg.market_count", card.quantity, card.buyQuantity);
                case OWN, OWN_REQUEST, FAVORITE -> Component.translatable("market.geg.count", card.quantity);
                case INVENTORY -> Component.translatable("market.geg.inventory_count", card.quantity);
                case SAMPLE -> Component.translatable("market.geg.sample");
                case HISTORY -> Component.translatable("market.geg.bought_count", card.quantity);
            });
            boolean ended = card.kind == Kind.FAVORITE && card.favorite != null && card.favorite.ended();
            Component priceText = switch (card.kind) {
                case GROUP, OWN, OWN_REQUEST, FAVORITE, HISTORY -> card.quantity == 0
                        ? card.kind == Kind.GROUP && card.buyQuantity > 0 ? Component.empty()
                        : Component.translatable(ended ? "market.geg.ended" : "market.geg.out_of_stock")
                        : Component.translatable("market.geg.price", MoneyCurrency.COIN.format(card.price));
                default -> Component.empty();
            };
            price.setText(priceText).setColor(ended ? SECONDARY_TEXT : ACCENT);
            price.setVisible(!priceText.getString().isEmpty());
        }

        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            for (Label label : List.of(name, count, price)) {
                int lineHeight = label.lineHeight(font);
                if (label.getMinimumHeight() != lineHeight)
                    label.setMinimum(0, lineHeight).setPreferred(0, lineHeight);
            }
            fitChild(row, new Rect(5, 5, Math.max(0, bounds.width() - 10),
                    Math.max(0, bounds.height() - 10)), font);
        }

        @Override protected void render(DrawContext context, int screenX, int screenY) {
            Rect area = getScreenBounds(screenX, screenY);
            background.drawAt(context, area);
            boolean ended = card.kind == Kind.FAVORITE && card.favorite != null && card.favorite.ended();
            if (ended) context.getGraphics().fill(area.x(), area.y(), area.right(), area.bottom(), 0xA032383D);
            else if (area.contains(context.getAbsMouseX(), context.getAbsMouseY()))
                context.getGraphics().fill(area.x(), area.y(), area.right(), area.bottom(), 0x24FFFFFF);
        }

        @Override public void draw(DrawContext context, Rect area, boolean hovered) {
            layout(new Rect(area.x(), area.y(), area.width(), area.height()), context.getFont());
            draw(context, 0, 0);
        }
    }

}
