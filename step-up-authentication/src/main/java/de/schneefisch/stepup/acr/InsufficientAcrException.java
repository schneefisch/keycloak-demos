package de.schneefisch.stepup.acr;

public class InsufficientAcrException extends RuntimeException {

    private final int requiredLevel;

    public InsufficientAcrException(int requiredLevel) {
        super("Required ACR level " + requiredLevel + " not met");
        this.requiredLevel = requiredLevel;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }
}
