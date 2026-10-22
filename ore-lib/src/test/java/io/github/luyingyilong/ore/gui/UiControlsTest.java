package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.base.*;
import io.github.luyingyilong.ore.control.data.*;
import io.github.luyingyilong.ore.control.display.avatar.Avatar;
import io.github.luyingyilong.ore.control.form.*;
import io.github.luyingyilong.ore.control.input.*;
import io.github.luyingyilong.ore.control.inventory.*;
import io.github.luyingyilong.ore.control.inventory.source.*;
import io.github.luyingyilong.ore.control.layout.*;
import io.github.luyingyilong.ore.control.overlay.*;
import io.github.luyingyilong.ore.signal.SignalScope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class UiControlsTest {
    private static final ResourceLocation RESOURCE=ResourceLocation.parse("test:gui/controls.xml");
    private static final String XML="""
      <Box id="root" gap="4">
        <Collapse id="collapse" text="Details" secondaryText="Hint" expanded="true" animationDuration="0">
          <slot name="content"><Input id="collapsedInput" text="draft"/></slot>
          <connect signal="expandedChanged" handler="bool"/>
        </Collapse>
        <Splitter id="split" ratio="0.4" firstMinimum="2" firstMaximum="200">
          <slot name="first"><Input id="splitInput" text="left"/></slot>
          <slot name="second"><Label id="right" text="right"/></slot>
        </Splitter>
        <Form id="form">
          <FormItem id="nameField" name="name" label="Name">
            <Input id="nameInput" text="initial" placeholder="Hint"/>
            <validate rule="required"/><validate rule="length" min="2" max="32"/>
          </FormItem>
          <FormItem id="agreeField" name="agree" label="Agree"><Switch id="agree" checked="true"/></FormItem>
          <FormItem id="choiceField" name="choice" label="Choice">
            <RadioGroup id="radio" orientation="HORIZONTAL" selected="yes">
              <item value="yes"><Radio id="yes" text="Yes"/></item>
              <item value="no"><Radio id="no" text="No"/></item>
            </RadioGroup><validate rule="required"/>
          </FormItem>
        </Form>
        <Inventory id="inventory" slotCount="9" columns="3" mode="SELECT" selected="2" showTitle="false">
          <item index="2" item="minecraft:stone" count="12"/>
          <connect signal="selectionChanged" handler="integer"/>
        </Inventory>
        <Inventory id="borrowed" source="ref:inventory" slotIndices="1,0" showTitle="false"/>
        <Slot id="preview" item="minecraft:diamond" count="4" backgroundVisible="false"/>
        <Avatar id="avatar" initials="Player" size="16" hatLayer="false"/>
        <DescriptionItem id="description" key="info" label="Information" content="Content"/>
        <Tree id="tree" defaultExpandAll="true" selected="child" rowHeight="20" showSwitcher="true">
          <item key="parent" text="Parent"><item key="child" text="Child" value="value"/></item>
          <connect signal="selectionChanged" handler="string"/>
          <connect signal="expansionChanged" handler="expansion"/>
        </Tree>
        <Table id="table" headerVisible="true" empty="Empty">
          <column key="name" text="Name" minWidth="24" width="80"/>
          <row key="one"><cell column="name" text="First"/></row>
          <connect signal="rowActivated" handler="row"/>
        </Table>
        <Overlay id="layers">
          <Modal id="modal" title="Modal" width="160" height="120" visible="false">
            <slot name="content"><Input id="modalInput" text="modal draft"/></slot>
            <slot name="actions"><Button id="confirm" text="Confirm"><connect signal="pressed" handler="action"/></Button></slot>
          </Modal>
          <Dialog id="dialog" title="Dialog" width="160" visible="false">
            <slot name="content"><Input id="dialogInput" text="dialog draft"/></slot>
            <slot name="actions"><Button id="cancel" text="Cancel"/></slot>
          </Dialog>
          <Drawer id="drawer" title="Drawer" placement="LEFT" width="180" open="false">
            <slot name="content"><TextArea id="drawerInput" rows="2" text="drawer draft"/></slot>
            <slot name="actions"><Button id="drawerAction" text="Done"/></slot>
          </Drawer>
        </Overlay>
        <Toast id="toast" text="Message" duration="0" type="PRIMARY" open="false"/>
      </Box>
      """;
    public static void main(String[] args) throws Exception {
        var dist=net.minecraftforge.fml.loading.FMLLoader.class.getDeclaredField("dist");dist.setAccessible(true);dist.set(null,net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER);
        var boot=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");boot.setAccessible(true);boot.setBoolean(null,true);
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        coverage(); allControls(); treeReload(); popupInput(); rejection();
        System.out.println("ORE_XML_CONTROLS_PASS");
    }
    private static void coverage() {
        Set<String> types=UiTypeRegistry.createDefault().getRegisteredTypes();
        for(String type:List.of("Collapse","Splitter","Avatar","Slot","Inventory","Tree","Table","Form","FormItem","RadioGroup","Dialog","Modal","Drawer","Overlay","Toast","DescriptionItem"))
            check(types.contains(type),"Missing XML control " + type);
        try(var files=java.nio.file.Files.walk(java.nio.file.Path.of(System.getProperty("ore.resourceRoot")).getParent().resolve("java/io/github/luyingyilong/ore/control"))) {
            var pattern=java.util.regex.Pattern.compile("public (?:final )?class (\\w+)(?:<[^\\n]*?>)? extends (?:Control|Container|Input)\\b");
            for(var file:files.filter(f -> f.toString().endsWith(".java")).toList()){
                var match=pattern.matcher(java.nio.file.Files.readString(file));
                if(match.find()&&!match.group(1).equals("InputControl"))check(types.contains(match.group(1)),"Control lacks built-in XML registration: " + match.group(1));
            }
        } catch(java.io.IOException failure){throw new AssertionError(failure);}
    }
    private static void allControls() {
        AtomicReference<String> xml=new AtomicReference<>(XML);
        MemoryInventorySource source=new MemoryInventorySource(3);
        UiBindings bindings=new UiBindings().value("inventory",InventorySource.class,source)
                .handler("bool",Boolean.class,v -> {}).handler("integer",Integer.class,v -> {}).handler("string",String.class,v -> {})
                .handler("expansion",Tree.Expansion.class,v -> {}).handler("row",UiTableRow.class,v -> {}).action("action",() -> {});
        UiLoader loader=new UiLoader(UiTypeRegistry.createDefault(),id -> new StringReader(xml.get()),Runnable::run);
        try(SignalScope owner=new SignalScope();UiDocument document=loader.open(RESOURCE,bindings,owner)) {
            Control root=document.getRoot();
            Form form=root.findControlFromId("form",Form.class);
            var name=form.getItem(new FieldKey<>("name",String.class));
            check(name.getInputNode()==root.findControlFromId("nameInput"),"Form adapts existing InputControl without wrapping again");
            var result=form.validate();check(result.getValid(),"Declarative form validates");
            check(result.getValues().getValue(new FieldKey<>("name",String.class)).equals("initial"),"Typed form values");
            Input input=name.getInput();input.setTextNoSignal("retained draft");
            UiRadioGroup group=root.findControlFromId("radio",UiRadioGroup.class);group.setSelectedNoSignal("no");
            Inventory inventory=root.findControlFromId("inventory",Inventory.class);
            check(inventory.getSlotControl(2).getItem().getCount()==12 && inventory.getSelected()==2,"Declarative inventory items and selection");
            check(root.findControlFromId("borrowed",Inventory.class).getSource()==source,"Borrowed inventory reference is typed");
            check(root.findControlFromId("preview",Slot.class).getQuantity()==4 && root.findControlFromId("avatar",Avatar.class).getAvatarSize()==16,"Slot preview and Avatar size");
            check(root.findControlFromId("dialogInput")!=null && root.findControlFromId("modalInput")!=null && root.findControlFromId("drawerInput")!=null,"Named popup content participates in XML ID lookup");
            Splitter split=root.findControlFromId("split",Splitter.class);check(((Panel)split.getFirst()).getChild(0)==root.findControlFromId("splitInput"),"Splitter named panes");
            Tree<?> tree=root.findControlFromId("tree",Tree.class);check(tree.getItems().size()==2 && tree.getSelected().equals("child"),"Nested Tree items and selection");
            tree.setExpandedNoSignal("parent",false);
            Table<?> table=root.findControlFromId("table",Table.class);check(table.getRows().size()==1,"Declarative table rows");
            xml.set(XML.replace("placeholder=\"Hint\"","placeholder=\"Updated\"").replace("text=\"First\"","text=\"Updated row\"").replace("title=\"Modal\"","title=\"Changed modal\""));
            document.reload();UiRuntime.flush();check(document.getStatus()==UiDocument.Status.READY,document.getError());
            check(root.findControlFromId("nameField")==name && root.findControlFromId("nameInput")==name.getInputNode() && input.getText().equals("retained draft"),"Reload preserves FormItem, input and draft");
            check(group.getSelected().equals("no") && !tree.getExpandedKeys().contains("parent"),"Radio selection and Tree expansion survive reload");
            check(root.findControlFromId("inventory")==inventory && inventory.getSelected()==2,"Unchanged inventory source/slots are reused");
            check(root.findControlFromId("confirm")!=null && root.findControlFromId("dialogInput")!=null,"Popup actions and body survive rebind");
            check(!source.isClosed(),"Reload never closes borrowed inventory");
        } finally {UiRuntime.flush();LifecycleScheduler.flush();check(!source.isClosed(),"Document close does not close shared source");source.close();}
    }
    private static void treeReload() {
        AtomicReference<String> xml = new AtomicReference<>("<Tree id='tree' selected='oldChild' defaultExpandAll='true'><item key='keep'><item key='oldChild'/></item><item key='removed'><item key='removedChild'/></item></Tree>");
        UiLoader loader = new UiLoader(UiTypeRegistry.createDefault(), id -> new StringReader(xml.get()), Runnable::run);
        try (SignalScope owner = new SignalScope(); UiDocument document = loader.open(RESOURCE, new UiBindings(), owner)) {
            Tree<?> tree = document.getRoot().findControlFromId("tree", Tree.class);
            check(tree.getExpandedKeys().containsAll(Set.of("keep", "removed")), "Initial branches expanded");
            xml.set("<Tree id='tree'><item key='keep'><item key='newChild'/></item></Tree>");
            document.reload(); UiRuntime.flush();
            check(document.getStatus() == UiDocument.Status.READY, document.getError());
            check(document.getRoot().findControlFromId("tree") == tree, "Tree data reload reuses the control");
            check(tree.getSelected().isEmpty(), "Removed Tree selection is cleared");
            check(tree.getExpandedKeys().equals(Set.of("keep")), "Surviving expansion retained when another branch disappears");
        } finally { UiRuntime.flush(); LifecycleScheduler.flush(); }
    }
    private static void popupInput() {
        String xml="<Overlay><Button id='background' text='Background'><connect signal='pressed' handler='background'/></Button><Modal id='modal' title='Modal' width='150' height='100'><slot name='content'><Label text='Content'/></slot><slot name='actions'><Button id='action' text='Action'><connect signal='pressed' handler='action'/></Button></slot><connect signal='closeRequested' handler='close'/></Modal></Overlay>";
        int[] calls={0,0};var reference=new java.util.concurrent.atomic.AtomicReference<UiDocument>();
        UiBindings bindings=new UiBindings().action("background",()->calls[0]++).action("action",()->calls[1]++)
                .action("close",()->reference.get().getRoot().findControlFromId("modal").setVisible(false));
        UiLoader loader=new UiLoader(UiTypeRegistry.createDefault(),id -> new StringReader(xml),Runnable::run);
        try(SignalScope owner=new SignalScope();UiDocument document=loader.open(RESOURCE,bindings,owner)){
            reference.set(document);
            var window=io.github.luyingyilong.ore.window.Window.embedded().setContent(document.getRoot());
            try {
                Font font=new Font(id -> null,false){@Override public int width(FormattedText text){return text.getString().length()*6;}};
                window.layoutViewport(240,180,font);
                Control modal=document.getRoot().findControlFromId("modal"),action=document.getRoot().findControlFromId("action"),background=document.getRoot().findControlFromId("background");
                check(window.getFocusManager().getInputScope()==modal && !window.getFocusManager().allowsNativeInput(background),"XML popup establishes a real focus scope through its mount wrapper");
                Rect area=io.github.luyingyilong.ore.event.EventGeometry.displayed(action);
                io.github.luyingyilong.ore.event.WindowInputs.press(window,area.x()+3,area.y()+3,null);
                io.github.luyingyilong.ore.event.WindowInputs.release(window,area.x()+3,area.y()+3);
                check(calls[0]==0 && calls[1]==1,"Popup button dispatches once without reaching background");
                io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,null);
                io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,null);
                check(!modal.getVisible() && window.getFocusManager().getInputScope()==null,"XML closeRequested binding closes only the popup");
            } finally {window.queueFree();}
        } finally {UiRuntime.flush();LifecycleScheduler.flush();}
    }
    private static void rejection() {
        for(String xml:List.of(
            "<Splitter><slot name='bad'><Control/></slot></Splitter>",
            "<Modal><slot name='actions'><Label text='wrong'/></slot></Modal>",
            "<RadioGroup><item value='same'><Radio/></item><item value='same'><Radio/></item></RadioGroup>",
            "<Tree><item key='same'/><item key='same'/></Tree>",
            "<Form><FormItem name='x'><Input id='a'/></FormItem><FormItem name='x'><Input id='b'/></FormItem></Form>",
            "<FormItem name='x'><Checkbox/><validate rule='length' min='1' max='2'/></FormItem>",
            "<Inventory slotCount='1'><item index='2' item='minecraft:stone'/></Inventory>",
            "<Inventory source='ref:missing'/>",
            "<Slot item='minecraft:no_such_item'/>")) {
            UiLoader loader=new UiLoader(UiTypeRegistry.createDefault(),id -> new StringReader(xml),Runnable::run);
            try(SignalScope owner=new SignalScope()) {
                try(UiDocument ignored=loader.open(RESOURCE,new UiBindings(),owner)){throw new AssertionError("Accepted invalid XML: " + xml);}
                catch(UiException expected){check(expected.getMessage().contains("test:gui/controls.xml"),"Error keeps resource location");}
            } finally {LifecycleScheduler.flush();}
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
