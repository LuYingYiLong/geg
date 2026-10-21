package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.signal.SignalScope;
import io.github.luyingyilong.ore.theme.*;

public final class ProgressBarTest {
    public static void main(String[] args){
        ProgressBar bar=new ProgressBar();SignalScope receiver=new SignalScope();int[] events={0};
        bar.valueChanged().connect(receiver,value->events[0]++);
        check(bar.getMin()==0&&bar.getMax()==100&&bar.getValue()==0,"defaults");
        bar.setValue(25).setValue(25).setValueNoSignal(50);check(events[0]==1,"changed and silent paths");
        Rect inner=new Rect(10,20,100,80);
        for(var d:ProgressBar.Direction.values()){
            bar.setDirection(d);Rect r=bar.getFillBounds(inner);
            check(r.equals(switch(d){case LEFT_TO_RIGHT->new Rect(10,20,50,80);case RIGHT_TO_LEFT->new Rect(60,20,50,80);
                case TOP_TO_BOTTOM->new Rect(10,20,100,40);case BOTTOM_TO_TOP->new Rect(10,60,100,40);}),"four direction geometry");
        }
        bar.setValue(1000);check(bar.getValue()==100&&events[0]==2,"clamp once");
        bar.setLimits(0,20);check(bar.getValue()==20&&events[0]==3,"limits atomically clamp");
        for(double v:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            reject(()->bar.setValue(v));reject(()->bar.setLimits(v,100));
        }
        reject(()->bar.setLimits(10,10));reject(()->bar.setLimits(20,10));
        check(bar.getMin()==0&&bar.getMax()==20&&bar.getValue()==20,"invalid values preserve prior state");
        bar.setThemeOverride(ProgressBar.PADDING_LEFT,3);
        check(bar.getThemeItem(ProgressBar.PADDING_LEFT)==3,"typed theme padding");
        check(!bar.acceptsFocus(),"display-only progress cannot focus");
        bar.queueFree();LifecycleScheduler.flush();check(bar.isDisposed()&&bar.valueChanged().connectionCount()==0,"owned label and connections clean up");receiver.close();
        System.out.println("ORE_PROGRESS_BAR_PASS");
    }
    private static void reject(Runnable action){try{action.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException expected){}}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
