package io.github.marcussi02.notification.provider;

public final class ProviderResult {
    private final boolean success;
    private final String errorMessage;

    public ProviderResult(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }

    public static ProviderResult ok() {
        return new ProviderResult(true, null);
    }

    public static ProviderResult fail(String reason) {
        return new ProviderResult(false, reason);
    }
}
