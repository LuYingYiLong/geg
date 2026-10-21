package io.github.luyingyilong.ore.gallery;

import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.Navbar;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** The developer UI is a normal retained Ore tree, not a screenshot-only catalogue. */
public final class GalleryLayoutTest {
    public static void main(String[] args) throws Exception {
        Set<String> covered=new HashSet<>();
        GalleryCatalog.getEntries().forEach(entry->entry.types().forEach(type->covered.add(type.getSimpleName())));
        Path sources=Path.of(System.getProperty("ore.resourceRoot")).getParent().resolve("java/io/github/luyingyilong/ore");
        Pattern controls=Pattern.compile("public (?:final )?class (\\w+)(?:<[^\\n]*?>)? extends (?:Control|Container|Input)\\b");
        Map<String,String> exclusions=Map.of("CanvasRegion","External Canvas geometry bridge; covered by CanvasClientTest");
        try(var files=Files.walk(sources)){
            for(Path path:files.filter(p->p.toString().endsWith(".java")).toList()){
                var match=controls.matcher(Files.readString(path));
                if(match.find() && path.getFileName().toString().equals(match.group(1)+".java"))
                    check(covered.contains(match.group(1))||exclusions.containsKey(match.group(1)),"Missing gallery example: "+match.group(1));
            }
        }
        Font font=new Font(id->null,false){
            @Override public java.util.List<FormattedCharSequence> split(FormattedText text,int width){
                java.util.List<FormattedCharSequence> lines=new java.util.ArrayList<>();
                int[] points=text.getString().codePoints().toArray();int count=Math.max(1,width/6);
                for(int from=0;from<points.length;from+=count)
                    lines.add(Component.literal(new String(points,from,Math.min(count,points.length-from))).getVisualOrderText());
                return lines.isEmpty()?java.util.List.of(FormattedCharSequence.EMPTY):lines;
            }
            @Override public int width(String text){return text.codePointCount(0,text.length())*6;}
            @Override public int width(FormattedText text){return width(text.getString());}
            @Override public int width(FormattedCharSequence text){int[] n={0};text.accept((i,s,c)->{n[0]++;return true;});return n[0]*6;}
        };
        GalleryView view=new GalleryView();
        for(int width:new int[]{127,128,129,480}){
            view.getWindow().layoutViewport(width,240,font);
            Control background=view.getWindow().getChild(0);
            check(background instanceof io.github.luyingyilong.ore.layout.Panel,"One overall background panel");
            Control root=background.getChild(0);
            check(root.getChild(0) instanceof Navbar && root.getChild(0).getAbsoluteBounds().y()==0,"Navbar remains at top");
            check(root.getChild(1).getBounds().height()>100,"Body consumes remaining height");
            for(int preview:new int[]{0,127,128,129}){
                view.setPreviewWidth(preview);
                view.getWindow().layoutViewport(width,240,font);
                check(view.getSample().isAlive(),"Preview width does not replace content");
            }
        }
        view.setPreviewWidth(127);
        view.getWindow().layoutViewport(480,120,font);
        check(view.getContentScroll().getMaxScrollY()>0 && view.getContentScroll().getViewport().height()<120,
                "Long sample scrolls in the right viewport instead of inflating the outer horizontal viewport");
        Control previous=view.getSample();
        view.select("button");
        check(previous.isQueuedForDeletion() && !view.getSample().isQueuedForDeletion(),"Only replaced sample is queued");
        for(int i=0;i<5;i++){view.select("label");view.select("box");}
        view.select("slider");
        for (int width : new int[]{127, 128, 129, 480}) {
            view.setPreviewWidth(width == 480 ? 0 : width);
            view.getWindow().layoutViewport(width, 240, font);
            check(countSliders(view.getSample()) == 7, "Slider gallery includes every mode and typed form fields");
        }
        view.getWindow().queueFree();LifecycleScheduler.flush();
        check(view.getWindow().isDisposed(),"Gallery lifetime ends cleanly");
        System.out.println("ORE_GALLERY_LAYOUT_PASS");
    }
    private static int countSliders(Control node) {
        return (node instanceof io.github.luyingyilong.ore.Slider ? 1 : 0)
                + node.getChildren().stream().mapToInt(GalleryLayoutTest::countSliders).sum();
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

