package com.simpleautofarm.compat;

import com.simpleautofarm.SimpleAutoFarm;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Optional Mekanism integration: exposes the Void Singularity as a chemical handler that accepts
 * (and instantly destroys) any gas/slurry/pigment/infuse type. All Mekanism references are confined
 * to this class and only touched when Mekanism is actually loaded, so the mod still runs without it.
 */
public final class MekanismCompat {

    private MekanismCompat() {
    }

    private static final IChemicalHandler VOID_CHEMICAL = new IChemicalHandler() {
        @Override
        public int getChemicalTanks() {
            return 1;
        }

        @Override
        public ChemicalStack getChemicalInTank(int tank) {
            return ChemicalStack.EMPTY;
        }

        @Override
        public void setChemicalInTank(int tank, ChemicalStack stack) {
        }

        @Override
        public long getChemicalTankCapacity(int tank) {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean isValid(int tank, ChemicalStack stack) {
            return true;
        }

        @Override
        public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
            // Accept everything (destroyed immediately).
            return ChemicalStack.EMPTY;
        }

        @Override
        public ChemicalStack extractChemical(int tank, long amount, Action action) {
            return ChemicalStack.EMPTY;
        }
    };

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.CHEMICAL.block(), SimpleAutoFarm.VOID_SINGULARITY_BLOCK_ENTITY.get(),
                (be, side) -> VOID_CHEMICAL);
    }
}
