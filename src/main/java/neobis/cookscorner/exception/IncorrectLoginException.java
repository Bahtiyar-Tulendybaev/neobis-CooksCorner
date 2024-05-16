package neobis.cookscorner.exception;

public class IncorrectLoginException extends RuntimeException{
    public IncorrectLoginException(String message) {
        super(message);
    }
}
