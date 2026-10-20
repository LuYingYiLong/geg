package io.github.luyingyilong.ore.theme;

import io.github.luyingyilong.ore.render.*;

import com.google.gson.*;
import io.github.luyingyilong.ore.signal.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.*;
import java.util.*;

/** Client-thread registry. Reload stages a complete graph before publishing any resource object. */
public final class ThemeDB implements SignalOwner {
    private final SignalScope scope=new SignalScope();
    private final SignalSource<Boolean> changed=new SignalSource<>(this,"themesChanged","Boolean");
    private final Map<String,ThemeKey<?>> keys=new LinkedHashMap<>();
    private final Map<String,String> types=new LinkedHashMap<>();
    private final Map<String,ThemeKey<?>> fallbacks=new LinkedHashMap<>();
    private final Map<ResourceLocation,Theme> themes=new LinkedHashMap<>();
    private final Set<ResourceLocation> registered=new LinkedHashSet<>();
    private List<Diagnostic> diagnostics=List.of();
    private ResourceLocation defaultTheme=ResourceLocation.fromNamespaceAndPath("ore","ore");
    private long revision;
    private Set<Theme> reloadNotifications=Set.of();
    private final Map<ResourceLocation,int[]> imageSizes=new HashMap<>();
    boolean isReloadNotification(Theme theme) { return reloadNotifications.contains(theme); }
    private static final class Builtin { static final ThemeDB INSTANCE=create(); private static ThemeDB create() {
        ThemeDB db=new ThemeDB(); OreTheme.register(db); return db;
    } }
    public static ThemeDB getDefault() { return Builtin.INSTANCE; }
    public SignalScope getSignalScope() { return scope; }
    public Signal<Boolean> changed() { return changed.signal(); }
    public long getRevision() { return revision; }
    void publish(boolean layout) { revision++; changed.emit(layout); }
    public void registerType(String name,String base) {
        scope.checkThread(); Objects.requireNonNull(name);
        if(name.isBlank() || name.startsWith(":")) throw new IllegalArgumentException("Invalid control theme type");
        if(types.containsKey(name)) { if(!Objects.equals(types.get(name),base)) throw new IllegalArgumentException("Conflicting theme type: "+name); return; }
        if(base!=null && !types.containsKey(base)) throw new IllegalArgumentException("Register base type first: "+base);
        types.put(name,base);
    }
    public void registerVariation(String name,String base) { registerType(name,Objects.requireNonNull(base)); }
    public void register(ThemeKey<?> key) {
        scope.checkThread(); Objects.requireNonNull(key);
        if(!types.containsKey(key.type())) throw new IllegalArgumentException("Unknown control theme type: "+key.type());
        ThemeKey<?> old=keys.putIfAbsent(key.getId(),key);
        if(old!=null && !(old.valueType()==key.valueType() && old.kind()==key.kind()
                && old.affectsLayout()==key.affectsLayout() && old.defaultValue().equals(key.defaultValue())
                && old.validator()==key.validator())) throw new IllegalArgumentException("Conflicting theme declaration: "+key.getId());
    }
    /** Explicit item fallback, evaluated within each theme layer before its resource parent. */
    public <T> void registerFallback(ThemeKey<T> key,ThemeKey<T> fallback) {
        scope.checkThread(); requireKey(key); requireKey(fallback);
        if(key.valueType()!=fallback.valueType() || key.kind()!=fallback.kind())
            throw new IllegalArgumentException("Theme fallback types must match");
        ThemeKey<?> previous=fallbacks.get(key.getId());
        if(previous!=null) {
            if(!previous.getId().equals(fallback.getId())) throw new IllegalArgumentException("Conflicting theme fallback: "+key.getId());
            return;
        }
        Set<String> visited=new HashSet<>(); visited.add(declaration(key.getId()).getId());
        for(ThemeKey<?> next=fallback;next!=null;next=fallbackFor(next))
            if(!visited.add(declaration(next.getId()).getId())) throw new IllegalArgumentException("Theme item fallback cycle");
        fallbacks.put(key.getId(),fallback);
    }
    private ThemeKey<?> fallbackFor(ThemeKey<?> key) {
        ThemeKey<?> fallback=fallbacks.get(key.getId());
        if(fallback==null) { ThemeKey<?> declared=declaration(key.getId()); if(declared!=null) fallback=fallbacks.get(declared.getId()); }
        return fallback;
    }
    private record ItemRequest(ThemeKey<?> key,List<String> candidates) { }
    private List<ItemRequest> requests(ThemeKey<?> key,List<String> candidates) {
        List<ItemRequest> result=new ArrayList<>(); result.add(new ItemRequest(key,candidates));
        for(ThemeKey<?> fallback=fallbackFor(key);fallback!=null;fallback=fallbackFor(fallback))
            result.add(new ItemRequest(fallback,getTypeChain(fallback.type())));
        return result;
    }
    public LocalValue getLocalThemeValue(Map<ThemeKey<?>,Object> values,ThemeKey<?> key,List<String> candidates) {
        for(ItemRequest request:requests(key,candidates)) {
            LocalValue value=getLocalValue(values,request.key(),request.candidates());
            if(value!=null) return value;
        }
        return null;
    }
    private ThemeKey<?> declaration(String id) {
        ThemeKey<?> exact=keys.get(id); if(exact!=null) return exact;
        int split=id.lastIndexOf(':'); if(split<1) return null;
        String type=id.substring(0,split), name=id.substring(split+1);
        if(!types.containsKey(type)) return null;
        for(String parent=types.get(type);parent!=null;parent=types.get(parent)) {
            ThemeKey<?> inherited=keys.get(parent+":"+name);
            if(inherited!=null) return inherited;
        }
        return null;
    }
    @SuppressWarnings("unchecked")
    public <T> ThemeKey<T> getKey(String type,String name,Class<T> expectedType) {
        ThemeKey<?> base=declaration(type+":"+name);
        if(base==null || base.valueType()!=expectedType) throw new IllegalArgumentException("Unknown/wrong typed theme key: "+type+":"+name);
        ThemeKey<T> typed=(ThemeKey<T>)base;
        if(base.type().equals(type)) return typed;
        return new ThemeKey<>(type,name,expectedType,typed.kind(),typed.defaultValue(),typed.affectsLayout(),typed.validator());
    }
    public <T> T validateValue(ThemeKey<T> key,Object value) {
        requireKey(key); declaration(key.getId()).validate(value); return key.validate(value);
    }
    void requireKey(ThemeKey<?> key) {
        ThemeKey<?> found=declaration(key.getId());
        if(found==null || found.valueType()!=key.valueType() || found.kind()!=key.kind()) throw new IllegalArgumentException("Undeclared theme item: "+key.getId());
    }
    public List<ThemeKey<?>> getKeys() { return List.copyOf(keys.values()); }
    public List<String> getTypeChain(String type) {
        List<String> result=new ArrayList<>();
        for(String t=type;t!=null;t=types.get(t)) { if(result.contains(t)) throw new IllegalStateException("Theme type cycle"); result.add(t); }
        return List.copyOf(result);
    }
    public boolean hasType(String name) { return types.containsKey(name); }
    public Theme getTheme(ResourceLocation id) { scope.checkThread(); return themes.computeIfAbsent(id,k->new Theme(this,k)); }
    public void registerTheme(ResourceLocation id) { scope.checkThread(); registered.add(Objects.requireNonNull(id)); getTheme(id); }
    public void setDefaultTheme(ResourceLocation id) { registerTheme(id); defaultTheme=id; publish(true); }
    public Theme getDefaultTheme() { return getTheme(defaultTheme); }
    public List<Diagnostic> getDiagnostics() { return diagnostics; }
    public record Diagnostic(String resource,String property,String message) { }
    public record Trace(String kind,String theme,String resource,String node,String item,String reason) { }
    public record Resolution<T>(T value,List<Trace> sources) { public Resolution { sources=List.copyOf(sources); } }
    public <T> Resolution<T> resolve(ThemeKey<T> key,List<String> candidates,List<Layer> layers) {
        requireKey(key); List<Trace> trace=new ArrayList<>();
        List<ItemRequest> requests=requests(key,candidates);
        for(Layer layer:layers) {
            LocalValue inherited=getLocalThemeValue(layer.overrides(),key,candidates);
            if(inherited!=null) {
                trace.add(new Trace("style owner override","","",layer.node(),inherited.item(),"internal part / popup style source"));
                return new Resolution<>(key.validate(inherited.value()),trace);
            }
            for(Theme theme=layer.theme();theme!=null;theme=theme.getParent()) {
                for(ItemRequest request:requests) for(String type:request.candidates()) {
                    String id=type+":"+request.key().name(); Object value=theme.getValue(id);
                    if(value!=null && key.valueType().isInstance(value)) {
                        trace.add(new Trace(theme==layer.theme()?layer.kind():"resource parent",theme.getId().toString(),theme.getValueResource(id),layer.node(),id,
                                request.key()==key?"resolved":"fallback for "+key.getId()));
                        return new Resolution<>(key.validate(value),trace);
                    }
                    trace.add(new Trace(theme==layer.theme()?layer.kind():"resource parent",theme.getId().toString(),theme.getResource(),layer.node(),id,"missing; continue fallback"));
                }
            }
        }
        for(String type:candidates) {
            ThemeKey<?> declaration=keys.get(type+":"+key.name());
            if(declaration!=null && declaration.valueType()==key.valueType()) {
                trace.add(new Trace("safe default","ore:default","code","",declaration.getId(),"no resource value"));
                return new Resolution<>(key.validate(declaration.defaultValue()),trace);
            }
        }
        trace.add(new Trace("safe default","ore:default","code","",key.getId(),"no resource value"));
        return new Resolution<>(key.defaultValue(),trace);
    }
    public record LocalValue(Object value,String item) { }
    public static LocalValue getLocalValue(Map<ThemeKey<?>,Object> values,ThemeKey<?> key,List<String> candidates) {
        for(String type:candidates) for(var entry:values.entrySet()) {
            ThemeKey<?> candidate=entry.getKey();
            if(candidate.type().equals(type) && candidate.name().equals(key.name())
                    && candidate.valueType()==key.valueType() && candidate.kind()==key.kind()) return new LocalValue(entry.getValue(),candidate.getId());
        }
        return null;
    }
    public record Layer(Theme theme,String kind,String node,Map<ThemeKey<?>,Object> overrides) { }
    public interface Resources {
        Reader open(ResourceLocation json) throws IOException;
        int[] imageSize(ResourceLocation texture) throws IOException;
    }
    public void reload(ResourceManager manager) {
        reload(new Resources() {
            public Reader open(ResourceLocation id) throws IOException { return manager.openAsReader(id); }
            public int[] imageSize(ResourceLocation id) throws IOException {
                try(var in=manager.open(id); var image=com.mojang.blaze3d.platform.NativeImage.read(in)) { return new int[]{image.getWidth(),image.getHeight()}; }
            }
        });
    }
    public void reload(Resources resources) {
        scope.checkThread(); imageSizes.clear(); Map<ResourceLocation,Staged> staged=new LinkedHashMap<>(); List<Diagnostic> errors=new ArrayList<>();
        try {
            for(ResourceLocation id:registered) if(!id.equals(OreTheme.DEFAULT_ID)) load(id,resources,staged,new LinkedHashSet<>(),errors);
            // Graph and values are valid now. No observer runs until every resource has been swapped.
            for(ResourceLocation id:staged.keySet()) getTheme(id);
            for(var entry:staged.entrySet()) {
                Staged s=entry.getValue(); getTheme(entry.getKey()).replace(s.values,s.parent==null?null:getTheme(s.parent),s.path);
            }
            diagnostics=List.copyOf(errors);
            reloadNotifications=Set.copyOf(staged.keySet().stream().map(this::getTheme).toList());
            try {
                publish(true);
                for(ResourceLocation id:staged.keySet()) getTheme(id).emitChanged(true);
            } finally { reloadNotifications=Set.of(); }
        } catch(RuntimeException | IOException error) {
            errors.add(new Diagnostic(error instanceof LoadFailure failure?failure.resource:"themes","$",error.getMessage()==null?error.toString():error.getMessage())); diagnostics=List.copyOf(errors);
        }
        for(Diagnostic d:diagnostics) System.getLogger(ThemeDB.class.getName()).log(System.Logger.Level.WARNING,d.resource()+" "+d.property()+": "+d.message());
    }
    private static final class LoadFailure extends RuntimeException {
        final String resource;
        LoadFailure(String resource,Exception error) { super(error.getMessage(),error); this.resource=resource; }
    }
    private record Staged(ResourceLocation parent,Map<String,Object> values,String path) { }
    private void load(ResourceLocation id,Resources resources,Map<ResourceLocation,Staged> staged,Set<ResourceLocation> pending,List<Diagnostic> errors) throws IOException {
        if(id.equals(OreTheme.DEFAULT_ID) || staged.containsKey(id)) return;
        if(!pending.add(id)) throw new IllegalArgumentException("Theme inheritance cycle: "+pending+" -> "+id);
        ResourceLocation file=ResourceLocation.fromNamespaceAndPath(id.getNamespace(),"themes/"+id.getPath()+".json");
        JsonObject json;
        try(Reader reader=resources.open(file)) { json=JsonParser.parseReader(reader).getAsJsonObject(); }
        catch(RuntimeException | IOException error) { throw new LoadFailure(file.toString(),error); }
        ResourceLocation parent=json.has("parent")?ResourceLocation.parse(json.get("parent").getAsString()):OreTheme.DEFAULT_ID;
        load(parent,resources,staged,pending,errors);
        Map<String,Object> values=new LinkedHashMap<>();
        for(var entry:json.entrySet()) {
            if(entry.getKey().equals("parent")) continue;
            ThemeKey<?> key=declaration(entry.getKey());
            if(key==null) { errors.add(new Diagnostic(file.toString(),entry.getKey(),"Unknown theme item")); continue; }
            try { values.put(entry.getKey(),key.validate(read(key,entry.getValue(),resources))); }
            catch(RuntimeException | IOException error) { errors.add(new Diagnostic(file.toString(),entry.getKey(),error.getMessage()==null?error.toString():error.getMessage())); }
        }
        staged.put(id,new Staged(parent,values,file.toString())); pending.remove(id);
    }
    private Object read(ThemeKey<?> key,JsonElement value,Resources resources) throws IOException {
        return switch(key.kind()) {
            case STYLE -> readStyle(value.getAsJsonObject(),resources);
            case COLOR -> parseColor(string(value));
            case FONT -> ResourceLocation.parse(string(value));
            case BOOLEAN -> { if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Expected boolean"); yield value.getAsBoolean(); }
            case INTEGER -> integer(value);
            case FLOAT -> { if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected number"); float f=value.getAsFloat(); if(!Float.isFinite(f)) throw new IllegalArgumentException("Non-finite number"); yield f; }
            case TEXTURE -> readImageItem(value.getAsJsonObject(),resources);
        };
    }
    private static String string(JsonElement e) { if(!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string"); return e.getAsString(); }
    public static int parseColor(String value) {
        if(!value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new IllegalArgumentException("Expected #RRGGBB or #AARRGGBB");
        long v=Long.parseLong(value.substring(1),16); return (int)(value.length()==7?v|0xFF000000L:v);
    }
    private static int integer(JsonElement value) {
        if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer");
        try { return value.getAsBigDecimal().intValueExact(); } catch(ArithmeticException ex) { throw new IllegalArgumentException("Expected finite 32-bit integer"); }
    }
    private static int number(JsonObject o,String key,int fallback) { return o.has(key)?integer(o.get(key)):fallback; }
    private static ThemeInsets insets(JsonObject o,String name,boolean borders) {
        if(!o.has(name)) return ThemeInsets.ZERO;
        JsonObject v=o.getAsJsonObject(name); String[] names=borders?new String[]{"borderLeft","borderTop","borderRight","borderBottom"}:new String[]{"left","top","right","bottom"};
        for(String k:v.keySet()) if(!Arrays.asList(names).contains(k)) throw new IllegalArgumentException(name+"."+k+": unknown property");
        return new ThemeInsets(number(v,names[0],0),number(v,names[1],0),number(v,names[2],0),number(v,names[3],0));
    }
    private static io.github.luyingyilong.ore.layout.Offset contentOffset(JsonObject o) {
        if(!o.has("contentOffset")) return io.github.luyingyilong.ore.layout.Offset.ZERO;
        JsonObject value=o.getAsJsonObject("contentOffset");
        for(String key:value.keySet()) if(!key.equals("x") && !key.equals("y"))
            throw new IllegalArgumentException("contentOffset."+key+": unknown property");
        return new io.github.luyingyilong.ore.layout.Offset(number(value,"x",0),number(value,"y",0));
    }
    private StyleBox readStyle(JsonObject o,Resources resources) throws IOException {
        if(!o.has("style")) throw new IllegalArgumentException("style: missing style kind");
        String style=string(o.get("style"));
        ThemeInsets content=insets(o,"contentMargin",false), expand=insets(o,"expandMargin",false);
        return switch(style) {
            case "empty" -> new StyleBoxEmpty(content,expand,insets(o,"drawInset",false),contentOffset(o));
            case "flat" -> new StyleBoxFlat(o.has("color")?parseColor(string(o.get("color"))):0,insets(o,"borders",true),o.has("borderColor")?parseColor(string(o.get("borderColor"))):0,content,expand,insets(o,"drawInset",false),contentOffset(o));
            case "texture" -> readTexture(o,resources);
            default -> throw new IllegalArgumentException("Unknown style: "+style);
        };
    }
    private StyleBoxTexture readTexture(JsonObject o,Resources resources) throws IOException {
        // Old MUI aliases are intentionally not parsed.
        if(o.has("borderX") || o.has("borderY") || o.has("textureMargin") || o.has("type")) throw new IllegalArgumentException("Unsupported legacy texture property");
        if(o.has("drawCenter") && (!o.get("drawCenter").isJsonPrimitive() || !o.getAsJsonPrimitive("drawCenter").isBoolean()))
            throw new IllegalArgumentException("drawCenter: expected boolean");
        TextureRegion region=readImage(o,resources);
        float alpha=o.has("opacity")?o.get("opacity").getAsFloat():1;
        return new StyleBoxTexture(region, insets(o,"textureBorders",true), insets(o,"contentMargin",false), insets(o,"expandMargin",false), o.has("horizontalMode")?StyleBoxTexture.AxisMode.valueOf(string(o.get("horizontalMode")).toUpperCase(Locale.ROOT)):StyleBoxTexture.AxisMode.STRETCH, o.has("verticalMode")?StyleBoxTexture.AxisMode.valueOf(string(o.get("verticalMode")).toUpperCase(Locale.ROOT)):StyleBoxTexture.AxisMode.STRETCH, !o.has("drawCenter") || o.get("drawCenter").getAsBoolean(), alpha, insets(o,"drawInset",false), contentOffset(o));
    }
    private TextureRegion readImage(JsonObject o,Resources resources) throws IOException {
        ResourceLocation location=ResourceLocation.parse(string(o.get("location")));
        if(!location.getPath().startsWith("textures/")) location=ResourceLocation.fromNamespaceAndPath(location.getNamespace(),"textures/"+location.getPath()+(location.getPath().endsWith(".png")?"":".png"));
        int[] actual=imageSizes.get(location);
        if(actual==null) { actual=resources.imageSize(location); imageSizes.put(location,actual); }
        int iw=number(o,"imageWidth",actual[0]), ih=number(o,"imageHeight",actual[1]);
        if(iw!=actual[0] || ih!=actual[1]) throw new IllegalArgumentException("imageWidth/imageHeight differ from PNG dimensions");
        return new TextureRegion(location,iw,ih,number(o,"x",0),number(o,"y",0),number(o,"width",iw),number(o,"height",ih));
    }
    private TextureRegion readImageItem(JsonObject o,Resources resources) throws IOException {
        Set<String> fields=Set.of("location","imageWidth","imageHeight","x","y","width","height");
        for(String field:o.keySet()) if(!fields.contains(field))
            throw new IllegalArgumentException("Unknown image-region property: "+field);
        return readImage(o,resources);
    }

}
