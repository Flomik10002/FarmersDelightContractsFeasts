package dev.flomik.farmerscontracts.client;

import dev.flomik.farmerscontracts.FarmersContractsMod;
import dev.flomik.farmerscontracts.contract.ContractDataComponents;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

// Must stay a separate class from FarmersContractsMod: a lambda implementing ItemPropertyFunction
// takes a ClientLevel parameter, and ClientLevel is @OnlyIn(Dist.CLIENT) - if that lambda were
// declared directly inside the common mod class, the synthetic method NeoForge generates for it
// would embed a client-only type in a class that's always loaded (including on dedicated
// servers), and the dist sideness check crashes server startup with "Attempted to load class
// ClientLevel for invalid dist DEDICATED_SERVER". Keeping it here means this class - and the
// lambda - is only ever loaded from FMLClientSetupEvent, which never fires on a server.
public final class ClientSetup {

    private ClientSetup() {
    }

    public static void registerItemProperties() {
        ItemProperties.register(
                FarmersContractsMod.CONTRACT_BOX_ITEM.get(),
                ResourceLocation.fromNamespaceAndPath(FarmersContractsMod.MODID, "sealed"),
                (stack, level, entity, seed) -> stack.has(ContractDataComponents.CONTRACT_DATA.get()) ? 1.0F : 0.0F);
    }
}
