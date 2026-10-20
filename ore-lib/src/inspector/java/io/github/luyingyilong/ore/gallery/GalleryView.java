package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Navbar is fixed; navigation and samples share the same Window input/overlay pipeline.
 */
public final class GalleryView {
    private final Window window = Window.embedded();
    private final Menu navigation = new Menu();
    private final Preview preview = new Preview();
    private final Dropdown<Integer> widths = new Dropdown<>(0, 4);
    private final Label heading = new Label().setHero(true).setWrap(Label.Wrap.WORD);
    private final Label description = new Label().setThemeTypeVariation("LabelSecondary").setWrap(Label.Wrap.WORD);
    private final Scroll content;
    private String selected = "";

    public GalleryView() {
        window.setDebugName("Ore Component Gallery");
        Navbar navbar = new Navbar(tr("title"));
        widths.setOptions(List.of(
                new Dropdown.Option<>(0, tr("follow_width")), new Dropdown.Option<>(127, Component.literal("127px")),
                new Dropdown.Option<>(128, Component.literal("128px")), new Dropdown.Option<>(129, Component.literal("129px"))));
        widths.selectionChanged().connect(window, value -> preview.setPreviewWidth(value));
        navbar.setExtra(widths);
        List<MenuItem> groups = new ArrayList<>();
        for (String group : List.of("layout", "display", "actions", "input", "data", "overlays")) {
            var children = GalleryCatalog.getEntries().stream().filter(entry -> entry.group().equals(group))
                    .map(entry -> MenuItem.item(entry.id(), Component.literal(entry.title()))).toList();
            groups.add(MenuItem.group("group:" + group, tr("group." + group), children));
        }
        navigation.setItems(groups);
        navigation.itemActivated().connect(window, item -> select(item.key()));
        Scroll nav = new Scroll().setContent(navigation);
        nav.setMinimum(100, 32);
        nav.setPreferred(128, 0);
        nav.setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Box examples = new Box().setGap(6);
        examples.addChild(heading);
        examples.addChild(description);
        examples.addChild(preview);
        preview.setMinimum(96, 24);
        preview.setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        content = new Scroll().setContent(examples);
        Padding panel = new Padding(new Padding.Insets(4, 4, 4, 4)).setChild(content);
        panel.setMinimum(108, 32);
        panel.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
        panel.setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Columns columns = new Columns(nav, panel);
        Scroll body = new Scroll().setVertical(Scroll.BarMode.DISABLED).setContent(columns);
        body.setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Box root = new Box().setGap(4);
        root.addChild(navbar);
        root.addChild(body);
        window.setContent(new Panel().setChild(root));
        select("box");
    }

    public static Component tr(String key, Object... args) {
        return Component.translatable("gallery.ore." + key, args);
    }

    public Window getWindow() {
        return window;
    }

    public Scroll getContentScroll() {
        return content;
    }

    public String getSelected() {
        return selected;
    }

    public Control getSample() {
        return preview.getChildCount() == 0 ? null : preview.getChild(0);
    }

    public void select(String id) {
        if (selected.equals(id)) return;
        GalleryCatalog.Entry entry = GalleryCatalog.getEntries().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
        Control next = GallerySamples.create(id, window);
        next.setDebugName("Gallery " + entry.title());
        preview.replace(next);
        selected = id;
        navigation.setSelectedNoSignal(id);
        heading.setText(Component.literal(entry.title()));
        description.setText(tr("description." + entry.id()));
        content.scrollToTop();
    }

    public void setPreviewWidth(int width) {
        widths.setSelectedNoSignal(width);
        preview.setPreviewWidth(width);
    }

    /**
     * Horizontal scrolling must not grant its children unbounded vertical space.
     */
    private static final class Columns extends Container implements ScrollContent {
        private final Control navigation, content;
        private Size extent = new Size(0, 0);

        Columns(Control navigation, Control content) {
            this.navigation = navigation;
            this.content = content;
            addChild(navigation);
            addChild(content);
            setMinimum(212, 32);
        }

        @Override
        public Size measureSize(Font font, Size available) {
            return new Size(Math.max(212, available.width()), 32);
        }

        @Override
        public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            int width = Math.max(212, bounds.width());
            int left = Math.min(128, Math.max(100, width - 112));
            fitChild(navigation, new Rect(0, 0, left, bounds.height()), font);
            fitChild(content, new Rect(left + 4, 0, width - left - 4, bounds.height()), font);
            extent = new Size(width, bounds.height());
        }

        @Override
        public Size getContentExtent() {
            return extent;
        }

        @Override
        public void setVisibleRegion(Rect region) {
        }
    }

    private static final class Preview extends Container {
        private int width;

        void setPreviewWidth(int value) {
            width = Math.max(0, value);
            requestLayout();
        }

        void replace(Control next) {
            Control old = getChildCount() == 0 ? null : getChild(0);
            if (old != null) {
                removeChild(old);
                old.queueFree();
            }
            addChild(next);
            requestLayout();
        }

        @Override
        public Size measureSize(Font font, Size available) {
            int actual = width == 0 ? Math.max(getMinimumWidth(), available.width()) : width;
            Size size = getChildCount() == 0 ? new Size(0, 0) : getChild(0).desiredSize(font, new Size(actual, available.height()));
            return new Size(actual, Math.max(getMinimumHeight(), size.height()));
        }

        @Override
        public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            if (getChildCount() > 0)
                fitChild(getChild(0), new Rect(0, 0, width == 0 ? bounds.width() : width, bounds.height()), font);
        }
    }
}

