package io.github.luyingyilong.ore.compat.recipeviewer;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiExclusionArea;
import dev.emi.emi.api.widget.Bounds;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.compat.recipeviewer.emi.OreEmiPlugin;
import io.github.luyingyilong.ore.compat.recipeviewer.jei.OreJeiPlugin;
import io.github.luyingyilong.ore.compat.recipeviewer.rei.OreReiPlugin;
import io.github.luyingyilong.ore.layout.LifecycleScheduler;
import io.github.luyingyilong.ore.screen.OreButtonWidget;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZonesProvider;
import me.shedaniel.rei.forge.REIPluginClient;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Exercise each real optional API registration; all callbacks must consult current geometry. */
public final class RecipeViewerPluginsTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        // Forge discovers these CLASS-retention annotations through bytecode scanning.
        check(hasAnnotation(OreJeiPlugin.class, JeiPlugin.class), "JEI discovery annotation");
        check(hasAnnotation(OreEmiPlugin.class, EmiEntrypoint.class), "EMI discovery annotation");
        check(hasAnnotation(OreReiPlugin.class, REIPluginClient.class), "REI discovery annotation");

        AtomicReference<IGlobalGuiHandler> jei = new AtomicReference<>();
        new OreJeiPlugin().registerGuiHandlers(proxy(IGuiHandlerRegistration.class, (method, values) -> {
            check(method.equals("addGlobalGuiHandler"), "JEI uses the global handler for vanilla inventory and Ore screens");
            jei.set((IGlobalGuiHandler) values[0]);
        }));
        check(jei.get() != null && jei.get().getGuiExtraAreas().isEmpty(), "JEI safely handles no active screen");
        check(new OreJeiPlugin().getPluginUid().toString().equals("ore:gui_exclusions"), "Stable host-independent plugin ID");

        // Reflective proxies eagerly initialize every type in EMI's registry interface,
        // including item stacks. Check the hook bytecode and exercise its actual handler
        // without bootstrapping Minecraft's item registry in a layout test.
        EmiExclusionArea<Screen> emi = new OreEmiPlugin();
        check(callsEmiRegistration(), "EMI registers its generic handler");
        AtomicReference<ExclusionZonesProvider<Screen>> rei = new AtomicReference<>();
        new OreReiPlugin().registerExclusionZones(proxy(ExclusionZones.class, (method, values) -> {
            check(method.equals("register") && values[0] == Screen.class, "REI covers every vanilla/Ore Screen subtype");
            rei.set((ExclusionZonesProvider<Screen>) values[1]);
        }));

        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        TestScreen screen = new TestScreen();
        OreButtonWidget widget = new OreButtonWidget(new Button(), Component.literal("Mail"));
        widget.place(font, 4, 74, 32); screen.add(widget);
        assertAreas(screen, emi, rei.get(), 4, 74, widget.getWidth(), widget.getHeight());
        widget.place(font, 14, 54, 32);
        assertAreas(screen, emi, rei.get(), 14, 54, widget.getWidth(), widget.getHeight());
        widget.visible = false;
        List<Bounds> empty = new ArrayList<>(); emi.addExclusionArea(screen, empty::add);
        check(empty.isEmpty() && rei.get().provide(screen).isEmpty(), "Optional adapters do not cache stale regions");
        widget.queueFree(); LifecycleScheduler.flush();
        System.out.println("ORE_RECIPE_VIEWER_PLUGINS_PASS");
    }

    private static void assertAreas(Screen screen, EmiExclusionArea<Screen> emi, ExclusionZonesProvider<Screen> rei,
                                    int x, int y, int width, int height) {
        List<Bounds> emiAreas = new ArrayList<>(); emi.addExclusionArea(screen, emiAreas::add);
        check(emiAreas.size() == 1, "EMI receives exactly one region");
        Bounds b = emiAreas.get(0);
        check(b.x() == x && b.y() == y && b.width() == width && b.height() == height, "EMI GUI-pixel conversion");
        List<Rectangle> reiAreas = List.copyOf(rei.provide(screen));
        check(reiAreas.size() == 1, "REI receives exactly one region");
        Rectangle r = reiAreas.get(0);
        check(r.x == x && r.y == y && r.width == width && r.height == height, "REI GUI-pixel conversion");
    }

    private interface Call { void accept(String method, Object[] args); }
    private static boolean callsEmiRegistration() throws java.io.IOException {
        boolean[] found = {false};
        try (var bytes = OreEmiPlugin.class.getResourceAsStream("OreEmiPlugin.class")) {
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals("register")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                            found[0] |= owner.equals("dev/emi/emi/api/EmiRegistry") && method.equals("addGenericExclusionArea");
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return found[0];
    }
    private static boolean hasAnnotation(Class<?> type, Class<?> annotation) throws java.io.IOException {
        boolean[] found = {false};
        try (var bytes = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    found[0] |= descriptor.equals(Type.getDescriptor(annotation));
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return found[0];
    }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            call.accept(method.getName(), args);
            return null;
        }));
    }
    private static final class TestScreen extends Screen {
        TestScreen() { super(Component.empty()); width = 200; height = 100; }
        void add(OreButtonWidget widget) { addRenderableWidget(widget); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
