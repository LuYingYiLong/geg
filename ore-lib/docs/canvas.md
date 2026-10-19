# Canvas and external Screens

`Canvas` embeds one ordinary Ore control tree in a Screen that Ore does not own. It is
not a Control superclass, a second event dispatcher, or an operating-system window.
Call `OreClient.initialize` as usual before using the host adapters.

## An ordinary Screen

Create a Canvas once per UI instance, attach it to the current Screen, and render it
from the Screen's normal rendering callback. The registered Forge bridge renders
Overlay content once after that Screen and supplies inputs through its coordinating
Window. Do not forward keyboard, character, click, or scroll callbacks yourself.

```java
private final Canvas canvas = new Canvas(createContent());

@Override
public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
    super.render(graphics, mouseX, mouseY, partial);
    canvas.attach(ScreenWindowHost.getOrCreate(this));
    canvas.setViewport(new Rect(0, 0, 180, 120));
    canvas.setTransform(new CanvasTransform(20, 30, 1));
    canvas.render(graphics, mouseX, mouseY, partial);
}
```

The viewport is in the tree's local GUI units. The transform maps those units into
the Screen's GUI pixels. Translation and positive uniform scaling are supported;
rotation, shear and nonuniform scaling are rejected. Several canvases on the same
Screen share one WindowContext, focus manager, IME session and Overlay. Content may
not be shared between canvases or moved to another host.

Call `canvas.close()` when its contents are permanently abandoned. Closing is
idempotent and queues the entire owned subtree for safe cleanup. Temporarily
covering a retained Screen suspends its controls instead. If the Screen overrides
`removed()` and can later be restored, close from its actual final disposal path,
not from temporary navigation. Call `setContent` only with an unattached node;
replacing content detaches the previous tree without freeing it automatically.

For a Screen-wide modal, add it to
`canvas.getHost().getWindow().getPopupOverlay()`. Dropdown does this automatically.
An external host never opens or closes the original Screen. Esc first handles the
input/IME or topmost Overlay; otherwise it returns to the original Screen.

## Recipe viewers

Use each viewer's native recipe extension point and a factory producing fresh
controls for that particular layout:

Pass ordinary controls or a composed content root. Do not pass an existing Window;
use its content factory so the external Screen keeps a single host and focus scope.

```java
// JEI: IRecipeCategory.createRecipeExtras
OreJeiCanvasWidget.add(builder, new Rect(0, 0, 170, 156), this::createContent);

// EMI: EmiRecipe.addWidgets
OreEmiCanvasWidget.add(holder, new Bounds(0, 0, 170, 156), this::createContent);

// REI: DisplayCategory.setupDisplay's returned widget list
new OreReiCanvasWidget(new Rectangle(bounds.x + 4, bounds.y + 4, 170, 156),
        this::createContent);
```

The adapters capture the recipe's current pose and inherited clip. They reuse the
same controls on redraw and retire replaced layouts after a completed frame.
Minimization, skipped frames and temporary Screen navigation do not retire layouts.
All recipe inputs go through the Screen host; native Widget input callbacks must
not deliver them again. Closing a retired layout cancels captured gestures, IME and
subscriptions before its deferred cleanup.

Keep ingredients in the viewer's native slots. An Ore Slot displays an item but does
not implement recipe lookup, uses, recipe transfer or bookmarks. Ore does not
automatically display GEG's market/organization/mail screens inside recipe viewers.

Viewer APIs remain optional compile-time dependencies. The default adapter priority
is EMI, then REI, then JEI. JEI's Ore widget is inactive when EMI is present, preventing
JEMI from rendering the same interactive content twice. Include native adapter
registration for each viewer you want to support.

## Custom drawing

Override `render(DrawContext context, int screenX, int screenY)` in custom controls.
Use `getScreenBounds` for the control's coordinates and `context.enableScissor` /
`disableScissor` for a balanced transformed clip. The context exposes graphics, font,
viewport, host context, mouse and frame time. Never store a DrawContext beyond its
callback; access after it closes is rejected. MUI remains an internal editing/theme
backend and is not a second Control rendering API.

## Development verification

`CanvasGeometryTest` covers scaled nested scrolling, capture/release, inherited
clipping, popup escape, ownership and layout retirement. `CanvasClientTest` and the
three `*CanvasDemoPlugin` fixtures live only in `clientIntegrationTest`. Mount that
source set in an isolated client and set `ore.canvas.demo=true` plus
`ore.canvas.smoke=none|jei|emi|rei|jei+emi` to exercise actual GLFW input and recipe
layouts. No demo recipes, Inspector classes or viewer APIs are included in the
published GEG JAR.
