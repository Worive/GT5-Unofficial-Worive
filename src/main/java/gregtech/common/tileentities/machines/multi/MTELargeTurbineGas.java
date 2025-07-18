package gregtech.common.tileentities.machines.multi;

import static gregtech.api.enums.Textures.BlockIcons.LARGETURBINE_NEW5;
import static gregtech.api.enums.Textures.BlockIcons.LARGETURBINE_NEW_ACTIVE5;
import static gregtech.api.enums.Textures.BlockIcons.LARGETURBINE_NEW_EMPTY5;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_CASINGS;
import static gregtech.api.enums.Textures.BlockIcons.casingTexturePages;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.TurbineStatCalculator;

public class MTELargeTurbineGas extends MTELargeTurbine {

    public MTELargeTurbineGas(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTELargeTurbineGas(String aName) {
        super(aName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        return new ITexture[] { MACHINE_CASINGS[1][colorIndex + 1],
            aFacing == side ? (aActive ? TextureFactory.builder()
                .addIcon(LARGETURBINE_NEW_ACTIVE5)
                .build()
                : hasTurbine() ? TextureFactory.builder()
                    .addIcon(LARGETURBINE_NEW5)
                    .build()
                    : TextureFactory.builder()
                        .addIcon(LARGETURBINE_NEW_EMPTY5)
                        .build())
                : casingTexturePages[0][58] };
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Gas Turbine, LGT")
            .addInfo("Needs a Turbine, place inside controller")
            // .addInfo("The excess fuel that gets consumed will be voided!")
            .addPollutionAmount(getPollutionPerSecond(null))
            .beginStructureBlock(3, 3, 4, true)
            .addController("Front center")
            .addCasingInfoRange("Stainless Steel Turbine Casing", 8, 30, false)
            .addDynamoHatch("Back center", 1)
            .addMaintenanceHatch("Side centered", 2)
            .addMufflerHatch("Side centered", 2)
            .addInputHatch("Gas Fuel, Side centered", 2)
            .addOtherStructurePart("Air", "3x3 area in front of controller")
            .toolTipFinisher();
        return tt;
    }

    public int getFuelValue(FluidStack aLiquid) {
        if (aLiquid == null) return 0;
        GTRecipe tFuel = RecipeMaps.gasTurbineFuels.getBackend()
            .findFuel(aLiquid);
        if (tFuel != null) return tFuel.mSpecialValue;
        return 0;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTELargeTurbineGas(mName);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.gasTurbineFuels;
    }

    @Override
    public int getRecipeCatalystPriority() {
        return -1;
    }

    @Override
    protected boolean filtersFluid() {
        return false;
    }

    @Override
    public Block getCasingBlock() {
        return GregTechAPI.sBlockCasings4;
    }

    @Override
    public byte getCasingMeta() {
        return 10;
    }

    @Override
    public int getCasingTextureIndex() {
        return 58;
    }

    @Override
    public boolean isNewStyleRendering() {
        return true;
    }

    @Override
    public int getPollutionPerSecond(ItemStack aStack) {
        return GTMod.proxy.mPollutionLargeGasTurbinePerSecond;
    }

    @Override
    int fluidIntoPower(ArrayList<FluidStack> aFluids, TurbineStatCalculator turbine) {
        if (!aFluids.isEmpty()) {
            int tEU = 0;
            int actualOptimalFlow = 0;

            FluidStack firstFuelType = new FluidStack(aFluids.get(0), 0); // Identify a SINGLE type of fluid to process.
                                                                          // Doesn't matter which one. Ignore the rest!
            int fuelValue = getFuelValue(firstFuelType);
            if (fuelValue <= 0) {
                return 0;
            }

            if (turbine.getOptimalGasEUt() < fuelValue) {
                // turbine too weak and/or fuel too powerful
                // at least consume 1L
                this.realOptFlow = 1;
                // wastes the extra fuel and generate aOptFlow directly
                depleteInput(new FluidStack(firstFuelType, 1));
                this.storedFluid += 1;
                return GTUtility.safeInt((long) turbine.getOptimalGasEUt());
            }

            actualOptimalFlow = GTUtility.safeInt(
                (long) ((looseFit ? turbine.getOptimalLooseGasFlow() : turbine.getOptimalGasFlow()) / fuelValue));
            this.realOptFlow = actualOptimalFlow;

            // Allowed to use up to 450% optimal flow rate, depending on the value of overflowMultiplier.
            // This value is chosen because the highest EU/t possible depends on the overflowMultiplier, and the formula
            // used
            // makes it so the flow rate for that max, per value of overflowMultiplier, is (percentage of optimal flow
            // rate):
            // - 150% if it is 1
            // - 300% if it is 2
            // - 450% if it is 3
            // Variable required outside of loop for multi-hatch scenarios.
            int remainingFlow = GTUtility
                .safeInt((long) (actualOptimalFlow * (1.5f * turbine.getOverflowEfficiency())));
            int flow = 0;
            int totalFlow = 0;

            storedFluid = 0;
            for (FluidStack aFluid : aFluids) {
                if (aFluid.isFluidEqual(firstFuelType)) {
                    flow = Math.min(aFluid.amount, remainingFlow); // try to use up to the max flow defined just above
                    depleteInput(new FluidStack(aFluid, flow)); // deplete that amount
                    this.storedFluid += aFluid.amount;
                    remainingFlow -= flow; // track amount we're allowed to continue depleting from hatches
                    totalFlow += flow; // track total input used
                }
            }
            if (totalFlow <= 0) return 0;
            tEU = GTUtility.safeInt((long) totalFlow * fuelValue);

            if (totalFlow != actualOptimalFlow) {
                float efficiency = getOverflowEfficiency(totalFlow, actualOptimalFlow, turbine.getOverflowEfficiency());
                tEU *= efficiency;
            }
            tEU = GTUtility
                .safeInt((long) (tEU * (looseFit ? turbine.getLooseGasEfficiency() : turbine.getGasEfficiency())));

            // EU/t output cap to properly tier the LGT against the Advanced LGT, will be implemented in a future dev
            // update
            /*
             * if (tEU > 8192) { tEU = 8192; }
             */

            // If next output is above the maximum the dynamo can handle, set it to the maximum instead of exploding the
            // turbine
            // Raising the maximum allowed flow rate to account for the efficiency changes beyond the optimal flow
            // When the max fuel consumption rate was increased, turbines could explode on world load
            if (tEU > getMaximumOutput()) {
                tEU = GTUtility.safeInt(getMaximumOutput());
            }
            return tEU;
        }
        return 0;
    }

    @Override
    float getOverflowEfficiency(int totalFlow, int actualOptimalFlow, int overflowMultiplier) {
        // overflowMultiplier changes how quickly the turbine loses efficiency after flow goes beyond the optimal value
        // At the default value of 1, any flow will generate less EU/t than optimal flow, regardless of the amount of
        // fuel used
        // The bigger this number is, the slower efficiency loss happens as flow moves beyond the optimal value
        // Gases are the second most efficient in this regard, with plasma being the most efficient
        float efficiency = 0;

        if (totalFlow > actualOptimalFlow) {
            efficiency = 1.0f - Math.abs((totalFlow - actualOptimalFlow))
                / ((float) actualOptimalFlow * ((overflowMultiplier * 3) - 1));
        } else {
            efficiency = 1.0f - Math.abs((totalFlow - actualOptimalFlow) / (float) actualOptimalFlow);
        }

        return efficiency;
    }
}
