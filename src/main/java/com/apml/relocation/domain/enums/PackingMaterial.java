package com.apml.relocation.domain.enums;

public enum PackingMaterial {
    CARTON_BOX(false), STRETCH_FILM(false), FOAM(false), CORRUGATED_ROLL(false), AIR_BUBBLE(false),
    HYGIENE_PAD(true), LED_BOX(true), SOLID_BOX(true);

    private final boolean returnable;

    PackingMaterial(boolean returnable) { this.returnable = returnable; }

    /** Returnable APML material must be handed back on the same day as unloading. */
    public boolean isReturnable() { return returnable; }
}
