package ai.pollinations.minecraft;

/** Outcome of an API call: a value, or a player-facing error message. */
record Result<T>(T value, String error) {

    static <T> Result<T> ok(T value) {
        return new Result<>(value, null);
    }

    static <T> Result<T> fail(String error) {
        return new Result<>(null, error);
    }

    boolean failed() {
        return error != null;
    }
}
