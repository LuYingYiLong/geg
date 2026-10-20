package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.signal.*;
import java.util.*;

/** Typed exclusive selection. Does not own, reparent or destroy its member controls. */
public final class RadioGroup<T> implements SignalOwner,AutoCloseable {
    private final SignalScope scope=new SignalScope();
    private final Class<T> type;
    private final Map<T,Radio> radios=new LinkedHashMap<>();
    private final SignalSource<T> selectionChanged=new SignalSource<>(this,"selectionChanged","T");
    private T selected;
    private boolean closed;
    private long generation;
    public RadioGroup(SignalOwner owner,Class<T> type){
        this.type=Objects.requireNonNull(type);Objects.requireNonNull(owner).getSignalScope().own(this);
    }
    @Override public SignalScope getSignalScope(){return scope;}
    public Class<T> getValueType(){return type;}
    public Signal<T> selectionChanged(){return selectionChanged.signal();}
    public T getSelected(){scope.checkThread();return selected;}
    public Map<T,Radio> getRadios(){scope.checkThread();return Collections.unmodifiableMap(new LinkedHashMap<>(radios));}
    private void requireOpen(){scope.checkThread();if(closed)throw new IllegalStateException("RadioGroup is closed");}
    public RadioGroup<T> addRadio(T value,Radio radio){
        requireOpen();type.cast(Objects.requireNonNull(value));Objects.requireNonNull(radio).getSignalScope().checkThread();
        if(!radio.isAlive()||radio.group!=null||radios.containsKey(value))throw new IllegalArgumentException("Radio/value already registered or unavailable");
        radios.put(value,radio);radio.group=this;
        if(radio.getChecked())change(value,true);
        return this;
    }
    public boolean removeRadio(Radio radio){
        scope.checkThread();
        T key=null;
        for(var entry:radios.entrySet())if(entry.getValue()==radio){key=entry.getKey();break;}
        if(key==null)return false;
        radios.remove(key,radio);if(radio.group==this)radio.group=null;
        if(Objects.equals(selected,key)) {
            radio.setGroupChecked(false);selected=null;long epoch=++generation;
            if(!closed){radio.emitToggled(false);if(epoch==generation&&!closed)selectionChanged.emit(null);}
        }
        return true;
    }
    public RadioGroup<T> setSelected(T value){requireOpen();change(value,true);return this;}
    public RadioGroup<T> setSelectedNoSignal(T value){requireOpen();change(value,false);return this;}
    public void clearSelection(){setSelected(null);}
    public void clearSelectionNoSignal(){setSelectedNoSignal(null);}
    void setRadioChecked(Radio radio,boolean checked,boolean notify){
        requireOpen();
        for(var entry:radios.entrySet())if(entry.getValue()==radio){
            if(checked)change(entry.getKey(),notify);
            else if(Objects.equals(selected,entry.getKey()))change(null,notify);
            return;
        }
        throw new IllegalStateException("Radio is not a member");
    }
    private void change(T value,boolean notify){
        if(value!=null){type.cast(value);if(!radios.containsKey(value)||!radios.get(value).isAlive())throw new IllegalArgumentException("Unknown or unavailable Radio value");}
        if(Objects.equals(selected,value))return;
        Radio old=radios.get(selected),next=radios.get(value);
        boolean oldChanged=old!=null&&old.getChecked(),nextChanged=next!=null&&!next.getChecked();
        selected=value;long epoch=++generation;
        if(old!=null)old.setGroupChecked(false);if(next!=null)next.setGroupChecked(true);
        if(!notify)return;
        if(oldChanged)old.emitToggled(false);
        if(epoch!=generation||closed)return;
        if(nextChanged)next.emitToggled(true);
        if(epoch==generation&&!closed)selectionChanged.emit(value);
    }
    @Override public void close(){
        scope.checkThread();if(closed)return;closed=true;generation++;
        for(Radio radio:radios.values())if(radio.group==this)radio.group=null;
        radios.clear();selected=null;scope.close();
    }
}
