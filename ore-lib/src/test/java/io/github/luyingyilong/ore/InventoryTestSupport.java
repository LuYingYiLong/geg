package io.github.luyingyilong.ore;

/** Bare JavaExec does not run Forge's event-class transformer. Only initialize vanilla registry data here;
 * the complete Forge bootstrap and network stack are exercised by the isolated client/server fixtures. */
public final class InventoryTestSupport {
    private static boolean initialized;
    public static void initializeRegistries(){
        if(initialized)return;
        net.minecraft.SharedConstants.tryDetectVersion();
        try{
            var field=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
            field.setAccessible(true);field.setBoolean(null,true);
        }catch(ReflectiveOperationException error){throw new AssertionError("Test registry bootstrap changed",error);}
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        initialized=true;
    }
}
