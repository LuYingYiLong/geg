package io.github.luyingyilong.ore.control.base;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Guards responsibility-based packages and the package-private control implementation boundary. */
public final class PackageContractTest {
    private static final String ROOT = "io.github.luyingyilong.ore";
    public static void main(String[] args) throws Exception {
        Map<String,String> groups = Map.ofEntries(
                Map.entry("control.base", "Control Container SizeFlags Rect Size Offset ControlNotification ControlTree ControlIds ControlIdScope LifecycleInfo LifecycleScheduler LifecycleState"),
                Map.entry("control.layout", "Box Grid Panel Padding Scroll ScrollContent Splitter Divider Collapse"),
                Map.entry("control.display", "Label TextureRect Badge Tag Descriptions DescriptionItem"),
                Map.entry("control.display.avatar", "Avatar"),
                Map.entry("control.progress", "ProgressBar TextureProgressBar Spin ProgressValueModel TextureProgressGeometry"),
                Map.entry("control.input", "Button Checkbox Switch Radio RadioGroup Dropdown Segmented Slider Input NumberInput TextArea InputControl OreInputMethod InputShortcuts OreTextFieldHandler OreTextFieldRenderer NumberInputRules"),
                Map.entry("control.data", "Table Tree PlayerIdentityCell"),
                Map.entry("control.inventory", "Inventory Slot"),
                Map.entry("control.inventory.source", "InventorySource MemoryInventorySource MenuInventorySource SlotBinding"),
                Map.entry("control.form", "Form FormItem FieldKey FieldValue FormField FormFields FormValues FormValidation"),
                Map.entry("control.form.validation", "Validator Validators"),
                Map.entry("control.navigation", "Menu MenuItem Navbar Tabs TabItem TabsBar"),
                Map.entry("control.overlay", "Overlay Drawer Dialog Modal Toast"),
                Map.entry("window", "Window FocusManager FocusGeometry"),
                Map.entry("window.container", "MenuSession MenuInteraction ContainerScreenAccess OreMenuScreen"),
                Map.entry("window.screen", "OreScreen OreButtonWidget"),
                Map.entry("event.gesture", "ReleaseGesture"), Map.entry("text", "TextLines"),
                Map.entry("render.internal", "OreLayers"), Map.entry("control.input.internal", "NativePaintPass"),
                Map.entry("control.overlay.toast", "OreToasts ToastQueue"));
        ClassLoader loader = PackageContractTest.class.getClassLoader();
        for (var group:groups.entrySet()) for(String name:group.getValue().split(" ")) {
            Class<?> type=Class.forName(ROOT+"."+group.getKey()+"."+name,false,loader);
            check(type.getPackageName().equals(ROOT+"."+group.getKey()), "wrong category: "+name);
            if(!group.getKey().equals("text"))
                check(loader.getResource(ROOT.replace('.','/')+"/"+name+".class")==null, "old root alias: "+name);
        }
        for(String name:new String[]{"ControlTree","ControlIds","LifecycleScheduler"}) {
            Class<?> type=Class.forName(ROOT+".control.base."+name,false,loader);
            if(!name.equals("LifecycleScheduler"))check(!Modifier.isPublic(type.getModifiers()),"tree implementation became public");
        }
        for(String name:new String[]{"ProgressValueModel","TextureProgressGeometry"})
            check(!Modifier.isPublic(Class.forName(ROOT+".control.progress."+name,false,loader).getModifiers()),"progress helper became public");
        check(!Modifier.isPublic(Class.forName(ROOT+".control.navigation.TabsBar", false, loader).getModifiers()), "tab bar helper became public");
        for(String name:new String[]{"InputShortcuts","OreTextFieldHandler","OreTextFieldRenderer","NumberInputRules","InputMethodSession","WindowsInputMethod","ImBlockerInputMethod"})
            check(!Modifier.isPublic(Class.forName(ROOT+".control.input."+name,false,loader).getModifiers()),"editor/IME helper became public");
        for(String field:new String[]{"children","parent","lifecycleOwner"}) {
            int access=Control.class.getDeclaredField(field).getModifiers();
            check(!Modifier.isPublic(access)&&!Modifier.isProtected(access)&&!Modifier.isPrivate(access),"tree field escaped its package");
        }
        for(String method:new String[]{"freeNow","readySubtree","exitTree"}) {
            int access=Control.class.getDeclaredMethod(method).getModifiers();
            check(!Modifier.isPublic(access)&&!Modifier.isProtected(access),"lifecycle method exposed: "+method);
        }
        for(String method:new String[]{"prepareReadyNotifications","suspendTree","enterTree"}) {
            var found=java.util.Arrays.stream(Control.class.getDeclaredMethods()).filter(m->m.getName().equals(method)).findFirst().orElseThrow();
            check(Modifier.isProtected(found.getModifiers())&&Modifier.isFinal(found.getModifiers()),"host hook must stay protected final");
        }
        Path source=Path.of(System.getProperty("ore.resourceRoot")).getParent().resolve("java").resolve(ROOT.replace('.','/'));
        try(var files=Files.list(source)) {
            Set<String> allowed=Set.of("OreClient.java","OreClientOptions.java","OreClientExtension.java","package-info.java");
            for(Path file:files.filter(Files::isRegularFile).toList())check(allowed.contains(file.getFileName().toString()),"root contains a control/helper: "+file);
        }
        for(String legacy:new String[]{"layout","input","inventory","menu","screen","skin","util","form"}) {
            check(!Files.exists(source.resolve(legacy)),"legacy package directory remains: "+legacy);
        }
        var declaration=Pattern.compile("(?m)^package ([\\w.]+);");
        try(var files=Files.walk(source)) {
            for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()) {
                var match=declaration.matcher(Files.readString(file));check(match.find(),"missing package: "+file);
                String expected=ROOT+(file.getParent().equals(source)?"":"."+source.relativize(file.getParent()).toString().replace('\\','.').replace('/','.'));
                check(match.group(1).equals(expected),"source path/package mismatch: "+file);
            }
        }
        System.out.println("ORE_PACKAGE_CONTRACT_PASS");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
