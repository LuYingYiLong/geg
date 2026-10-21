package io.github.luyingyilong.ore.control.inventory;

import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.display.TextureRect;
import io.github.luyingyilong.ore.control.inventory.source.MenuInventorySource;
import io.github.luyingyilong.ore.control.inventory.source.SlotBinding;
import io.github.luyingyilong.ore.event.InputEvent;
import io.github.luyingyilong.ore.signal.DispatchGuard;
import io.github.luyingyilong.ore.signal.Signal;
import io.github.luyingyilong.ore.signal.SignalScope;
import io.github.luyingyilong.ore.signal.SignalSource;
import io.github.luyingyilong.ore.theme.StyleBox;
import io.github.luyingyilong.ore.theme.StyleBoxFlat;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeInsets;

import io.github.luyingyilong.ore.theme.ThemeKey;
import io.github.luyingyilong.ore.theme.ThemePadding;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.event.gesture.ReleaseGesture;
import java.util.Objects;

/**
 * Item preview or a stable source binding. Only a MenuSession can perform inventory operations.
 */
public final class Slot extends Control {
    public static final ThemeKey<Float> COUNT_FONT_SIZE = new ThemeKey<>("SlotCount", "font_size", Float.class,
            ThemeKey.Kind.FLOAT, 8.0F, true, value -> Float.isFinite(value) && value > 0);
    private static final ThemePadding PADDING = ThemePadding.of("Slot", "", 1, 1, 1, 1);
    public static final ThemeKey<Integer> PADDING_TOP = PADDING.top();
    public static final ThemeKey<Integer> PADDING_RIGHT = PADDING.right();
    public static final ThemeKey<Integer> PADDING_BOTTOM = PADDING.bottom();
    public static final ThemeKey<Integer> PADDING_LEFT = PADDING.left();
    private static final ThemePadding COUNT_PADDING = ThemePadding.of("Slot", "count_", 0, 0, 0, 0);
    public static final ThemeKey<Integer> COUNT_PADDING_TOP = COUNT_PADDING.top();
    public static final ThemeKey<Integer> COUNT_PADDING_RIGHT = COUNT_PADDING.right();
    public static final ThemeKey<Integer> COUNT_PADDING_BOTTOM = COUNT_PADDING.bottom();
    public static final ThemeKey<Integer> COUNT_PADDING_LEFT = COUNT_PADDING.left();

    public static final int SIZE = 18;
    public static final ThemeKey<StyleBox> SELECTED = ThemeKey.of("Slot","selected",StyleBox.class,ThemeKey.Kind.STYLE,
            new StyleBoxFlat(0x306CC349,new ThemeInsets(1,1,1,1),0xFF6CC349,ThemeInsets.ZERO,ThemeInsets.ZERO),false);
    public static final ThemeKey<StyleBox> HOVER = ThemeKey.of("Slot","hover",StyleBox.class,ThemeKey.Kind.STYLE,
            new StyleBoxFlat(0x30FFFFFF,ThemeInsets.ZERO,0,ThemeInsets.ZERO,ThemeInsets.ZERO),false);
    private final Label count = ownPart("count", new Label().setThemeTypeVariation("SlotCount").setShadow(true)
            .setAlignment(Label.Horizontal.RIGHT, Label.Vertical.BOTTOM));
    private ItemStack item = ItemStack.EMPTY;
    private int quantity;
    private boolean backgroundVisible = true;
    private SlotBinding binding;
    private SignalScope bindingScope;
    private Inventory.Mode mode=Inventory.Mode.READ_ONLY;
    private boolean selected;
    private final SignalSource<Integer> activated=new SignalSource<>(this,"slotActivated","Integer");
    private final ReleaseGesture<Integer> gesture=new ReleaseGesture<>();
    private final TextureRect emptyIcon=ownPart("emptyIcon",new TextureRect());

    public Slot() {
        setMinimum(SIZE, SIZE);
        setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        updateChildren(java.util.List.of(count));
        getSignalScope().own(this::releaseBinding);
    }

    public static void registerTheme(io.github.luyingyilong.ore.theme.ThemeDB db) {
        io.github.luyingyilong.ore.theme.OreTheme.registerType(db, "Slot");
        db.registerVariation("SlotCount", "Label");
        db.register(COUNT_FONT_SIZE);
        db.register(SELECTED);db.register(HOVER);
        PADDING.register(db); COUNT_PADDING.register(db);
    }

    @Override
    protected void validateChildren(java.util.List<io.github.luyingyilong.ore.control.base.Control> proposed) {
        if (!isUpdatingChildren())
            throw new IllegalStateException("Slot has a fixed skeleton; configure its properties");
    }

    public Slot setItem(ItemStack item, int quantity) {
        Objects.requireNonNull(item);
        if(quantity<0)throw new IllegalArgumentException("Negative item count");
        clearBinding();
        updateItem(item,quantity);
        return this;
    }

    private void updateItem(ItemStack item,int quantity){
        this.item = item.copyWithCount(1);
        this.quantity = quantity;
        count.setText(Component.literal(Integer.toString(quantity)));
        count.setVisible(quantity > 1 && !item.isEmpty() && mode!=Inventory.Mode.CONTAINER);
    }

    public SlotBinding getBinding(){return binding;}
    public Inventory.Mode getMode(){return mode;}
    public Slot setBinding(SlotBinding next){
        if(binding==next||binding!=null&&next!=null&&binding.source()==next.source()&&binding.slot()==next.slot())return this;
        if(next!=null){next.source().getSignalScope().checkThread();if(next.source().isClosed())throw new IllegalStateException("Source is closed");
            if(mode==Inventory.Mode.CONTAINER){if(!(next.source() instanceof MenuInventorySource source)||source.getSession().getSource()!=source)throw new IllegalArgumentException("Use the session's Menu source");source.getSession().validateView(this,next.slot());}}
        releaseBinding();binding=next;
        if(next!=null){
            bindingScope=getSignalScope().child("slot binding");
            next.source().slotChanged().connect(bindingScope,index->{if(isAlive()&&binding==next&&index==next.slot())refreshBinding();});
            refreshBinding();
            if(mode==Inventory.Mode.CONTAINER)getMenuSession().register(this);
        }
        return this;
    }
    public Slot clearBinding(){releaseBinding();return this;}
    private void refreshBinding(){if(binding!=null){ItemStack stack=binding.source().getItem(binding.slot());updateItem(stack,stack.getCount());}}
    private void releaseBinding(){
        cancelInteraction();
        var session=getMenuSession();if(session!=null)session.unregister(this);
        if(bindingScope!=null){bindingScope.close();bindingScope=null;}binding=null;
    }
    public Slot setMode(Inventory.Mode value){
        Objects.requireNonNull(value);if(mode==value)return this;
        if(value==Inventory.Mode.CONTAINER){if(!(binding!=null&&binding.source() instanceof MenuInventorySource source)||source.getSession().getSource()!=source)throw new IllegalArgumentException("Use the session's Menu binding");getMenuSession().validateView(this,binding.slot());}
        cancelInteraction();var old=getMenuSession();if(old!=null)old.unregister(this);mode=value;
        setFocusable(value!=Inventory.Mode.READ_ONLY);
        if(value==Inventory.Mode.CONTAINER)getMenuSession().register(this);
        if(binding!=null)refreshBinding();
        return this;
    }
    public io.github.luyingyilong.ore.window.container.MenuSession getMenuSession(){return binding!=null&&binding.source() instanceof MenuInventorySource source?source.getSession():null;}
    public boolean getSelected(){return selected;}
    public Slot setSelected(boolean value){selected=value;return this;}
    public Signal<Integer> slotActivated(){return activated.signal();}
    public Slot setEmptyIcon(TextureSource value){emptyIcon.setTexture(value);return this;}
    public TextureSource getEmptyIcon(){return emptyIcon.getTexture();}

    @Override public void input(InputEvent event){
        if(mode==Inventory.Mode.CONTAINER){var session=getMenuSession();if(session!=null&&session.input(this,event))event.accept();return;}
        super.input(event);
    }
    @Override protected boolean pointerDown(int x,int y){if(mode!=Inventory.Mode.SELECT||!hit(x,y)||binding==null||binding.source().isClosed())return false;gesture.pointerDown(binding.slot());return true;}
    @Override protected boolean pointerUp(boolean over){var r=gesture.pointerUp(binding==null?null:binding.slot(),over);if(r.target()!=null)activate();return r.handled();}
    @Override public boolean onActivationPressed(int key){if(!acceptsFocus())return false;gesture.keyDown(key,binding==null?-1:binding.slot());return true;}
    @Override public boolean onActivationReleased(int key){var r=gesture.keyUp(key,acceptsFocus()?(binding==null?-1:binding.slot()):null);if(r.target()!=null)activate();return r.handled();}
    @Override public boolean activate(){
        if(mode==Inventory.Mode.CONTAINER){var session=getMenuSession();return session!=null&&session.activate(this);}
        if(mode!=Inventory.Mode.SELECT||binding==null||binding.source().isClosed())return false;
        activated.emit(binding.slot());return true;
    }
    @Override public boolean acceptsFocus(){return mode!=Inventory.Mode.READ_ONLY&&binding!=null&&!binding.source().isClosed()
            &&(mode!=Inventory.Mode.CONTAINER||getMenuSession().isValid())&&super.acceptsFocus();}
    @Override public void cancelInteraction(){gesture.cancel();var session=getMenuSession();if(mode==Inventory.Mode.CONTAINER&&session!=null)session.cancelFrom(this);}
    @Override public void onFocusChanged(boolean focused){if(!focused)gesture.cancel();}

    public ItemStack getItem() {
        return binding==null?item.copy():binding.source().getItem(binding.slot());
    }

    public int getQuantity() {
        return binding==null?quantity:binding.source().getItem(binding.slot()).getCount();
    }

    public boolean getBackgroundVisible() {
        return backgroundVisible;
    }

    public Slot setBackgroundVisible(boolean visible) {
        backgroundVisible = visible;
        return this;
    }

    @Override
    public boolean hasTooltip() {
        return !getItem().isEmpty()&&(getMenuSession()==null||getMenuSession().getMenu().getCarried().isEmpty());
    }

    @Override
    public void renderTooltip(DrawContext context, int mouseX, int mouseY) {
        context.getGraphics().renderTooltip(context.getFont(), getItem(), mouseX, mouseY);
    }

    @Override
    public void layout(Rect bounds, net.minecraft.client.gui.Font font) {
        super.layout(bounds, font);
        count.layout(COUNT_PADDING.getInsets(this).inset(getThemeItem(OreTheme.SLOT_PANEL).getContentRect(new Rect(0, 0, bounds.width(), bounds.height()))), font);
    }

    @Override public int getMinimumWidth(){return Math.max(super.getMinimumWidth(),16+PADDING.getInsets(this).getHorizontal()
            +getThemeItem(OreTheme.SLOT_PANEL).getContentMargin().getHorizontal());}
    @Override public int getMinimumHeight(){return Math.max(super.getMinimumHeight(),16+PADDING.getInsets(this).getVertical()
            +getThemeItem(OreTheme.SLOT_PANEL).getContentMargin().getVertical());}

    @Override public int measureWidth(net.minecraft.client.gui.Font font, int available) {
        return Math.max(super.measureWidth(font, available), 16 + PADDING.getInsets(this).getHorizontal() + getThemeItem(OreTheme.SLOT_PANEL).getContentMargin().getHorizontal());
    }

    @Override public int measureHeight(net.minecraft.client.gui.Font font, int available) {
        return Math.max(super.measureHeight(font, available), 16 + PADDING.getInsets(this).getVertical() + getThemeItem(OreTheme.SLOT_PANEL).getContentMargin().getVertical());
    }

    @Override
    protected void render(DrawContext context, int screenX, int screenY) {
        Rect area = getScreenBounds(screenX, screenY);
        if (backgroundVisible) {
            getThemeItem(OreTheme.SLOT_PANEL).draw(context, area);
        }
        Rect inner = PADDING.getInsets(this).inset(getThemeItem(OreTheme.SLOT_PANEL).getContentRect(area));
        int ix=inner.x()+(inner.width()-16)/2,iy=inner.y()+(inner.height()-16)/2;
        if(mode==Inventory.Mode.CONTAINER&&getMenuSession()!=null){getMenuSession().renderSlot(this,context,ix,iy);}
        else if(!item.isEmpty()){
            context.getGraphics().renderItem(item,ix,iy);
            context.getGraphics().renderItemDecorations(context.getFont(),item.copyWithCount(Math.max(1,quantity)),ix,iy,"");
        }else if(emptyIcon.getTexture()!=null){emptyIcon.layout(new Rect(ix,iy,16,16),context.getFont());emptyIcon.draw(context,0,0);}
        if(selected)getThemeItem(SELECTED).draw(context,area);
        if(mode!=Inventory.Mode.READ_ONLY&&area.contains(context.getAbsMouseX(),context.getAbsMouseY()))getThemeItem(HOVER).draw(context,area);
    }

    @Override
    protected void drawContent(DrawContext context, int screenX, int screenY) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            render(context, screenX, screenY);
            if (!count.getVisible()||mode==Inventory.Mode.CONTAINER) return;
            var graphics = context.getGraphics();
            graphics.flush();
            graphics.pose().pushPose();
            try {
                // Match vanilla item-decoration depth so the quantity stays above the item model.
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                count.draw(context, screenX, screenY);
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
    }
}
